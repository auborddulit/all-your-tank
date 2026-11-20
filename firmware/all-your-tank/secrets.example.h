// =============================================================================
//  secrets.example.h : MODÈLE pour vos mots de passe
// =============================================================================
//  1. Copiez ce fichier et nommez la copie « secrets.h » (même dossier).
//  2. Remplissez secrets.h avec vos vraies valeurs.
//  3. Ne publiez JAMAIS secrets.h : il est déjà exclu du dépôt par .gitignore.
//
//  Astuce : si un mot de passe contient un guillemet ", écrivez-le \"
//  (exemple : le mot de passe  ab"cd  s'écrit  "ab\"cd").
// =============================================================================
#pragma once

// --- Votre réseau WiFi (la box 4G ou votre box internet)
#define WIFI_SSID       "nom_de_votre_wifi"
#define WIFI_PASS       "mot_de_passe_wifi"

// --- Votre compte Adafruit IO
// Sur io.adafruit.com : cliquez sur l'icône de clé jaune (« My Key »).
#define IO_USERNAME     "votre_nom_utilisateur_adafruit"
#define IO_KEY          "aio_xxxxxxxxxxxxxxxxxxxxxxxxxxxx"

// --- Mot de passe demandé pour mettre à jour l'ESP32 par le WiFi (OTA)
// Choisissez-en un nouveau, différent de votre mot de passe WiFi.
#define OTA_PASSWORD    "choisissez_un_mot_de_passe"
