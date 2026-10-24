# IPTV Smart

Application Android IPTV pour comptes **Xtream Codes** : chaînes en direct, films, séries,
historique de lecture et cast vers Chromecast. Interface sombre inspirée de Disney+.

## Installer l'APK sur le téléphone

1. Sur ton téléphone, ouvre la page **Releases** du dépôt et télécharge `IPTVSmart.apk`
   (release « latest », mise à jour à chaque nouvelle version).
2. Ouvre le fichier. Android demande d'autoriser l'installation depuis ton navigateur :
   accepte, puis appuie sur **Installer**.
3. Les versions suivantes s'installent par-dessus, sans perdre ta connexion ni ton historique.

## Se connecter

Entre l'URL du serveur (ex. `http://exemple.com:8080`), ton identifiant et ton mot de passe
Xtream. Tu peux aussi coller directement ton lien M3U complet dans le champ URL :
l'identifiant et le mot de passe sont remplis automatiquement.

## Développement

- Kotlin, Jetpack Compose, Material 3
- Android 8.0 (API 26) minimum
- L'APK est compilé par GitHub Actions (`.github/workflows/build-apk.yml`) à chaque push.
- Compilation locale : `./gradlew assembleRelease`
