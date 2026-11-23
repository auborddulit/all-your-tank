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
//  2. Noms des cuves, températures cibles et fourchettes
// -----------------------------------------------------------------------------

// Noms affichés à l'écran (6 caractères maximum, sans accent).
#define NOMS_CUVES              { "CUVE 1", "CUVE 2", "CUVE 3" }
#define NOM_LONGUEUR_MAX        6

// Température cible et fourchette (en °C) par défaut.
// Exemple : cible 19 et fourchette 1  →  tout va bien entre 18 et 20 °C.
// L'alerte se déclenche si la cuve est trop chaude OU trop froide.
//
// Ces valeurs ne servent qu'au tout premier démarrage. Ensuite, cibles et
// fourchettes se règlent avec les curseurs du tableau de bord Adafruit IO,
// et l'ESP32 les garde en mémoire même après une coupure de courant.
#define CIBLES_DEFAUT           { 19.0, 19.0, 19.0 }
#define FOURCHETTES_DEFAUT      { 1.0, 1.0, 1.0 }

// Marge anti-clignotement (°C) : une fois en alerte, la cuve doit revenir
// de 0,2 °C à l'intérieur de sa fourchette pour que l'alerte s'arrête.
// Évite une rafale d'alertes quand la température hésite sur la limite.
#define HYSTERESIS              0.2

// Nom de l'espace de mémoire interne où sont gardées cibles et fourchettes.
#define PREFS_NAMESPACE         "brasserie"


// -----------------------------------------------------------------------------
//  3. Adafruit IO (le tableau de bord en ligne)
// -----------------------------------------------------------------------------
//  Noms des « feeds » (flux de données) sur io.adafruit.com : 10 au total,
//  le maximum du compte gratuit. Voir docs/adafruit-io.md.

// Températures mesurées (envoyées par l'ESP32)
#define FEED_TEMP_1             "cuve1"
#define FEED_TEMP_2             "cuve2"
#define FEED_TEMP_3             "cuve3"

// Températures cibles (réglées avec un curseur du tableau de bord)
#define FEED_CIBLE_1            "cible-cuve1"
#define FEED_CIBLE_2            "cible-cuve2"
#define FEED_CIBLE_3            "cible-cuve3"

// Fourchettes ± (réglées avec un curseur du tableau de bord)
#define FEED_FOURCHETTE_1       "fourchette-cuve1"
#define FEED_FOURCHETTE_2       "fourchette-cuve2"
#define FEED_FOURCHETTE_3       "fourchette-cuve3"

// État d'alerte, envoyé par l'ESP32 pour déclencher les emails :
//   0  = toutes les cuves dans leur fourchette
//   2  = la cuve 2 est hors fourchette
//   13 = les cuves 1 et 3 sont hors fourchette, etc.
#define FEED_ALERTE             "alerte"

// Envoi des températures toutes les 20 secondes (en millisecondes).
// Le compte gratuit Adafruit IO accepte 30 valeurs par minute :
// 3 cuves × 3 envois par minute = 9 valeurs, on reste large.
#define INTERVALLE_ENVOI_MS     20000

// Le feed « alerte » n'est envoyé que quand l'état change,
// et au plus une fois toutes les 30 secondes.
#define INTERVALLE_ALERTE_MS    30000

// Une sonde qui ne répond plus depuis ce temps déclenche l'alerte « SONDE n »
// (2 minutes : on ignore un simple faux contact).
#define DELAI_SONDE_MUETTE_MS   120000

// Au démarrage, temps d'attente maximal d'internet. Passé ce délai, l'écran
// affiche quand même les températures (avec « HORS LIGNE ») et le boîtier
// continue d'essayer de se connecter en arrière-plan.
#define ATTENTE_CONNEXION_MS    30000


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
