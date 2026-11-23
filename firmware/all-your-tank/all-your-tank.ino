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
//    1. affiche l'écran de démarrage animé (ecran_demarrage.h) et se connecte
//       au WiFi puis à Adafruit IO ;
//    2. récupère la température cible et la fourchette de chaque cuve
//       (et les garde en mémoire pour le prochain démarrage) ;
//    3. lit les 3 sondes en continu et affiche les températures à l'écran ;
//    4. affiche « ALERTE » (clignotant) si une cuve sort de sa fourchette,
//       trop chaude ou trop froide ;
//    5. envoie les températures à Adafruit IO toutes les 20 secondes,
//       et l'état d'alerte quand il change (pour les emails) : « OK »,
//       « CHAUD 1 : 21.3 C (19±1) », « SONDE 3 » (sonde muette)… ;
//    6. accepte les mises à jour du programme par le WiFi (OTA) ;
//    7. sans internet, continue d'afficher les températures (« HORS LIGNE »)
//       et retente la connexion en arrière-plan ;
//    8. fonctionne aussi derrière une box 4G qui ne donne que de l'IPv6
//       (RESEAU_IPV6_SEUL dans secrets.h, voir docs/reseau-ipv6.md).
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
#include "ecran_demarrage.h"   // écran de démarrage animé

// --- Mots de passe (fichier personnel, jamais publié)
#if __has_include("secrets.h")
  #include "secrets.h"
#else
  #error "Fichier secrets.h manquant : copiez secrets.example.h en secrets.h et remplissez-le."
#endif

// --- Réseau en IPv6 seul (voir docs/reseau-ipv6.md). Si la ligne n'est pas
//     dans votre secrets.h, on suppose un réseau classique.
#ifndef RESEAU_IPV6_SEUL
  #define RESEAU_IPV6_SEUL false
#endif
#if RESEAU_IPV6_SEUL && (!defined(ESP_ARDUINO_VERSION_MAJOR) || ESP_ARDUINO_VERSION_MAJOR < 3)
  #error "RESEAU_IPV6_SEUL demande le paquet de cartes « esp32 » d'Espressif, version 3 ou plus (voir docs/reseau-ipv6.md)."
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

// Heure où chaque sonde a cessé de répondre (0 = elle répond)
unsigned long sondeMuetteDepuis[3] = { 0, 0, 0 };

bool enLigne = false;             // connecté à Adafruit IO ?
bool otaDemarre = false;          // mises à jour par le WiFi prêtes ? (il faut le WiFi)

unsigned long lastCloud = 0;      // heure du dernier envoi des températures
unsigned long lastAlerte = 0;     // heure du dernier envoi du feed « alerte »
String alerteEnvoyee = "";        // dernier texte envoyé dans « alerte » ("" = jamais)
unsigned long dernierEssaiWifi = 0;   // pour relancer le WiFi s'il ne revient pas

// Lecture des sondes sans bloquer : on lance une mesure, on la lit plus tard
const unsigned long DUREE_MESURE_MS = (750UL >> (12 - RESOLUTION_SONDES)) + 10;
bool mesureEnCours = false;
unsigned long debutMesure = 0;

bool altAlerte = false;           // fait clignoter le bandeau ALERTE
unsigned long dernierClignotement = 0;
unsigned long dernierAffichage = 0;

// Fonctions écrites plus bas mais utilisées avant (l'Arduino IDE s'en passe,
// d'autres outils non)
void demanderReglages();
void demarrerOTA();
void verifierCuve(int i);


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

// Le réseau est-il prêt pour joindre Adafruit IO ?
//  - réseau classique : il suffit d'être connecté au WiFi ;
//  - réseau IPv6 seul : il faut aussi notre adresse IPv6 (elle arrive quelques
//    secondes après le WiFi), sinon le premier essai partirait en IPv4, échouerait,
//    et la bibliothèque Adafruit IO attendrait une minute avant de réessayer.
//    On impose aussi un serveur DNS « DNS64 » (celui de Google) : il donne une
//    adresse IPv6 spéciale (64:ff9b:…) pour les sites restés en IPv4, comme
//    io.adafruit.com, et l'opérateur fait le pont vers l'IPv4.
#if RESEAU_IPV6_SEUL
const char *DNS64[2] = { "2001:4860:4860::6464", "2001:4860:4860::64" };
#endif

bool reseauPret() {
  if (WiFi.status() != WL_CONNECTED) return false;
#if RESEAU_IPV6_SEUL
  if (!WiFi.STA.hasGlobalIPv6()) return false;
  for (int i = 0; i < 2; i++) {
    IPAddress dns;
    dns.fromString(DNS64[i]);
    if (WiFi.STA.dnsIP(i) != dns) WiFi.STA.dnsIP(i, dns);   // le WiFi peut l'écraser
  }
#endif
  return true;
}

// Message affiché en bas de l'écran de démarrage, avec des points qui défilent
unsigned long debutDemarrage = 0;
const char *messageConnexion() {
  static char texte[16];
  const char *etape = "ADAFRUIT";
  if (WiFi.status() != WL_CONNECTED) etape = "WIFI";
  else if (!reseauPret()) etape = "IPV6";   // WiFi là, adresse IPv6 pas encore
  int nb = ((millis() - debutDemarrage) / 400) % 3 + 1;
  snprintf(texte, sizeof(texte), "%s%.*s", etape, nb, "...");
  return texte;
}

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
#if RESEAU_IPV6_SEUL
  WiFi.enableIPv6();   // demander une adresse IPv6 dès la connexion au WiFi
#endif
  io.connect();

  feedC[0]->onMessage(cbC0); feedC[1]->onMessage(cbC1); feedC[2]->onMessage(cbC2);
  feedF[0]->onMessage(cbF0); feedF[1]->onMessage(cbF1); feedF[2]->onMessage(cbF2);

  // Écran de démarrage animé (ecran_demarrage.h), avec en bas l'étape en cours.
  // a) L'ouverture se joue d'une traite : le WiFi se connecte pendant ce temps.
  debutDemarrage = millis();
  while (!dessinerIntro(u8g2, millis() - debutDemarrage, messageConnexion())) {
    delay(30);
  }
  // b) Connexion à Adafruit IO : chaque essai bloque un moment, alors on ne
  //    vérifie qu'avec l'œil ouvert, et on cligne de temps en temps.
  //    Au bout de ATTENTE_CONNEXION_MS (config.h), on démarre sans internet.
  unsigned long dernierClignement = millis();
  while (true) {
    enLigne = reseauPret() && io.status() >= AIO_CONNECTED;
    if (enLigne || millis() - debutDemarrage >= ATTENTE_CONNEXION_MS) break;
    if (millis() - dernierClignement >= PERIODE_CLIGNEMENT) {
      clignerOeil(u8g2, messageConnexion());
      dernierClignement = millis();
    } else {
      dessinerLogo(u8g2, messageConnexion());
      delay(100);
    }
  }

  // 4. Demander à Adafruit IO les dernières cibles et fourchettes enregistrées
  //    (sans internet : on garde celles de la mémoire)
  if (enLigne) demanderReglages();

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
  demarrerOTA();   // seulement si le WiFi est là ; sinon plus tard, dans loop()

  // 6. Le logo reste encore 2 secondes avec « CONNECTE ! » (ou « HORS LIGNE »)
  const char *bilan = enLigne ? "CONNECTE !" : "HORS LIGNE";
  dessinerLogo(u8g2, bilan);
  delay(1000);
  clignerOeil(u8g2, bilan);
  delay(500);

  // 7. Démarrage des sondes
  sensors.begin();
  for (int i = 0; i < 3; i++) sensors.setResolution(addr[i], RESOLUTION_SONDES);
  sensors.setWaitForConversion(false);   // ne pas attendre la fin des mesures
}


// =============================================================================
//  Outils
// =============================================================================

// Écrit un nombre sans décimale inutile : 19 → "19", 18.5 → "18.5"
void formatNombre(char *buf, size_t taille, float v) {
  if (fabsf(v - roundf(v)) < 0.05f) snprintf(buf, taille, "%d", (int)roundf(v));
  else snprintf(buf, taille, "%.1f", v);
}

// Demande à Adafruit IO les dernières cibles et fourchettes enregistrées
void demanderReglages() {
  for (int i = 0; i < 3; i++) {
    feedC[i]->get();
    feedF[i]->get();
    delay(200);
  }
}

// Lance les mises à jour par le WiFi (OTA), une seule fois, quand le WiFi est là
void demarrerOTA() {
  if (otaDemarre || WiFi.status() != WL_CONNECTED) return;
  ArduinoOTA.begin();
  otaDemarre = true;
}

// Garde la connexion en vie, ou la retente en arrière-plan sans bloquer l'écran.
// io.status() ne retente Adafruit IO qu'une fois par minute (règle de la
// bibliothèque). io.run() n'est appelé qu'une fois connecté : sinon il
// attendrait la connexion jusqu'à une minute, écran figé.
void gererConnexion(unsigned long now) {
  bool wifiOk = WiFi.status() == WL_CONNECTED;
  demarrerOTA();
  if (otaDemarre) ArduinoOTA.handle();   // une mise à jour par le WiFi est-elle demandée ?

  // WiFi absent : relancer la connexion une fois par minute
  if (!wifiOk && now - dernierEssaiWifi >= 60000) {
    dernierEssaiWifi = now;
    WiFi.reconnect();
  }

  bool etaitEnLigne = enLigne;
  enLigne = reseauPret() && io.status() >= AIO_CONNECTED;
  if (enLigne) io.run();                       // garde la connexion Adafruit IO en vie
  if (enLigne && !etaitEnLigne) demanderReglages();   // de retour : relire les réglages
}

// Lit le résultat de la mesure des 3 sondes, et note depuis quand une sonde est muette
void lireSondes(unsigned long now) {
  for (int i = 0; i < 3; i++) {
    temp[i] = sensors.getTempC(addr[i]);
    // 85 °C exactement = valeur d'une sonde mal alimentée, pas une vraie mesure
    if (temp[i] == 85.0f) temp[i] = DEVICE_DISCONNECTED_C;
    if (temp[i] == DEVICE_DISCONNECTED_C) {
      if (sondeMuetteDepuis[i] == 0) sondeMuetteDepuis[i] = now ? now : 1;
    } else {
      sondeMuetteDepuis[i] = 0;
    }
    verifierCuve(i);
  }
}

// Écrit l'état d'alerte en clair, en deux versions :
//  - « etat », court, pour savoir si quelque chose a changé : « CHAUD 1, SONDE 3 » ;
//  - « detail », envoyé dans le feed « alerte » (et donc dans l'email), avec la
//    température et la consigne : « CHAUD 1 : 21.3 C (19±1), SONDE 3 ».
//  Les deux valent « OK » quand tout va bien.
void texteAlerte(char *etat, size_t tailleEtat, char *detail, size_t tailleDetail, unsigned long now) {
  etat[0] = '\0';
  detail[0] = '\0';
  for (int i = 0; i < 3; i++) {
    const char *quoi = nullptr;
    if (enAlerte[i]) {
      quoi = temp[i] > cible[i] ? "CHAUD" : "FROID";
    } else if (sondeMuetteDepuis[i] != 0 && now - sondeMuetteDepuis[i] >= DELAI_SONDE_MUETTE_MS) {
      quoi = "SONDE";
    }
    if (quoi == nullptr) continue;

    size_t l = strlen(etat);
    snprintf(etat + l, tailleEtat - l, "%s%s %d", l > 0 ? ", " : "", quoi, i + 1);

    l = strlen(detail);
    if (enAlerte[i]) {
      char cBuf[8], fBuf[8];
      formatNombre(cBuf, sizeof(cBuf), cible[i]);
      formatNombre(fBuf, sizeof(fBuf), fourchette[i]);
      snprintf(detail + l, tailleDetail - l, "%s%s %d : %.1f C (%s±%s)",
               l > 0 ? ", " : "", quoi, i + 1, temp[i], cBuf, fBuf);
    } else {
      snprintf(detail + l, tailleDetail - l, "%s%s %d", l > 0 ? ", " : "", quoi, i + 1);
    }
  }
  if (etat[0] == '\0') {
    snprintf(etat, tailleEtat, "OK");
    snprintf(detail, tailleDetail, "OK");
  }
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
  unsigned long now = millis();

  // 1. Connexion (OTA, Adafruit IO), retentée en arrière-plan si besoin
  gererConnexion(now);

  // 2. Sondes, sans bloquer : on lance une mesure, puis on lit le résultat
  //    à un tour suivant, une fois la mesure finie (375 ms en 11 bits)
  if (!mesureEnCours) {
    sensors.requestTemperatures();
    debutMesure = now;
    mesureEnCours = true;
  } else if (now - debutMesure >= DUREE_MESURE_MS) {
    lireSondes(now);
    mesureEnCours = false;
  }

  bool alerteGlob = enAlerte[0] || enAlerte[1] || enAlerte[2];
  char alerte[48], alerteDetail[128];
  texteAlerte(alerte, sizeof(alerte), alerteDetail, sizeof(alerteDetail), now);

  if (enLigne) {
    // 3. Envoyer les températures à Adafruit IO (voir INTERVALLE_ENVOI_MS)
    if (now - lastCloud >= INTERVALLE_ENVOI_MS) {
      lastCloud = now;
      for (int i = 0; i < 3; i++) {
        if (temp[i] != DEVICE_DISCONNECTED_C) feedT[i]->save(temp[i]);
      }
    }

    // 4. Envoyer l'état d'alerte s'il a changé (au plus toutes les 30 s), avec
    //    les températures du moment. On ne compare que l'état (« CHAUD 1 ») :
    //    une température qui bouge ne renvoie pas d'email.
    //    Hors ligne, il partira au retour de la connexion.
    if (alerteEnvoyee != alerte && now - lastAlerte >= INTERVALLE_ALERTE_MS) {
      lastAlerte = now;
      alerteEnvoyee = alerte;
      feedAlerte->save(alerteDetail);
    }
  }

  // 5. Bandeau ALERTE : plein / contour, toutes les demi-secondes
  if (now - dernierClignotement >= 500) {
    dernierClignotement = now;
    altAlerte = alerteGlob ? !altAlerte : false;
  }

  // 6. Dessiner l'écran 4 fois par seconde : 3 cuves empilées, puis le bandeau en bas
  if (now - dernierAffichage < 250) { delay(10); return; }
  dernierAffichage = now;
  u8g2.firstPage();
  do {
    dessinerCuve(0, 12); u8g2.drawHLine(0, 33, 64);
    dessinerCuve(1, 46); u8g2.drawHLine(0, 67, 64);
    dessinerCuve(2, 80); u8g2.drawHLine(0, 101, 64);

    // Hors ligne, le bandeau est moins haut pour laisser la place à « HORS LIGNE »
    int hauteur = enLigne ? 24 : 16;
    int base = enLigne ? 122 : 117;
    if (alerteGlob) {
      // Bandeau ALERTE qui alterne plein / contour
      u8g2.setFont(u8g2_font_7x14B_tf);
      if (altAlerte) {
        u8g2.drawBox(0, 104, 64, hauteur);
        u8g2.setDrawColor(0);
        u8g2.setCursor(11, base); u8g2.print("ALERTE");
        u8g2.setDrawColor(1);
      } else {
        u8g2.drawFrame(0, 104, 64, hauteur);
        u8g2.setCursor(11, base); u8g2.print("ALERTE");
      }
    } else {
      u8g2.setFont(u8g2_font_6x12_tf);
      u8g2.setCursor(6, enLigne ? 120 : 115); u8g2.print("Temp OK !");
    }
    if (!enLigne) {
      u8g2.setFont(u8g2_font_4x6_tf);
      u8g2.drawStr((64 - u8g2.getStrWidth("HORS LIGNE")) / 2, 127, "HORS LIGNE");
    }
  } while (u8g2.nextPage());
}
