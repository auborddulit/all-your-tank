# Installation du logiciel

Comptez une demi-heure la première fois. Tout se fait avec l'**Arduino IDE**, gratuit.

## 1. Installer l'Arduino IDE

Téléchargez la version 2 sur <https://www.arduino.cc/en/software> et installez-la.

## 2. Ajouter la carte Arduino Nano ESP32

1. Ouvrez **Outils > Carte > Gestionnaire de cartes**.
2. Cherchez **« Arduino ESP32 Boards »** (par Arduino) et cliquez sur **Installer**.
3. Branchez le Nano ESP32 en USB-C, puis choisissez **Outils > Carte > Arduino ESP32
   Boards > Arduino Nano ESP32** et le bon port dans **Outils > Port**.

> **Numérotation des broches** : le menu **Outils > Pin Numbering** propose deux
> réglages. Dans `config.h`, la broche des sondes est donnée par son numéro de
> **GPIO** (5 pour la borne D2). Si les sondes ne sont pas détectées, voir
> [depannage.md](depannage.md#les-numéros-de-broches-du-nano-esp32).

## 3. Installer les bibliothèques

Ouvrez **Outils > Gérer les bibliothèques**, cherchez et installez :

| Bibliothèque | Auteur | Rôle |
|---|---|---|
| **OneWire** | Paul Stoffregen | Bus des sondes |
| **DallasTemperature** | Miles Burton | Lecture des sondes DS18B20 |
| **U8g2** | oliver | Écran OLED |
| **Adafruit IO Arduino** | Adafruit | Tableau de bord en ligne |

Quand l'IDE propose d'installer aussi les **dépendances** (Adafruit MQTT,
ArduinoHttpClient…), acceptez avec **Install all**.

Les autres bibliothèques utilisées (WiFi, ArduinoOTA, Preferences…) sont fournies
avec la carte : rien à installer.

## 4. Récupérer le projet

Sur la page GitHub du projet : bouton vert **Code > Download ZIP**, puis
décompressez. Ouvrez le fichier `firmware/all-your-tank/all-your-tank.ino` : l'IDE
affiche aussi `config.h` et les autres fichiers dans des onglets.

## 5. Mettre vos mots de passe

1. Dans le dossier `firmware/all-your-tank/`, copiez `secrets.example.h` et
   renommez la copie **`secrets.h`**.
2. Ouvrez `secrets.h` et remplissez :
   - le nom et le mot de passe de votre WiFi ;
   - votre nom d'utilisateur et votre clé Adafruit IO (sur io.adafruit.com, icône de
     clé jaune) ;
   - un mot de passe de votre choix pour les mises à jour par le WiFi.

Le fichier `secrets.h` reste sur votre ordinateur : il est exclu du dépôt et ne sera
jamais publié.

> L'ESP32 ne capte que le WiFi **2,4 GHz**. Si votre box émet aussi en 5 GHz, utilisez
> le nom du réseau 2,4 GHz.

## 6. Trouver l'adresse de vos sondes

Chaque sonde a une adresse unique gravée en usine. Il faut l'indiquer dans `config.h`.

1. Ouvrez `outils/scan-sondes/scan-sondes.ino` et téléversez-le (bouton **→**).
2. Ouvrez le **moniteur série** (icône de loupe en haut à droite), réglé sur
   **115200 bauds**.
3. Il affiche une ligne par sonde, par exemple :
   ```text
   #define ADRESSE_SONDE_1         { 0x28, 0xC0, 0x01, 0xCB, 0x00, 0x00, 0x00, 0xE1 }   // 19.50 C
   ```
4. Pour savoir quelle sonde va dans quelle cuve, chauffez-en une dans la main et
   regardez laquelle monte.
5. Recopiez les trois lignes dans `config.h`, dans l'ordre des cuves 1, 2 et 3.

## 7. Créer le tableau de bord

Créez les feeds sur Adafruit IO **avant** de téléverser le programme principal :
voir [adafruit-io.md](adafruit-io.md).

## 8. Téléverser le programme

Ouvrez `firmware/all-your-tank/all-your-tank.ino` et cliquez sur **→ Téléverser**.
L'écran affiche « Bonjour ! Connexion a Adafruit… », puis les températures.

**Si le téléversement échoue** : appuyez **deux fois rapidement** sur le petit bouton
RST du Nano. La LED se met à pulser : la carte attend un programme. Choisissez à
nouveau le port dans **Outils > Port** et relancez.

## 9. Les mises à jour suivantes, par le WiFi

Une fois le programme installé, plus besoin de câble :

1. L'ordinateur doit être sur le **même réseau WiFi** que l'ESP32.
2. Dans **Outils > Port**, choisissez le port réseau **Gestion-Cuves-ESP32**.
3. Téléversez : l'IDE demande le mot de passe OTA mis dans `secrets.h`.
   L'écran affiche « Mise a jour OTA… » pendant l'envoi.
