# Zynora

Application Android IPTV pour comptes **Xtream Codes** : chaînes en direct, films, séries,
historique de lecture et cast vers Chromecast. Interface sombre inspirée de Disney+.

## Installer l'APK sur le téléphone

1. Sur ton téléphone, ouvre la page **Releases** du dépôt et télécharge `Zynora.apk`
   (release « latest », mise à jour à chaque nouvelle version).
2. Ouvre le fichier. Android demande d'autoriser l'installation depuis ton navigateur :
   accepte, puis appuie sur **Installer**.
3. Les versions suivantes s'installent par-dessus, sans perdre ta connexion ni ton historique.

## Fonctions

- **Accueil façon Disney+** : carrousel à la une, « Reprendre la lecture », « Ma liste »,
  chaînes récentes, nouveautés et rangées par catégorie.
- **Direct, Films, Séries** : filtres par catégorie et par favoris, grille d'affiches.
- **Fiches** films et séries : résumé, casting, saisons et épisodes, ajout à « Ma liste ».
- **Lecteur** plein écran : reprise là où tu t'étais arrêté, épisode suivant automatique,
  zapping entre les chaînes d'une catégorie, pistes audio et sous-titres, format de l'image.
- **Recherche** dans tout le catalogue (sans tenir compte des accents).
- **Chromecast** : bouton Cast sur l'accueil et dans le lecteur. La lecture passe sur la
  télé à la même position, et revient sur le téléphone quand tu arrêtes le cast.

## Se connecter

Entre l'URL du serveur (ex. `http://exemple.com:8080`), ton identifiant et ton mot de passe
Xtream. Tu peux aussi coller directement ton lien M3U complet dans le champ URL :
l'identifiant et le mot de passe sont remplis automatiquement.

## Limites connues

- La Chromecast lit les chaînes en HLS (m3u8) et les films en MP4. Certains films en MKV
  ou AVI ne passent qu'en lecture sur le téléphone.
- Sur certains téléphones, le son AC3/DTS de quelques films n'est pas pris en charge.

## Développement

- Kotlin, Jetpack Compose, Material 3
- Android 8.0 (API 26) minimum
- L'APK est compilé par GitHub Actions (`.github/workflows/build-apk.yml`) à chaque push.
- Compilation locale : `./gradlew assembleRelease`
