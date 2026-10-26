# Historique des versions de Zynora

Le numéro de version affiché dans l'appli est `0.1.N`, où `N` est le
numéro du build GitHub Actions qui a produit l'APK. Les builds des pull
requests et les builds en échec ne donnent pas de version publiée, d'où les
numéros qui sautent.

Chaque version a aussi sa page dans les
[Releases GitHub](https://github.com/misterwhite44/iptv_smart/releases)
(tag `v0.1.N`), créée automatiquement à chaque mise à jour de `main` avec la
liste des changements. Le lien de téléchargement habituel ne change pas :
https://github.com/misterwhite44/iptv_smart/releases/download/latest/Zynora.apk

## Prochaine version
- Nouveau logo Zynora : le Z violet lumineux remplace l'ancienne télé, sur
  l'icône de l'appli (toutes les formes d'icônes Android), l'écran de
  démarrage, les écrans de connexion et en haut de l'accueil.
- Icône 512 px pour la fiche Play Store dans `play-store/icone-512.png`.

## 0.1.41 — 26/10/2026
- « En cours » ne montre plus que les films et les séries : les chaînes en
  direct restent dans « Chaînes récentes ».
- Zapper entre beaucoup de chaînes ne fait plus disparaître de l'historique
  les films et épisodes en cours.

## 0.1.39 — 26/10/2026
- Accueil : nouvelle rangée « En cours » juste sous l'affiche, avec les films
  et épisodes entamés (barre de progression, temps restant) et les chaînes
  regardées dans les dernières 24 h (badge DIRECT). Elle apparaît aussi,
  filtrée, dans les onglets Films, Séries et Direct.
- Un épisode en cours reprend directement à la bonne minute, sans repasser
  par la fiche de la série ; les épisodes suivants s'enchaînent.
- Appui long sur une carte « En cours » pour la retirer.
- La position est aussi gardée pour les films dont le serveur n'annonce pas
  la durée.

## 0.1.35 — 25/10/2026
- Corrige l'erreur 429 (« trop de requêtes ») au lancement : les requêtes vers
  le serveur Xtream partent deux par deux, celles refusées (429/503) sont
  réessayées après une pause, et l'abonnement n'est vérifié qu'à l'ouverture
  du Profil.

## 0.1.34 — 25/10/2026
- Le nom affiché sous l'icône devient « Zynora IPTV ».

## 0.1.32 — 25/10/2026
- Identifiant définitif de l'appli : `com.zynora.app`, avec sa configuration
  Firebase. ⚠️ Cette version s'installe à côté de l'ancienne appli au lieu de
  la remplacer.
- Textes de la fiche Play Store (dossier `play-store/`).

## 0.1.30 — 25/10/2026
- Profil : détail de l'abonnement Premium (formule mensuelle ou à vie,
  renouvellement ou résiliation, date d'achat) et bouton « Gérer mon
  abonnement » vers Google Play.

## 0.1.28 — 25/10/2026
- Premium : abonnement à 4 €/mois et achat à vie à 30 € via Google Play,
  restauration des achats, accès VIP posé à la main dans Firebase.
- Chromecast réservé à Premium ; en gratuit, le bouton ouvre l'offre.
- Profil : statut Premium et bouton « Passer à Premium ».

## 0.1.26 — 25/10/2026
- Pages légales : nom du responsable et email de contact.

## 0.1.24 — 25/10/2026
- Politique de confidentialité et page de suppression de compte (GitHub
  Pages), liées depuis l'écran de compte et le Profil.

## 0.1.22 — 25/10/2026
- Comptes Zynora : connexion par email/mot de passe ou Google, création de
  compte, mot de passe oublié.
- Favoris sauvegardés sur le compte et restaurés à la connexion.
- Profil : déconnexion, suppression du compte et de ses données,
  « Changer de fournisseur » séparé.

## 0.1.18 — 25/10/2026
- Préparation au Play Store : Canal+ / Eurosport FR retirés des favoris par
  défaut (les favoris existants restent, et passent en tête du Direct).
- Avertissement au premier lancement : Zynora ne fournit aucun contenu.
- Build AAB signé pour le Play Store ; cible Android 16 (API 36).

## 0.1.15 — 24/10/2026
- Cast : les chaînes sont envoyées à la Chromecast comme du direct (HLS en
  MPEG-TS) pour qu'elle démarre la lecture ; message si la télé ne démarre
  rien au bout de 25 s.

## 0.1.12 — 24/10/2026
- Cast : corrige le plantage de la fenêtre de choix de la Chromecast.

## 0.1.11 — 24/10/2026
- Cast : le téléphone coupe son flux avant de caster et la Chromecast reçoit
  un seul élément à la fois, pour éviter l'erreur 429 avec les abonnements
  limités à une connexion ; la lecture réessaie si le serveur n'a pas encore
  libéré la connexion.

## 0.1.10 — 24/10/2026
- Nouvel accueil : grande affiche plein écran, sections Pour toi / Films /
  Séries / Direct, menu à gauche sur écran large et barre du bas sur
  téléphone.
- Canal+ FR et Eurosport FR ajoutés aux favoris et en tête du Direct.

## 0.1.9 — 24/10/2026
- Recherche fluide : elle tourne en arrière-plan au lieu de bloquer l'appli
  avec les gros catalogues.
- Nouvelle page Profil.

## 0.1.8 — 24/10/2026
- L'appli devient Zynora, avec un thème violet.
- Corrige la fermeture de l'appli après la connexion : les gros catalogues
  sont lus en flux au lieu d'être chargés entiers en mémoire.
- Rapport de plantage affiché au lancement suivant en cas de problème.

## 0.1.6 — 24/10/2026
- README : fonctions et limites connues.

## 0.1.5 — 24/10/2026
- Cast vers Chromecast depuis le lecteur et l'accueil.

## 0.1.4 — 24/10/2026
- Lecteur vidéo, historique « Reprendre la lecture » et favoris.

## 0.1.3 — 24/10/2026
- Catalogue : accueil, onglets Direct / Films / Séries, fiches et recherche.

## 0.1.1 — 24/10/2026
- Première version : connexion à un compte Xtream Codes (URL, identifiant,
  mot de passe).
