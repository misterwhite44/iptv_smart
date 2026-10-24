package com.misterwhite.iptvsmart.data.account

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Le compte Zynora connecté (Firebase). */
data class AccountUser(val uid: String, val email: String?, val displayName: String?)

class AccountException(message: String) : Exception(message)

/**
 * Comptes Zynora : email + mot de passe ou Google, via Firebase Authentication.
 * Les mots de passe sont gérés par Firebase et ne passent jamais par nos données.
 * Les identifiants Xtream, eux, restent uniquement sur le téléphone.
 */
class AccountRepository(private val context: Context) {

    /** Faux si le fichier google-services.json manquait au build : les comptes sont alors désactivés. */
    val isConfigured: Boolean = FirebaseApp.getApps(context).isNotEmpty()

    private val auth: FirebaseAuth? = if (isConfigured) FirebaseAuth.getInstance() else null
    private val db: FirebaseFirestore? = if (isConfigured) FirebaseFirestore.getInstance() else null

    private val _user = MutableStateFlow(auth?.currentUser?.toAccountUser())
    val user: StateFlow<AccountUser?> = _user.asStateFlow()

    /** Vrai s'il faut un compte Zynora avant d'aller plus loin. */
    val needsSignIn: Boolean
        get() = auth != null && auth.currentUser == null

    init {
        auth?.addAuthStateListener { _user.value = it.currentUser?.toAccountUser() }
    }

    suspend fun signIn(email: String, password: String) = guarded {
        val result = requireAuth().signInWithEmailAndPassword(email.trim(), password).await()
        onSignedIn(result.user, provider = "password")
    }

    suspend fun signUp(email: String, password: String) = guarded {
        val result = requireAuth().createUserWithEmailAndPassword(email.trim(), password).await()
        result.user?.sendEmailVerification()
        onSignedIn(result.user, provider = "password")
    }

    suspend fun sendPasswordReset(email: String) = guarded {
        requireAuth().sendPasswordResetEmail(email.trim()).await()
    }

    /** « Continuer avec Google » : le compte Google du téléphone, en un tap. */
    suspend fun signInWithGoogle(activityContext: Context) = guarded {
        val clientId = webClientId() ?: throw AccountException("Connexion Google non configurée.")
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build())
            .build()
        val credential = try {
            CredentialManager.create(activityContext).getCredential(activityContext, request).credential
        } catch (e: GetCredentialCancellationException) {
            return@guarded
        } catch (e: NoCredentialException) {
            throw AccountException("Aucun compte Google sur ce téléphone.")
        } catch (e: GetCredentialException) {
            throw AccountException("Connexion Google impossible : ${e.message}")
        }
        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            throw AccountException("Réponse Google inattendue.")
        }
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        val result = requireAuth().signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        onSignedIn(result.user, provider = "google")
    }

    fun signOut() {
        auth?.signOut()
    }

    /** Supprime le compte et toutes ses données en ligne (exigé par Google et le RGPD). */
    suspend fun deleteAccount() = guarded {
        val user = requireAuth().currentUser ?: return@guarded
        val userDoc = requireDb().collection(USERS).document(user.uid)
        userDoc.collection(LIBRARY).document(FAVORITES).delete().await()
        userDoc.delete().await()
        user.delete().await()
    }

    /** Suit le drapeau « vip » de la fiche du compte, posé à la main dans la console Firebase. */
    fun observeVip(uid: String): Flow<Boolean> = callbackFlow {
        val registration = requireDb().collection(USERS).document(uid).addSnapshotListener { snapshot, _ ->
            trySend(snapshot?.getBoolean("vip") == true)
        }
        awaitClose { registration.remove() }
    }

    /** Favoris enregistrés sur le compte, ou null s'il n'y en a pas encore. */
    suspend fun loadFavorites(uid: String): List<Map<String, Any?>>? {
        val snapshot = requireDb().collection(USERS).document(uid)
            .collection(LIBRARY).document(FAVORITES).get().await()
        @Suppress("UNCHECKED_CAST")
        return snapshot.get("items") as? List<Map<String, Any?>>
    }

    suspend fun saveFavorites(uid: String, items: List<Map<String, Any?>>) {
        requireDb().collection(USERS).document(uid)
            .collection(LIBRARY).document(FAVORITES)
            .set(mapOf("items" to items, "updatedAt" to FieldValue.serverTimestamp()))
            .await()
    }

    /** Crée ou met à jour la fiche du compte : le strict nécessaire, rien sur le fournisseur IPTV. */
    private suspend fun onSignedIn(user: FirebaseUser?, provider: String) {
        user ?: return
        val doc = requireDb().collection(USERS).document(user.uid)
        val data = mutableMapOf<String, Any?>(
            "email" to user.email,
            "displayName" to user.displayName,
            "provider" to provider,
            "lastLoginAt" to FieldValue.serverTimestamp(),
        )
        if (!doc.get().await().exists()) data["createdAt"] = FieldValue.serverTimestamp()
        doc.set(data, SetOptions.merge()).await()
    }

    private fun webClientId(): String? {
        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (id != 0) context.getString(id) else null
    }

    private fun requireAuth() = auth ?: throw AccountException("Les comptes ne sont pas configurés dans cette version.")
    private fun requireDb() = db ?: throw AccountException("Les comptes ne sont pas configurés dans cette version.")

    private suspend fun guarded(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: AccountException) {
            throw e
        } catch (e: Exception) {
            throw AccountException(frenchMessage(e))
        }
    }

    private fun frenchMessage(e: Exception): String = when (e) {
        is FirebaseAuthWeakPasswordException -> "Mot de passe trop faible : 6 caractères minimum."
        is FirebaseAuthUserCollisionException -> "Un compte existe déjà avec cette adresse."
        is FirebaseAuthInvalidUserException -> "Aucun compte avec cette adresse."
        is FirebaseAuthInvalidCredentialsException -> "Adresse ou mot de passe incorrect."
        is FirebaseAuthRecentLoginRequiredException -> "Par sécurité, reconnecte-toi puis réessaie."
        is FirebaseNetworkException -> "Pas de connexion Internet."
        is FirebaseAuthException -> "Erreur de compte : ${e.errorCode}"
        else -> "Erreur inattendue : ${e.message}"
    }

    private fun FirebaseUser.toAccountUser() = AccountUser(uid, email, displayName)

    private companion object {
        const val USERS = "users"
        const val LIBRARY = "library"
        const val FAVORITES = "favorites"
    }
}
