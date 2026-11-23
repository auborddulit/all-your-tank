# Dépannage

Pour voir ce qui se passe, gardez l'ESP32 branché en USB et ouvrez le **moniteur
série** de l'Arduino IDE (115200 bauds).

## Les numéros de broches du Nano ESP32

C'est **le piège n° 1** de cette carte. Chaque borne a deux numéros :

- l'**étiquette** imprimée sur la carte et le bornier (D2, A4…) ;
- le **numéro de GPIO** de la puce ESP32-S3, qui est différent.

Certaines bibliothèques, dont **OneWire** (les sondes), utilisent le numéro de GPIO.
C'est pour cela que `config.h` indique **5** pour la borne **D2**.

| Étiquette | GPIO | Utilisée par le projet pour |
|---|:---:|---|
| D2 | 5 | Données des sondes |
| A1 | 2 | Rien |
| A2 | 3 | Rien |
| A3 | 4 | Rien |
| A4 | 11 | SDA de l'écran |
| A5 | 12 | SCL de l'écran |

Le « réveil des broches » du démarrage agit sur plusieurs de ces bornes : avant de
brancher autre chose sur D3 à D5 ou A1 à A3, lisez
[ameliorations.md](ameliorations.md#1-comprendre-et-si-possible-retirer-le--réveil-des-broches-).

Le brochage complet est sur la
[fiche officielle d'Arduino](https://docs.arduino.cc/resources/pinouts/ABX00083-full-pinout.pdf).
L'Arduino IDE propose aussi deux réglages dans **Outils > Pin Numbering** : si vous en
changez et que les sondes ou l'écran ne répondent plus, revenez au réglage précédent.

## L'écran affiche « ERR. SONDE »

La sonde de cette cuve ne répond pas.

1. Vérifiez les 3 fils : rouge sur 3.3V, noir sur GND, jaune sur D2.
2. Vérifiez la **résistance de 4,7 kΩ** entre D2 et 3.3V (sans elle, aucune sonde ne marche).
3. Lancez l'outil `outils/scan-sondes` : la sonde apparaît-elle ? Son adresse
   correspond-elle à celle de `config.h` ?
4. Si **toutes** les sondes sont en erreur, le problème vient du bus commun
   (résistance, fil jaune, borne D2) ou du numéro de broche.

## Une sonde reste sur « ERR. SONDE » ou affiche 85 °C

85 °C est la valeur qu'une DS18B20 donne avant sa première vraie mesure. Le
programme l'ignore et affiche **ERR. SONDE** à la place, pour éviter une fausse
alerte. Si ça dure, c'est en général un problème d'alimentation ou de contact sur la
sonde : vérifiez les fils rouge et noir.

## Les températures sont instables ou disparaissent par moments

- Les rallonges sont-elles bien étanches ? L'humidité dans un raccord perturbe le bus.
- Avec de grandes longueurs de câble, le signal s'affaiblit. Essayer une résistance
  plus faible (2,2 kΩ au lieu de 4,7 kΩ) aide souvent.

## L'écran reste noir

1. Vérifiez les 4 fils : GND, VCC sur **3.3V**, SCL sur **A5**, SDA sur **A4**
   (SDA et SCL inversés sont une erreur fréquente).
2. Débranchez l'alimentation 10 secondes, puis rebranchez.
3. Vérifiez que `ECRAN_TYPE` dans `config.h` correspond à votre écran.
4. Un **module** OLED à 4 broches est bien plus simple qu'un écran « nu »
   (voir [materiel.md](materiel.md#à-propos-de-lécran)).

## L'écran affiche « HORS LIGNE »

Le boîtier n'a pas pu se connecter à Adafruit IO. Il affiche quand même les
températures et les alertes (avec les cibles et fourchettes gardées en mémoire), mais
**n'envoie rien** : ni températures, ni emails. Il réessaie tout seul, en arrière-plan,
et « HORS LIGNE » disparaît dès que la connexion revient.

Au démarrage, le boîtier attend internet 30 secondes au plus (`ATTENTE_CONNEXION_MS`
dans `config.h`). Le message en bas de l'écran de démarrage dit où ça bloque :
**« WIFI... »** = le boîtier ne trouve pas le WiFi (nom, mot de passe, 2,4 GHz) ;
**« ADAFRUIT... »** = le WiFi marche, mais Adafruit IO refuse ou ne répond pas (nom
d'utilisateur, clé).

- Le nom et le mot de passe WiFi dans `secrets.h` sont-ils exacts (majuscules,
  caractères spéciaux) ? Un guillemet `"` dans un mot de passe s'écrit `\"`.
- L'ESP32 ne capte que le **WiFi 2,4 GHz**.
- La box 4G a-t-elle du réseau ? Testez avec un téléphone sur le même WiFi.
- Le nom d'utilisateur et la clé Adafruit IO sont-ils exacts ? Si vous avez régénéré
  la clé, avez-vous téléversé la nouvelle ?

## Le téléversement échoue

- Appuyez **deux fois rapidement** sur le bouton RST du Nano : la LED pulse, la carte
  attend un programme. Choisissez à nouveau le port et relancez.
- Essayez un autre câble USB-C : certains câbles ne transportent que le courant.

## Le port réseau (OTA) n'apparaît pas dans l'Arduino IDE

- L'ordinateur et l'ESP32 doivent être sur le **même réseau WiFi**.
- Attendez une minute après le démarrage de l'ESP32, puis rouvrez **Outils > Port**.
- Un pare-feu peut bloquer la détection : autorisez l'Arduino IDE.

## Adafruit IO ne reçoit plus rien

- Si vous avez réduit `INTERVALLE_ENVOI_MS`, vous avez peut-être dépassé la limite
  gratuite de **30 valeurs par minute** : Adafruit IO bloque alors les envois.
- Sur io.adafruit.com, vérifiez que les feeds `cuve1`, `cuve2` et `cuve3` existent
  avec exactement ces noms.
