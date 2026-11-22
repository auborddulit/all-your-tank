# Améliorations envisagées

Le projet fonctionne. Voici ce qui pourrait le rendre plus robuste ou plus pratique,
du plus utile au plus accessoire. Les contributions sont bienvenues.

## Robustesse

### 1. Continuer à afficher les températures sans internet

Aujourd'hui, au démarrage, le programme attend la connexion à Adafruit IO dans une
boucle sans fin. Si la box 4G est en panne au moment d'une coupure de courant,
**l'écran reste sur « Connexion… » et n'affiche aucune température**, alors que les
sondes fonctionnent.

Idée : limiter l'attente (par exemple 30 secondes), puis démarrer l'affichage local et
laisser `io.run()` retenter la connexion en arrière-plan.

### 2. Comprendre et, si possible, retirer le « réveil des broches »

Au démarrage, le programme crée un bus OneWire sur les numéros 2, 3, 4 et 5
(`BROCHES_REVEIL`) pour « réveiller » le matériel. Cette astuce a été trouvée pendant
la mise au point et fonctionne, mais sans explication certaine. Elle agit sur
plusieurs bornes (D2 et, selon le réglage de numérotation, A1 à A3 ou D3 à D5) :
mieux vaut ne pas les utiliser pour autre chose tant qu'elle est là.

**Piste d'explication, à vérifier** : avec le réglage par défaut de l'Arduino IDE
(**Outils > Pin Numbering > By Arduino pin**), les fonctions comme `pinMode()` sont
renumérotées (5 veut dire D5), mais la bibliothèque OneWire accède aussi directement
à la puce avec le numéro brut (5 veut dire GPIO 5, donc D2). `OneWire(5)` préparerait
alors la mauvaise borne, et c'est `OneWire(2)` de l'astuce qui préparerait la borne D2.
Si c'est bien ça, passer le réglage sur **By GPIO number (legacy)** rendrait l'astuce
inutile. À tester, après plusieurs coupures de courant, avant de la supprimer.

### 3. Lire les sondes sans bloquer

`sensors.requestTemperatures()` attend la fin de la mesure (environ 375 ms en 11 bits)
à chaque tour de boucle. Pendant ce temps, rien d'autre ne se passe. Idée : utiliser
`sensors.setWaitForConversion(false)` et lire le résultat au tour suivant.

## Alertes

### 4. Prévenir aussi quand une sonde ne répond plus

Une sonde débranchée affiche « ERR. SONDE » à l'écran, mais ne déclenche pas d'email.
Idée : ajouter un code spécial dans le feed `alerte` (par exemple 9) quand une sonde
est en erreur depuis plus de quelques minutes.

### 5. Renommer les cuves depuis le tableau de bord

Les noms sont fixés dans `config.h`, car les 10 feeds du compte gratuit servent aux
températures, cibles, fourchettes et alertes. Avec Adafruit IO+ (feeds illimités),
on pourrait remettre un feed de nom par cuve.

## Confort

### 6. Nombre de cuves réglable

Passer de 3 cuves fixes à un nombre réglable dans `config.h` (tableaux, feeds et mise
en page calculés à partir de ce nombre).

### 7. Logo au démarrage

Les écrans sont prêts dans `ecrans_demarrage.h` : il reste à choisir lequel afficher et
à l'activer par une option de `config.h`. Voir [personnaliser.md](personnaliser.md#afficher-un-logo-au-démarrage).

### 8. Fichier du support imprimé en 3D

Publier le fichier du support de cartes visible sur les photos.

## Matériel

### 9. Alimentation dans la plage prévue

Voir le [point de vigilance](materiel.md#point-de-vigilance--lalimentation-5-v) :
une alimentation 12 V sur VIN serait plus conforme à la notice du Nano ESP32.

## Déjà fait

- **Cible ± fourchette** par cuve, réglables avec des curseurs, au lieu d'un simple seuil.
- **Alerte trop chaud et trop froid**, avec une marge anti-clignotement (`HYSTERESIS`).
- **Un seul réglage pour l'écran et les emails** : l'ESP32 envoie lui-même le feed `alerte`.
- **Valeur 85 °C ignorée** : elle s'affiche comme une erreur de sonde.

## Pour plus tard

- **Niveau des cuves** : un détecteur de seuil par cuve sur le tube de niveau.
- **Détection de fuite** au sol.
- **Pilotage du groupe froid et de la pompe** : uniquement avec des contacteurs
  câblés par un électricien.
- **Régulation automatique** : passer à
  [BrewPi-ESP](https://github.com/thorrak/brewpi-esp8266) ou à
  [Home Assistant](https://www.home-assistant.io/) avec ESPHome, qui savent piloter
  le chaud et le froid. Les sondes et l'ESP32 restent réutilisables.
