// =============================================================================
//  ALL YOUR TANK ARE BELONG TO US
//  Surveillance de température de 3 cuves de brasserie
// =============================================================================
//  Carte   : Arduino Nano ESP32
//  Sondes  : 3 × DS18B20 sur un seul bus 1-Wire (borne D2)
//  Écran   : OLED 2,42" 128×64 SSD1309 en I2C (bornes A4/A5), en portrait
//  Cloud   : Adafruit IO (tableau de bord + alertes par email)
//
//  Ce que fait le programme :
//    1. se connecte au WiFi puis à Adafruit IO ;
//    2. récupère la température cible et la fourchette de chaque cuve
//       (et les garde en mémoire pour le prochain démarrage) ;
//    3. lit les 3 sondes en continu et affiche les températures à l'écran ;
//    4. affiche « ALERTE » (clignotant) si une cuve sort de sa fourchette,
//       trop chaude ou trop froide ;
//    5. envoie les températures à Adafruit IO toutes les 20 secondes,
//       et l'état d'alerte quand il change (pour les emails) ;
//    6. accepte les mises à jour du programme par le WiFi (OTA).
//
//  Réglages : config.h        Mots de passe : secrets.h (voir secrets.example.h)
//  Licence  : MIT (voir le fichier LICENSE à la racine du dépôt)
// =============================================================================

// --- Bibliothèques (à installer depuis le gestionnaire de bibliothèques de
//     l'Arduino IDE, voir docs/installation.md)
#include <WiFi.h>
#include <ESPmDNS.h>
#include <WiFiUdp.h>
#include <ArduinoOTA.h>         // mises à jour par le WiFi
#include <OneWire.h>            // bus des sondes
#include <DallasTemperature.h>  // sondes DS18B20
#include <U8g2lib.h>            // écran OLED
#include <Wire.h>               // bus I2C de l'écran
#include <Preferences.h>        // mémoire qui survit aux coupures de courant
#include "AdafruitIO_WiFi.h"    // tableau de bord en ligne

// --- Réglages du projet
#include "config.h"

// --- Mots de passe (fichier personnel, jamais publié)
#if __has_include("secrets.h")
  #include "secrets.h"
#else
  #error "Fichier secrets.h manquant : copiez secrets.example.h en secrets.h et remplissez-le."
#endif


// =============================================================================
//  Objets principaux
// =============================================================================

AdafruitIO_WiFi io(IO_USERNAME, IO_KEY, WIFI_SSID, WIFI_PASS);

// Par cuve : température mesurée, cible et fourchette
AdafruitIO_Feed *feedT[3] = { io.feed(FEED_TEMP_1), io.feed(FEED_TEMP_2), io.feed(FEED_TEMP_3) };
AdafruitIO_Feed *feedC[3] = { io.feed(FEED_CIBLE_1), io.feed(FEED_CIBLE_2), io.feed(FEED_CIBLE_3) };
AdafruitIO_Feed *feedF[3] = { io.feed(FEED_FOURCHETTE_1), io.feed(FEED_FOURCHETTE_2), io.feed(FEED_FOURCHETTE_3) };
// État d'alerte global (pour les emails)
AdafruitIO_Feed *feedAlerte = io.feed(FEED_ALERTE);

OneWire oneWire(ONE_WIRE_BUS);
DallasTemperature sensors(&oneWire);
ECRAN_TYPE u8g2(ECRAN_ROTATION, U8X8_PIN_NONE);
Preferences prefs;

DeviceAddress addr[3] = {
  ADRESSE_SONDE_1,
  ADRESSE_SONDE_2,
  ADRESSE_SONDE_3
};


// =============================================================================
//  État du programme
// =============================================================================

// Dernière température lue (DEVICE_DISCONNECTED_C = -127 = sonde absente)
float temp[3] = { DEVICE_DISCONNECTED_C, DEVICE_DISCONNECTED_C, DEVICE_DISCONNECTED_C };
String nom[3] = NOMS_CUVES;
float cible[3] = CIBLES_DEFAUT;
float fourchette[3] = FOURCHETTES_DEFAUT;
bool enAlerte[3] = { false, false, false };   // cuve hors de sa fourchette ?

unsigned long lastCloud = 0;      // heure du dernier envoi des températures
unsigned long lastAlerte = 0;     // heure du dernier envoi du feed « alerte »
int codeAlerteEnvoye = -1;        // dernier état d'alerte envoyé (-1 = jamais)
bool altAlerte = false;           // fait clignoter le bandeau ALERTE


// =============================================================================
//  Réception des réglages depuis Adafruit IO
// =============================================================================
//  Quand on bouge un curseur sur le tableau de bord, Adafruit IO envoie la
//  nouvelle valeur à l'ESP32, qui l'applique et la garde en mémoire.

void majCible(int i, AdafruitIO_Data *d) {
  cible[i] = d->toFloat();
  prefs.putFloat(("c" + String(i)).c_str(), cible[i]);
}

void majFourchette(int i, AdafruitIO_Data *d) {
  float f = d->toFloat();
  if (f <= 0) return;   // une fourchette nulle ou négative n'a pas de sens
  fourchette[i] = f;
  prefs.putFloat(("f" + String(i)).c_str(), fourchette[i]);
}

// La bibliothèque Adafruit IO demande une fonction par feed
void cbC0(AdafruitIO_Data *d) { majCible(0, d); }
void cbC1(AdafruitIO_Data *d) { majCible(1, d); }
void cbC2(AdafruitIO_Data *d) { majCible(2, d); }
void cbF0(AdafruitIO_Data *d) { majFourchette(0, d); }
void cbF1(AdafruitIO_Data *d) { majFourchette(1, d); }
void cbF2(AdafruitIO_Data *d) { majFourchette(2, d); }


// =============================================================================
//  Démarrage
// =============================================================================

void setup() {
  Serial.begin(115200);
  delay(2000);

  // 1. Noms (fixés dans config.h), puis cibles et fourchettes gardées en mémoire
  for (int i = 0; i < 3; i++) {
    if (nom[i].length() > NOM_LONGUEUR_MAX) nom[i] = nom[i].substring(0, NOM_LONGUEUR_MAX);
  }
  prefs.begin(PREFS_NAMESPACE, false);
  for (int i = 0; i < 3; i++) {
    cible[i] = prefs.getFloat(("c" + String(i)).c_str(), cible[i]);
    fourchette[i] = prefs.getFloat(("f" + String(i)).c_str(), fourchette[i]);
  }

  // 2. Réveil matériel des broches (astuce du montage d'origine)
  byte pins[] = BROCHES_REVEIL;
  for (byte p : pins) { OneWire b(p); byte a[8]; b.search(a); }
  delay(500);

  // 3. Écran, puis connexion à Adafruit IO
  u8g2.begin();
  io.connect();

  feedC[0]->onMessage(cbC0); feedC[1]->onMessage(cbC1); feedC[2]->onMessage(cbC2);
  feedF[0]->onMessage(cbF0); feedF[1]->onMessage(cbF1); feedF[2]->onMessage(cbF2);

  // Écran d'attente avec des points animés tant que la connexion n'est pas faite.
  // ⚠️ Sans internet, le programme attend ici (voir docs/ameliorations.md).
  while (io.status() < AIO_CONNECTED) {
    int nb = ((millis() / 500) % 3) + 1;
    char pts[10] = "";
    for (int i = 0; i < nb; i++) strcat(pts, ".");
    u8g2.firstPage();
    do {
      u8g2.setFont(u8g2_font_6x10_tf);
      u8g2.setCursor(6, 25); u8g2.print("Bonjour !");
      u8g2.setCursor(4, 55); u8g2.print("Connexion a");
      u8g2.setCursor(4, 70); u8g2.print("Adafruit");
      u8g2.setCursor(4, 85); u8g2.print(pts);
    } while (u8g2.nextPage());
    delay(100);
  }

  // 4. Demander à Adafruit IO les dernières cibles et fourchettes enregistrées
  for (int i = 0; i < 3; i++) {
    feedC[i]->get();
    feedF[i]->get();
    delay(200);
  }

  // 5. Mises à jour par le WiFi (OTA)
  ArduinoOTA.setHostname(OTA_HOSTNAME);
  ArduinoOTA.setPassword(OTA_PASSWORD);
  ArduinoOTA.onStart([]() {
    u8g2.firstPage();
    do {
      u8g2.setFont(u8g2_font_6x10_tf);
      u8g2.setCursor(4, 55); u8g2.print("Mise a jour");
      u8g2.setCursor(4, 70); u8g2.print("OTA...");
    } while (u8g2.nextPage());
  });
  ArduinoOTA.begin();

  // 6. Message de bienvenue pendant 2 secondes
  u8g2.firstPage();
  do {
    u8g2.setFont(u8g2_font_6x10_tf);
    u8g2.setCursor(6, 25); u8g2.print("Bonjour !");
    u8g2.setCursor(4, 55); u8g2.print("Connexion");
    u8g2.setCursor(4, 70); u8g2.print("reussie a");
    u8g2.setCursor(4, 85); u8g2.print("Adafruit !");
  } while (u8g2.nextPage());
  delay(2000);

  // 7. Démarrage des sondes
  sensors.begin();
  for (int i = 0; i < 3; i++) sensors.setResolution(addr[i], RESOLUTION_SONDES);
}


// =============================================================================
//  Outils
// =============================================================================

// Écrit un nombre sans décimale inutile : 19 → "19", 18.5 → "18.5"
void formatNombre(char *buf, size_t taille, float v) {
  if (fabsf(v - roundf(v)) < 0.05f) snprintf(buf, taille, "%d", (int)roundf(v));
  else snprintf(buf, taille, "%.1f", v);
}

// Met à jour l'état d'alerte d'une cuve, avec une marge anti-clignotement :
// on entre en alerte dès qu'on sort de [cible - fourchette ; cible + fourchette],
// on n'en sort qu'en revenant de HYSTERESIS °C à l'intérieur.
void verifierCuve(int i) {
  if (temp[i] == DEVICE_DISCONNECTED_C) { enAlerte[i] = false; return; }

  float bas = cible[i] - fourchette[i];
  float haut = cible[i] + fourchette[i];

  if (!enAlerte[i]) {
    if (temp[i] > haut || temp[i] < bas) enAlerte[i] = true;
  } else {
    float marge = fminf((float)HYSTERESIS, fourchette[i] / 2);
    if (temp[i] <= haut - marge && temp[i] >= bas + marge) enAlerte[i] = false;
  }
}


// =============================================================================
//  Affichage d'une cuve (une bande de l'écran portrait 64 × 128)
// =============================================================================
//  Ligne du haut : nom de la cuve à gauche, « cible±fourchette » à droite.
//  Dessous, en gros : la température (en négatif si hors fourchette),
//  ou « ERR. SONDE » si la sonde ne répond pas.

void dessinerCuve(int i, int y) {
  // Nom de la cuve
  u8g2.setFont(u8g2_font_5x7_tf);
  u8g2.setCursor(0, y);
  u8g2.print(nom[i]);

  // Cible ± fourchette, en petit, alignée à droite (ex. « 18.5±1 »)
  char cBuf[8], fBuf[8], rBuf[20];
  formatNombre(cBuf, sizeof(cBuf), cible[i]);
  formatNombre(fBuf, sizeof(fBuf), fourchette[i]);
  snprintf(rBuf, sizeof(rBuf), "%s\xB1%s", cBuf, fBuf);   // \xB1 = signe ±
  u8g2.setFont(u8g2_font_4x6_tf);
  u8g2.drawStr(64 - u8g2.getStrWidth(rBuf), y, rBuf);

  // Température en gros
  if (temp[i] == DEVICE_DISCONNECTED_C) {
    u8g2.setFont(u8g2_font_6x10_tf);
    u8g2.setCursor(2, y + 16);
    u8g2.print("ERR. SONDE");
  } else {
    char tBuf[12] = "";
    dtostrf(temp[i], 4, 1, tBuf);
    strcat(tBuf, " C");
    if (enAlerte[i]) {
      u8g2.drawBox(0, y + 3, 64, 16);   // fond blanc...
      u8g2.setDrawColor(0);             // ...et texte noir
    }
    u8g2.setFont(u8g2_font_helvB12_tf);
    u8g2.setCursor(4, y + 16);
    u8g2.print(tBuf);
    u8g2.setDrawColor(1);
  }
}


// =============================================================================
//  Boucle principale (tourne en permanence)
// =============================================================================

void loop() {
  ArduinoOTA.handle();   // une mise à jour par le WiFi est-elle demandée ?
  io.run();              // garde la connexion Adafruit IO en vie
  unsigned long now = millis();

  // 1. Lire les 3 sondes et vérifier les fourchettes
  sensors.requestTemperatures();
  bool alerteGlob = false;
  int codeAlerte = 0;    // ex. 13 = cuves 1 et 3 hors fourchette
  for (int i = 0; i < 3; i++) {
    temp[i] = sensors.getTempC(addr[i]);
    // 85 °C exactement = valeur d'une sonde mal alimentée, pas une vraie mesure
    if (temp[i] == 85.0f) temp[i] = DEVICE_DISCONNECTED_C;
    verifierCuve(i);
    if (enAlerte[i]) {
      alerteGlob = true;
      codeAlerte = codeAlerte * 10 + (i + 1);
    }
  }

  // 2. Envoyer les températures à Adafruit IO (voir INTERVALLE_ENVOI_MS)
  if (now - lastCloud >= INTERVALLE_ENVOI_MS) {
    lastCloud = now;
    for (int i = 0; i < 3; i++) {
      if (temp[i] != DEVICE_DISCONNECTED_C) feedT[i]->save(temp[i]);
    }
  }

  // 3. Envoyer l'état d'alerte s'il a changé (au plus toutes les 30 s)
  if (codeAlerte != codeAlerteEnvoye && now - lastAlerte >= INTERVALLE_ALERTE_MS) {
    lastAlerte = now;
    codeAlerteEnvoye = codeAlerte;
    feedAlerte->save(codeAlerte);
  }

  // 4. Dessiner l'écran : 3 cuves empilées, puis le bandeau d'état en bas
  u8g2.firstPage();
  do {
    dessinerCuve(0, 12); u8g2.drawHLine(0, 33, 64);
    dessinerCuve(1, 46); u8g2.drawHLine(0, 67, 64);
    dessinerCuve(2, 80); u8g2.drawHLine(0, 101, 64);

    if (alerteGlob) {
      // Bandeau ALERTE qui alterne plein / contour à chaque tour de boucle
      u8g2.setFont(u8g2_font_7x14B_tf);
      if (altAlerte) {
        u8g2.drawBox(0, 104, 64, 24);
        u8g2.setDrawColor(0);
        u8g2.setCursor(11, 122); u8g2.print("ALERTE");
        u8g2.setDrawColor(1);
      } else {
        u8g2.drawFrame(0, 104, 64, 24);
        u8g2.setCursor(11, 122); u8g2.print("ALERTE");
      }
    } else {
      u8g2.setFont(u8g2_font_6x12_tf);
      u8g2.setCursor(6, 120); u8g2.print("Temp OK !");
    }
  } while (u8g2.nextPage());

  if (alerteGlob) altAlerte = !altAlerte;
  delay(50);
}
