# Liste du matériel

Les références entre parenthèses sont celles du revendeur Reichelt, où la plupart des
pièces ont été achetées. Tout se trouve aussi ailleurs : ce qui compte, c'est la
description.

## Électronique

| Pièce | Quantité | Détail | Réf. Reichelt |
|---|:---:|---|---|
| Arduino Nano ESP32 (avec connecteurs soudés) | 1 | Le cerveau : WiFi intégré | 353087 |
| Adaptateur à bornier à vis pour Nano | 1 | Pour tout brancher sans soudure | 339155 |
| Sonde DS18B20 inox étanche, embout 6 mm | 3 | Une par cuve, fils rouge / jaune / noir | — |
| Résistance 4,7 kΩ, 1/4 W | 1 | **Une seule** pour tout le bus des sondes | 1425 |
| Module écran OLED 2,42" 128×64, contrôleur SSD1309, I2C | 1 | Version **module** sur circuit, 4 broches : GND, VCC, SCL, SDA | — |
| Câbles Dupont femelle / fil nu | 4 | Pour relier l'écran au bornier | — |

## Alimentation et boîtier

| Pièce | Quantité | Détail | Réf. Reichelt |
|---|:---:|---|---|
| Alimentation MEAN WELL APV-8-5 | 1 | 5 V, 1,4 A, fils nus, à installer **dans** le boîtier | 170873 |
| Câble secteur fiche Euro, extrémités ouvertes, 1,5 m | 1 | Arrivée 230 V | 332289 |
| Boîtier industriel 180×120×90 mm, IP66/IP68, couvercle transparent | 1 | On voit l'écran sans ouvrir | 340567 |
| Presse-étoupe PG7 (câbles de 3 à 6,5 mm) | 3 | Un par sonde | — |
| Presse-étoupe PG9 (câbles de 4 à 8 mm) | 1 | Câble secteur | — |
| Écarteurs autocollants 4,8 mm | 4 + | Pour fixer les cartes | 53729 |
| Support de cartes | 1 | Imprimé en 3D sur la photo du montage (fichier non fourni pour l'instant) | — |
| Sachet de gel de silice | 1 | Contre la condensation dans le boîtier | — |

## Selon votre installation

| Pièce | Quand en a-t-on besoin ? |
|---|---|
| Câble de rallonge LIYY 3 × 0,14 mm² | Si les câbles des sondes sont trop courts |
| Gaine thermorétractable à colle, ou boîtier de jonction à résine | Pour rendre étanches les raccords des rallonges |
| Routeur 4G (par exemple TP-Link TL-MR110) + carte SIM | S'il n'y a pas de WiFi dans la brasserie |

## À propos de l'écran

Prenez un **module** OLED 2,42" (écran déjà soudé sur un petit circuit avec 4 broches),
alimenté en 3,3 V. Un premier essai avec un écran industriel « nu » (EA OLEDL128-6),
qui demande une alimentation 12 V séparée et un câblage de configuration délicat,
s'est mal terminé : l'écran a grillé pendant les essais. Le module générique coûte
moins cher et se branche avec 4 fils.

## Point de vigilance : l'alimentation 5 V

La notice d'Arduino indique que la borne **VIN** du Nano ESP32 accepte **de 6 à 21 V**.
L'alimentation APV-8-5 fournit 5 V :

- **branchée sur VIN**, elle est en dessous de la plage prévue. Ça peut fonctionner,
  mais la carte risque de redémarrer lors des pics de consommation du WiFi ;
- **branchée sur la borne 5 V (VBUS)**, c'est une pratique courante, mais elle n'est
  pas décrite par la notice officielle. Dans ce cas, ne branchez jamais le câble USB
  en même temps, pour ne pas renvoyer du courant vers l'ordinateur.

**Solution la plus propre** : une alimentation **12 V** de la même famille
(MEAN WELL APV-8-12, même taille) branchée sur **VIN**.

## Sécurité

- Le raccordement au **230 V** doit être fait par une personne qualifiée.
- L'alimentation doit être **à l'intérieur** du boîtier fermé, jamais accessible.
- Les éventuelles extensions futures (pilotage d'un groupe froid, d'une pompe) se
  font avec des contacteurs, câblés par un électricien.
