# Appli et widget Android

Une petite appli Android pour suivre et régler les 3 cuves depuis le téléphone,
avec un widget pour l'écran d'accueil. Elle passe toute seule en mode clair
(fond blanc) ou sombre (fond noir) selon le réglage du téléphone.

**Le widget** : un cadran par cuve. La bande ambre montre la fourchette, l'aiguille
la température. Une cuve trop chaude s'affiche en rouge, trop froide en bleu, et en
gris quand il n'y a pas eu de mesure depuis plus de 10 minutes. Il se met à jour tout
seul environ toutes les 15 minutes (le minimum qu'Android autorise) ; touchez l'heure
pour le mettre à jour tout de suite, touchez une cuve pour ouvrir l'appli dessus.

**L'appli** : un onglet par cuve, le grand cadran, la cible et la fourchette réglables
avec − / +, et des **préréglages** (« Garde à froid : 2 °C ±1 »…) qui appliquent cible
et fourchette d'un seul geste. Les réglages partent sur Adafruit IO, et le boîtier les
reçoit quelques secondes plus tard.

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

1. Ouvrez l'appli **All your tank**, puis **Réglages**.
2. Entrez votre **nom d'utilisateur** et votre **clé** Adafruit IO (sur io.adafruit.com,
   icône de clé jaune).
3. Donnez un nom à chaque cuve, par exemple le nom de la bière.
4. Modifiez la liste des **préréglages** si besoin : nom, cible en °C, fourchette en ± °C.
5. Touchez **Enregistrer et tester**.

La clé est gardée dans la mémoire privée de l'appli, inaccessible aux autres applis.
Si vous perdez le téléphone, régénérez la clé sur io.adafruit.com (**Regenerate Key**),
puis mettez la nouvelle dans le boîtier (`secrets.h`).

## Ajouter le widget

1. Appui long sur un espace vide de l'écran d'accueil.
2. **Widgets**, puis **All your tank**.
3. Faites glisser le widget sur l'écran, et agrandissez-le si besoin.


## Comment ça marche

L'appli lit en une seule requête la dernière valeur de tous les feeds du compte
(`GET https://io.adafruit.com/api/v2/{utilisateur}/feeds`), et écrit la consigne
dans les feeds `cible-cuveX` et `fourchette-cuveX` (`POST …/feeds/{feed}/data`) :

| Feed | Rôle |
|---|---|
| `cuve1` … `cuve3` | Température (lue) |
| `cible-cuve1` … | Cible (lue et écrite) |
| `fourchette-cuve1` … | Fourchette ± (lue et écrite) |

Lire ne compte pas dans la limite de 30 valeurs par minute du compte gratuit. Chaque
réglage envoyé compte pour 2 valeurs ; avec − / +, l'appli attend 1,5 seconde après le
dernier appui avant d'envoyer, pour ne pas multiplier les envois.

## Pour les curieux : le code

| Fichier | Rôle |
|---|---|
| `MainActivity.kt` | Écran des cuves (cadran, consigne, préréglages) |
| `ReglagesActivity.kt` | Compte, noms des cuves, éditeur de préréglages |
| `Jauge.kt` | Dessin du cadran (appli et widget) |
| `Cuve.kt` | État d'une cuve (trop chaud, trop froid…) et mise en forme des nombres |
| `Prereglages.kt` | Liste des préréglages |
| `Palette.kt` | Couleurs du mode clair et du mode sombre |
| `TankWidgetProvider.kt` | Le widget (événements d'Android, appuis) |
| `Rafraichissement.kt` | Lecture régulière en tâche de fond (WorkManager) |
| `AdafruitIO.kt` | Lecture et envoi vers Adafruit IO, mémoire des dernières valeurs |
| `Dessin.kt` | Dessin du widget |
| `res/values/colors.xml`, `res/values-night/colors.xml` | Palette « Ambre », clair et sombre |

Fabrication : `.github/workflows/widget-android.yml`. Pour fabriquer à la main avec
Android Studio, ouvrez le dossier `android-widget/`.

La clé de signature `app/widget.keystore` est volontairement publiée avec le code : elle
permet seulement d'installer les nouvelles versions par-dessus les anciennes. Si vous
publiez votre propre version de l'appli, générez votre propre clé.
