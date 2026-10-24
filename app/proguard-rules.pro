# Annotations de compilation utilisées par Tink (security-crypto), absentes à l'exécution.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**

# Tink (chiffrement des identifiants) lit ses messages protobuf par réflexion.
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.api.client.**
-dontwarn org.joda.time.**
-keepclassmembers class * extends com.google.crypto.tink.shaded.protobuf.GeneratedMessageLite { <fields>; }

# Classe chargée par son nom depuis le manifeste par le framework Cast.
-keep class com.misterwhite.iptvsmart.cast.CastOptionsProvider { *; }

# Credential Manager (« Continuer avec Google ») charge son implémentation par réflexion.
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** {
  *;
}
