# Play Console : réponses « Sécurité des données »

À recopier dans Play Console → Contenu de l'application → Sécurité des données.
Doit rester cohérent avec la politique de confidentialité (`docs/index.html`).

## Collecte et sécurité

| Question | Réponse |
| --- | --- |
| L'appli collecte-t-elle ou partage-t-elle des données ? | Oui |
| Les données sont-elles chiffrées en transit ? | Oui |
| Les utilisateurs peuvent-ils demander la suppression de leurs données ? | Oui (dans l'appli et via https://misterwhite44.github.io/iptv_smart/suppression-compte.html) |
| Création de compte | Oui : nom d'utilisateur et mot de passe, et connexion Google |
| Lien de suppression de compte | https://misterwhite44.github.io/iptv_smart/suppression-compte.html |

## Données collectées (aucune n'est partagée avec des tiers)

| Type Play Console | Donnée Zynora | Obligatoire ? | Finalité |
| --- | --- | --- | --- |
| Informations personnelles → Adresse e-mail | Email du compte | Oui | Gestion du compte |
| Informations personnelles → Nom | Nom du compte Google | Non (Google seulement) | Gestion du compte |
| Informations personnelles → ID utilisateur | Identifiant Firebase du compte | Oui | Gestion du compte |
| Activité dans l'appli → Autres actions | Favoris | Non | Fonctionnalités de l'appli |
| Informations financières → Historique des achats | Statut Premium (étape 5) | Non | Fonctionnalités de l'appli |

Non collecté : identifiants du fournisseur IPTV, historique de lecture, position, contacts,
journaux de plantage (le rapport de plantage reste sur le téléphone), identifiants publicitaires.

# Registre des traitements (RGPD, article 30)

| Champ | Contenu |
| --- | --- |
| Responsable | Louis Boucard, louis44.boucard@proton.me |
| Traitement | Gestion des comptes utilisateurs de l'appli Zynora |
| Finalités | Création et connexion au compte, sauvegarde des favoris, gestion de l'abonnement Premium |
| Base légale | Exécution du contrat (art. 6.1.b) |
| Personnes concernées | Utilisateurs de l'appli (15 ans et plus) |
| Données | Email, nom (Google), identifiant de compte, mode de connexion, dates de création et de connexion, favoris, statut Premium |
| Destinataires | Le responsable uniquement |
| Sous-traitants | Google Ireland / Google LLC (Firebase Authentication, Cloud Firestore europe-west1), Google Play (paiements) |
| Transferts hors UE | États-Unis (Firebase), encadrés par le Data Privacy Framework et les clauses contractuelles types |
| Durée de conservation | Durée du compte ; suppression immédiate (appli) ou sous 30 jours (email) ; comptes inactifs supprimés après 3 ans |
| Sécurité | Chiffrement en transit, règles d'accès Firestore par utilisateur, mots de passe hachés par Firebase, identifiants IPTV chiffrés sur l'appareil uniquement |
