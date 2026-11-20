// =============================================================================
//  config.h : TOUS LES RÉGLAGES DU PROJET SONT ICI
// =============================================================================
//  C'est le seul fichier à modifier pour adapter le projet à votre brasserie.
//  Les mots de passe ne sont PAS ici : ils sont dans secrets.h
//  (voir secrets.example.h).
// =============================================================================
#pragma once


// -----------------------------------------------------------------------------
//  1. Sondes de température DS18B20
// -----------------------------------------------------------------------------

// Broche du bus 1-Wire (les 3 sondes sont branchées en parallèle dessus).
// ⚠️ Sur l'Arduino Nano ESP32, la borne marquée « D2 » correspond au GPIO 5
// de la puce. La bibliothèque OneWire utilise le numéro de GPIO, d'où le 5.
// Voir docs/depannage.md pour le tableau de correspondance.
#define ONE_WIRE_BUS            5

// Précision des sondes : 9 (0,5 °C, rapide) à 12 bits (0,0625 °C, lent).
// 11 bits = 0,125 °C, lecture en 375 ms environ.
#define RESOLUTION_SONDES       11

// Adresse unique de chaque sonde (gravée en usine, 8 octets).
// Pour connaître les adresses de VOS sondes : téléversez d'abord le croquis
// outils/scan-sondes/scan-sondes.ino et recopiez ce qu'il affiche.
// L'ordre ici donne l'ordre des cuves à l'écran (1, 2, 3).
#define ADRESSE_SONDE_1         { 0x28, 0xC0, 0x01, 0xCB, 0x00, 0x00, 0x00, 0xE1 }
#define ADRESSE_SONDE_2         { 0x28, 0xD2, 0x7D, 0xCC, 0x00, 0x00, 0x00, 0x1E }
#define ADRESSE_SONDE_3         { 0x28, 0x04, 0x99, 0xC9, 0x00, 0x00, 0x00, 0x2C }

// Broches « réveillées » au démarrage (astuce du montage d'origine, voir
// docs/ameliorations.md). À laisser telles quelles si tout fonctionne.
#define BROCHES_REVEIL          {2, 3, 4, 5}


// -----------------------------------------------------------------------------
//  2. Noms et seuils d'alerte par défaut
// -----------------------------------------------------------------------------
//  Ces valeurs ne servent qu'au tout premier démarrage. Ensuite, les noms et
//  les seuils se règlent depuis le tableau de bord Adafruit IO, et l'ESP32
//  les garde en mémoire même après une coupure de courant.

#define NOMS_DEFAUT             { "CUVE 1", "CUVE 2", "CUVE 3" }
#define NOM_DEFAUT_PREFIXE      "CUVE "       // donne "CUVE 1", "CUVE 2"...
#define NOM_LONGUEUR_MAX        6             // l'écran n'affiche que 6 lettres

// Seuil d'alerte en °C : l'alerte se déclenche quand la température
// atteint ou dépasse ce seuil.
#define SEUILS_DEFAUT           { 40.0, 45.0, 35.5 }

// Nom de l'espace de mémoire interne où sont gardés noms et seuils.
#define PREFS_NAMESPACE         "brasserie"


// -----------------------------------------------------------------------------
//  3. Adafruit IO (le tableau de bord en ligne)
// -----------------------------------------------------------------------------
//  Noms des « feeds » (flux de données) à créer sur io.adafruit.com.
//  Voir docs/adafruit-io.md.

// Températures mesurées (envoyées par l'ESP32)
#define FEED_TEMP_1             "cuve1"
#define FEED_TEMP_2             "cuve2"
#define FEED_TEMP_3             "cuve3"

// Noms des cuves (réglés depuis le tableau de bord)
#define FEED_NOM_1              "nom-cuve1"
#define FEED_NOM_2              "nom-cuve2"
#define FEED_NOM_3              "nom-cuve3"

// Seuils d'alerte (réglés depuis le tableau de bord)
#define FEED_SEUIL_1            "seuil-cuve1"
#define FEED_SEUIL_2            "seuil-cuve2"
#define FEED_SEUIL_3            "seuil-cuve3"

// Envoi des températures toutes les 20 secondes (en millisecondes).
// Le compte gratuit Adafruit IO accepte 30 valeurs par minute :
// 3 cuves × 3 envois par minute = 9 valeurs, on reste large.
#define INTERVALLE_ENVOI_MS     20000


// -----------------------------------------------------------------------------
//  4. Écran OLED
// -----------------------------------------------------------------------------
//  Module OLED 2,42" 128×64, contrôleur SSD1309, branché en I2C (A4/A5).
//  Pour un autre écran, changez le type (liste des écrans U8g2 :
//  https://github.com/olikraus/u8g2/wiki/u8g2setupcpp).

#define ECRAN_TYPE              U8G2_SSD1309_128X64_NONAME0_F_HW_I2C

// Orientation : U8G2_R0 = paysage, U8G2_R1 = portrait (tourné de 90°),
// U8G2_R2 = paysage retourné, U8G2_R3 = portrait retourné.
// L'affichage est dessiné pour le portrait (64 px de large, 128 de haut).
#define ECRAN_ROTATION          U8G2_R1


// -----------------------------------------------------------------------------
//  5. Mise à jour à distance (OTA)
// -----------------------------------------------------------------------------
//  Nom de l'ESP32 sur le réseau, visible dans l'Arduino IDE
//  (Outils > Port > ports réseau). Le mot de passe est dans secrets.h.

#define OTA_HOSTNAME            "Gestion-Cuves-ESP32"
