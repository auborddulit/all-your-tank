# Box 4G qui ne donne internet qu'en IPv6

Certains opérateurs mobiles (Bouygues Telecom, Orange…) ne donnent plus que des
adresses **IPv6**. Les box et routeurs 4G récents savent quand même joindre les sites
restés en IPv4 ; les plus anciens non (par exemple un TP-Link Archer MR200 v2 avec une
carte SIM Bouygues).

**Symptôme** : sur le WiFi de la box, un PC Linux a internet, mais pas un téléphone
Android ni le boîtier (bloqué sur « ADAFRUIT... » puis « HORS LIGNE »).

## Vérifier depuis un PC Linux connecté à ce WiFi

```
ip -6 route | head -3
getent ahosts io.adafruit.com | head -1
```

Le boîtier peut fonctionner si :

- la première commande affiche des lignes avec **`proto ra`** (le PC s'est fabriqué
  lui-même une adresse IPv6, ce que l'ESP32 sait faire aussi) ;
- la deuxième affiche une adresse qui commence par **`64:ff9b:`** (l'opérateur fait
  le pont entre l'IPv6 et les sites en IPv4).

## Ce que fait alors le boîtier

Avec `RESEAU_IPV6_SEUL true`, l'ESP32 :

1. demande une adresse IPv6 en plus de l'IPv4 du WiFi ;
2. attend de l'avoir avant de contacter Adafruit IO (l'écran affiche « IPV6... ») ;
3. utilise le DNS64 public de Google (`2001:4860:4860::6464`), puis celui du
   routeur en secours ; le DNS64 donne pour
   `io.adafruit.com` une adresse `64:ff9b:…` que l'opérateur relie à l'IPv4.

Les mises à jour par le WiFi (OTA) continuent de passer par l'IPv4 du réseau local.

⚠️ Ne l'activez pas sur un réseau classique : sans le pont de l'opérateur, les adresses
`64:ff9b:…` ne mènent nulle part et le boîtier resterait « HORS LIGNE ».

## Mise en place

### 1. Changer de paquet de cartes (une fois)

L'IPv6 demande le paquet **esp32** d'Espressif, version 3 ou plus. Le paquet
« Arduino ESP32 Boards » (version 2) ne le gère pas.

1. **Notez d'abord votre réglage actuel** de **Outils > Pin Numbering**.
2. **Outils > Carte > Gestionnaire de cartes** : cherchez **esp32** (par
   *Espressif Systems*) et installez la dernière version.
3. **Outils > Carte > esp32 > Arduino Nano ESP32**.
4. Remettez **Outils > Pin Numbering** comme noté à l'étape 1.
5. **Outils > Gérer les bibliothèques** : mettez à jour toutes les bibliothèques du
   projet (OneWire doit être en version 2.3.8 ou plus).

### 2. Activer l'option

Dans votre `secrets.h`, ajoutez (ou modifiez) la ligne :

```cpp
#define RESEAU_IPV6_SEUL true
```

Si vous oubliez l'étape 1, la compilation s'arrête avec un message qui l'explique.

### 3. Téléverser par câble USB (la première fois)

⚠️ **Débranchez d'abord le 230 V**, puis branchez l'USB. Ouvrez le **moniteur série**
pour voir ce qui se passe. Si le téléversement échoue, appuyez deux fois rapidement sur
le bouton du Nano ESP32 (la LED verte pulse) et réessayez.

Ensuite, les mises à jour par le WiFi (OTA) fonctionnent comme avant.

## Et le téléphone ?

Il n'aura toujours pas internet sur ce WiFi, mais il n'en a pas besoin : l'appli
Android passe par sa propre connexion 4G.
