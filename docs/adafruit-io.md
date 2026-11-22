# Tableau de bord Adafruit IO

[Adafruit IO](https://io.adafruit.com) est un service en ligne qui reçoit les
températures, les affiche en graphiques sur téléphone ou ordinateur, et peut envoyer
des emails d'alerte. Le compte gratuit suffit pour ce projet.

## 1. Créer le compte et noter la clé

1. Créez un compte sur <https://io.adafruit.com>.
2. Cliquez sur l'**icône de clé jaune** : vous y trouvez votre **nom d'utilisateur**
   et votre **clé** (elle commence par `aio_`). Recopiez-les dans `secrets.h`.

> 🔒 La clé donne le contrôle de votre compte. Ne la publiez jamais, ne la collez pas
> dans un message ou un forum. Si elle a fuité, cliquez sur **Regenerate Key** : l'ancienne
> ne marche plus, il suffit de mettre la nouvelle dans `secrets.h` et de téléverser.
>
> La clé ne sert pas à se connecter au site : pour cela, il faut l'email et le mot de
> passe du compte.

## 2. Les 10 feeds

Un *feed* est un flux de données. Le projet en utilise **10**, le maximum du compte
gratuit. Leurs noms doivent être écrits **exactement** comme ici :

| Feed | Sens | Contenu |
|---|---|---|
| `cuve1`, `cuve2`, `cuve3` | ESP32 → Adafruit IO | Température mesurée (°C) |
| `cible-cuve1`, `cible-cuve2`, `cible-cuve3` | Adafruit IO → ESP32 | Température cible (°C) |
| `fourchette-cuve1`, `fourchette-cuve2`, `fourchette-cuve3` | Adafruit IO → ESP32 | Écart toléré de part et d'autre de la cible (°C) |
| `alerte` | ESP32 → Adafruit IO | Cuves hors fourchette : `0` = tout va bien, `2` = cuve 2, `13` = cuves 1 et 3… |

- `cuve1` à `cuve3` et `alerte` se créent tout seuls à la première valeur envoyée
  par le boîtier.
- Les feeds `cible-…` et `fourchette-…` se créent en même temps que les curseurs du
  tableau de bord (étape suivante).
- Si vous aviez des feeds d'une ancienne version (`nom-cuveX`, `seuil-cuveX`),
  supprimez-les pour rester sous la limite de 10.

Vous préférez d'autres noms ? Changez-les aussi dans `config.h` (section 3).

## 3. Construire le tableau de bord

1. **Dashboards > + New Dashboard**, donnez un nom (par exemple `Brasserie`), puis
   ouvrez-le.
2. Pour chaque bloc : **engrenage** en haut à droite > **Create New Block**.

| Bloc | Relié à | Réglages conseillés |
|---|---|---|
| **Line Chart** (graphique) | `cuve1`, `cuve2`, `cuve3` | Show History : 24 hours |
| **Slider** (curseur), un par cuve | `cible-cuve1`… | Min 0, Max 30, Step 0.5 |
| **Slider** (curseur), un par cuve | `fourchette-cuve1`… | Min 0.5, Max 5, Step 0.5 |
| **Gauge** (jauge), facultatif | un feed `cuveX` | Min 0, Max 30 |

Pour relier un curseur à un feed qui n'existe pas encore, tapez son nom dans la case
de création de feed du bloc, puis cliquez sur **Create**.

Quand vous bougez un curseur, l'ESP32 reçoit la valeur en quelques secondes,
l'affiche (par exemple `18.5±1` à côté du nom de la cuve) et la garde en mémoire,
même après une coupure de courant.

Pour ranger les blocs : engrenage > **Edit Layout**, puis **Save Layout**.

## 4. Recevoir les alertes par email

L'ESP32 compare lui-même chaque température à sa cible et à sa fourchette. Quand une
cuve sort de sa fourchette (trop chaude **ou** trop froide), il envoie le numéro des
cuves concernées dans le feed `alerte`. Il suffit donc d'une seule règle :

1. Menu **Actions** > **New Action**, type **Reactive** (réaction à une valeur).
2. Règle : *si* le feed **`alerte`** est **supérieur à** `0`, *alors* **envoyer un email**.
3. Si l'option est proposée, activez aussi la notification quand la valeur revient
   à `0`, pour être prévenu que tout est rentré dans l'ordre.

La valeur reçue dans l'email indique les cuves concernées : `1`, `2`, `3`, ou `13`
pour les cuves 1 et 3.

Pour éviter une rafale d'emails, l'ESP32 n'envoie le feed `alerte` que quand l'état
change, au plus une fois toutes les 30 secondes, et une cuve ne sort d'alerte qu'en
revenant de 0,2 °C à l'intérieur de sa fourchette (`HYSTERESIS` dans `config.h`).

## Les limites du compte gratuit

- **10 feeds** : le projet les utilise tous.
- **30 valeurs par minute** au maximum. Le projet en envoie 9 par minute (3 cuves
  toutes les 20 secondes), plus quelques valeurs `alerte` : il reste de la marge.
  Au-delà, Adafruit IO refuse les envois et peut bloquer temporairement le compte.
- L'historique est conservé **30 jours**. Pour garder un historique plus long,
  exportez régulièrement les données (bouton **Download** sur chaque feed).
