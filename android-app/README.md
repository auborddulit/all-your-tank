# Appli Android

Une petite appli Android pour suivre et régler les 3 cuves depuis le téléphone,
avec un widget pour l'écran d'accueil. Elle passe toute seule en mode clair
(fond blanc) ou sombre (fond noir) selon le réglage du téléphone.

**Le widget** : pour chaque cuve, un cadran fin à graduations (un trait par degré, la
fourchette en ambre, une aiguille courte), la température et le nom. Une cuve trop chaude
s'affiche en rouge, trop froide en bleu, et en gris quand il n'y a pas eu de mesure
depuis plus de 10 minutes. Il se met à jour tout seul environ toutes les 15 minutes (le
minimum qu'Android autorise) ; touchez une cuve pour ouvrir l'appli dessus, ce qui
relit aussi les températures et met le widget à jour.

Le widget remplit la place qu'on lui donne (appui long sur le widget, puis tirer les
poignées) : cadrans et chiffres grandissent avec lui. Très large et bas, les trois
cadrans se répartissent sur toute la largeur ; plus haut que large, les cuves
s'empilent, une par ligne.

**L'appli** : un onglet par cuve, le grand cadran, la cible et la fourchette réglables
avec − / +, et des **préréglages** (« Garde à froid : 2 °C ±1 »…) qui appliquent cible
et fourchette d'un seul geste. Les réglages partent sur Adafruit IO, et le boîtier les
reçoit quelques secondes plus tard. Le bouton **↻** relit tout de suite les dernières
mesures (le boîtier en envoie toutes les 20 secondes) : pratique quand l'e-mail d'alerte
d'Adafruit arrive.

**Les notifications** : à chaque relevé (environ toutes les 15 minutes), l'appli vérifie
les cuves et prévient avec une notification, qui suit les réglages du téléphone (son,
vibration, pop-up) :

| Notification | Quand |
|---|---|
| « CUVE 2 trop chaude : 21,3 °C » (ou trop froide) | La température sort de cible ± fourchette |
| « CUVE 3 : la sonde ne mesure plus » | Une seule cuve n'a plus de mesure depuis plus de 10 min |
| « Le boîtier n'envoie plus rien » | Aucune cuve n'a de mesure depuis plus de 10 min (courant, WiFi) |

Tant que le problème dure, un rappel arrive toutes les heures. Quand tout redevient
normal, la notification s'efface d'elle-même. Touchez-la pour ouvrir l'appli sur la cuve
concernée. Pour une cuve vide, un préréglage « Vide » avec une très grande fourchette
évite les alertes. Au premier lancement, Android (13 et plus) demande l'autorisation
d'afficher des notifications : répondez **Autoriser**. Le son et le pop-up se règlent
dans les paramètres du téléphone : Applications → All your tank → Notifications →
« Alertes des cuves ».

## Installer l'appli

L'appli n'est pas sur le Play Store : GitHub la fabrique automatiquement à chaque
modification de ce dossier.

1. Sur le téléphone, ouvrez la page **Releases** du dépôt GitHub (colonne de droite de la
   page d'accueil du dépôt, ou adresse `…/all-your-tank/releases/latest`). Le dépôt étant
   privé, il faut être connecté à votre compte GitHub.
2. Touchez **all-your-tank.apk** pour le télécharger.
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
4. Réglez l'**échelle du cadran** : la température du début et de la fin du grand cadran
   (par défaut -5 à 35 °C, au moins 5 °C d'écart). Le widget garde 0 à 30 °C.
5. Modifiez la liste des **préréglages** si besoin : nom, cible en °C, fourchette en ± °C.
6. Touchez **Enregistrer et tester**.

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
| `Alertes.kt` | Notifications : hors fourchette, sonde en erreur, boîtier silencieux |
| `AdafruitIO.kt` | Lecture et envoi vers Adafruit IO, mémoire des dernières valeurs |
| `Dessin.kt` | Dessin du widget, adapté à sa taille (3 dispositions) |
| `res/values/colors.xml`, `res/values-night/colors.xml` | Palette « Ambre », clair et sombre |

Fabrication : `.github/workflows/appli-android.yml`. Pour fabriquer à la main avec
Android Studio, ouvrez le dossier `android-app/`.

**Clé de signature.** Elle n'est pas dans le code : elle est gardée dans les secrets
GitHub du dépôt (**Settings > Secrets and variables > Actions**) :

| Secret | Contenu |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | le fichier de clé (PKCS12, alias `appli`), encodé en base64 |
| `ANDROID_KEYSTORE_PASSWORD` | son mot de passe (le même pour la clé) |

Pour votre propre copie du projet, créez votre clé puis ces deux secrets :

```
keytool -genkeypair -keystore appli.p12 -storetype PKCS12 -alias appli \
        -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 appli.p12    # à coller dans ANDROID_KEYSTORE_BASE64
```

Sans ces secrets, l'appli est signée avec la clé de test d'Android : elle s'installe,
mais pas par-dessus une version signée avec une autre clé.
