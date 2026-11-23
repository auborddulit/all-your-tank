# Widget Android

Une petite appli Android qui affiche les températures des 3 cuves sur l'écran
d'accueil du téléphone, à partir de votre compte Adafruit IO.

- **Une ligne par cuve** : nom, température, cible et fourchette.
- **Rouge** quand une cuve est hors de sa fourchette (trop chaud ou trop froid),
  **gris** quand il n'y a pas eu de mesure depuis plus de 10 minutes.
- **Mise à jour** automatique environ toutes les 15 minutes (le minimum qu'Android
  autorise pour un widget), et immédiate quand on touche le widget.

## Installer l'appli

L'appli n'est pas sur le Play Store : GitHub la fabrique automatiquement à chaque
modification de ce dossier.

1. Sur le téléphone, ouvrez la page **Releases** du dépôt GitHub (colonne de droite de la
   page d'accueil du dépôt, ou adresse `…/all-your-tank/releases/latest`). Le dépôt étant
   privé, il faut être connecté à votre compte GitHub.
2. Touchez **all-your-tank-widget.apk** pour le télécharger.
3. Ouvrez le fichier téléchargé. Android demande d'autoriser l'installation
   d'applications depuis votre navigateur (« sources inconnues ») : acceptez, puis
   **Installer**.
4. Pour une nouvelle version, refaites la même chose : elle s'installe par-dessus
   l'ancienne, sans perdre vos réglages.

## Régler l'appli

1. Ouvrez l'appli **All your tank**.
2. Entrez votre **nom d'utilisateur** et votre **clé** Adafruit IO (sur io.adafruit.com,
   icône de clé jaune).
3. Donnez un nom à chaque cuve, par exemple le nom de la bière.
4. Touchez **Enregistrer et tester** : l'appli affiche les températures reçues.

La clé est gardée dans la mémoire privée de l'appli, inaccessible aux autres applis.
Si vous perdez le téléphone, régénérez la clé sur io.adafruit.com (**Regenerate Key**),
puis mettez la nouvelle dans le boîtier (`secrets.h`).

## Ajouter le widget

1. Appui long sur un espace vide de l'écran d'accueil.
2. **Widgets**, puis **All your tank**.
3. Faites glisser le widget sur l'écran, et agrandissez-le si besoin.

Touchez le widget pour le mettre à jour, touchez **Brasserie ⚙** pour ouvrir les réglages.

## Comment ça marche

L'appli lit en une seule requête la dernière valeur de tous les feeds du compte
(`GET https://io.adafruit.com/api/v2/{utilisateur}/feeds`), et utilise :

| Feed | Affiché comme |
|---|---|
| `cuve1` … `cuve3` | Température |
| `cible-cuve1` … | Cible |
| `fourchette-cuve1` … | Fourchette ± |

Lire des données ne compte pas dans la limite de 30 valeurs par minute du compte
gratuit, qui ne concerne que les envois.

## Pour les curieux : le code

| Fichier | Rôle |
|---|---|
| `MainActivity.kt` | Écran de réglages |
| `TankWidgetProvider.kt` | Le widget (événements d'Android, appui) |
| `Rafraichissement.kt` | Lecture régulière en tâche de fond (WorkManager) |
| `AdafruitIO.kt` | Requête vers Adafruit IO et mémoire des dernières valeurs |
| `Dessin.kt` | Dessin du widget (couleurs, textes) |
| `res/layout/widget_cuves.xml` | Mise en page du widget |

Fabrication : `.github/workflows/widget-android.yml`. Pour fabriquer à la main avec
Android Studio, ouvrez le dossier `android-widget/`.

La clé de signature `app/widget.keystore` est volontairement publiée avec le code : elle
permet seulement d'installer les nouvelles versions par-dessus les anciennes. Si vous
publiez votre propre version de l'appli, générez votre propre clé.
