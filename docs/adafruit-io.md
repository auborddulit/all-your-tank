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
| `alerte` | ESP32 → Adafruit IO | État en clair : `OK`, `CHAUD 1 : 21.3 C (19±1)`, `SONDE 3`…, plusieurs séparés par des virgules |

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

L'ESP32 compare lui-même chaque température à sa cible et à sa fourchette, et
surveille les sondes. Il écrit l'état en clair dans le feed `alerte` :

| Valeur | Signification |
|---|---|
| `OK` | tout va bien |
| `CHAUD 1 : 21.3 C (19±1)` | cuve 1 au-dessus de sa fourchette : sa température et sa consigne |
| `FROID 2 : 10.6 C (12±1)` | cuve 2 en dessous de sa fourchette |
| `SONDE 3` | la sonde de la cuve 3 ne répond plus depuis 2 minutes (`DELAI_SONDE_MUETTE_MS`) |
| `CHAUD 1 : 21.3 C (19±1), SONDE 3` | plusieurs problèmes en même temps |

La température est celle du moment où l'alerte part. Un nouvel email ne part que
quand l'état change (une cuve de plus ou de moins en alerte), pas à chaque variation
de température.

Il suffit donc d'une seule règle : *si* `alerte` est **différent de** `OK`, *alors*
**envoyer un email**.

1. Menu **Actions** > **New Action** (éditeur à blocs).
2. Dans le bloc **Reactive**, choisissez le feed **`alerte`**.
3. Pour la condition, prenez dans la catégorie **Triggers** le bloc **Compare Text Matcher**
   (comparaison de texte), réglez-le sur **≠** et tapez `OK` (en majuscules).
4. En action, un bloc **Email**, avec dans le texte la valeur du feed (`{{value}}`),
   par exemple : « Brasserie : {{value}} ».
5. Enregistrez.

> **Vous aviez la règle « supérieur à 0 » d'une version précédente ?** Elle ne marche
> plus avec du texte : remplacez la condition comme ci-dessus.

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
