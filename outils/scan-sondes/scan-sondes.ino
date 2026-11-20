// =============================================================================
//  scan-sondes : trouve l'adresse de chaque sonde DS18B20
// =============================================================================
//  À utiliser UNE fois, avant le programme principal :
//    1. téléversez ce croquis sur l'Arduino Nano ESP32 ;
//    2. ouvrez le moniteur série (115200 bauds) ;
//    3. recopiez les lignes « #define ADRESSE_SONDE_... » dans config.h.
//
//  Pour savoir quelle sonde est laquelle : chauffez-en une dans la main
//  (ou dans un verre d'eau chaude) et regardez quelle température monte.
//
//  Licence : MIT (voir le fichier LICENSE à la racine du dépôt)
// =============================================================================

#include <OneWire.h>
#include <DallasTemperature.h>

// Même broche que dans config.h : borne D2 du Nano ESP32 = GPIO 5
#define ONE_WIRE_BUS 5

OneWire oneWire(ONE_WIRE_BUS);
DallasTemperature sensors(&oneWire);

void setup() {
  Serial.begin(115200);
  delay(2000);

  // Même « réveil » des broches que dans le programme principal
  byte pins[] = {2, 3, 4, 5};
  for (byte p : pins) { OneWire b(p); byte a[8]; b.search(a); }
  delay(500);

  sensors.begin();
}

void loop() {
  int nombre = sensors.getDeviceCount();

  Serial.println();
  Serial.printf("--- %d sonde(s) trouvee(s) sur la borne D2 ---\n", nombre);

  if (nombre == 0) {
    Serial.println("Aucune sonde. Verifiez :");
    Serial.println(" - fil jaune (donnees) sur D2, rouge sur 3.3V, noir sur GND ;");
    Serial.println(" - resistance de 4,7 kOhm entre D2 et 3.3V.");
  }

  sensors.requestTemperatures();

  for (int n = 0; n < nombre; n++) {
    DeviceAddress adr;
    if (!sensors.getAddress(adr, n)) continue;

    // Ligne prête à copier dans config.h
    Serial.printf("#define ADRESSE_SONDE_%d         { ", n + 1);
    for (int i = 0; i < 8; i++) {
      Serial.printf("0x%02X%s", adr[i], (i < 7) ? ", " : " }");
    }
    Serial.printf("   // %.2f C\n", sensors.getTempC(adr));
  }

  delay(5000);   // nouvelle recherche toutes les 5 secondes
}
