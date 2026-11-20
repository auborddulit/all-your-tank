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

## 2. Créer les 9 feeds

Un *feed* est un flux de données. Allez dans **Feeds > New Feed** et créez-en 9, avec
**exactement** ces noms :

| Feed | Sens | Contenu |
|---|---|---|
| `cuve1`, `cuve2`, `cuve3` | ESP32 → Adafruit IO | Température mesurée (°C) |
| `nom-cuve1`, `nom-cuve2`, `nom-cuve3` | Adafruit IO → ESP32 | Nom affiché à l'écran (6 caractères max) |
| `seuil-cuve1`, `seuil-cuve2`, `seuil-cuve3` | Adafruit IO → ESP32 | Seuil d'alerte (°C) |

Vous préférez d'autres noms ? Changez-les aussi dans `config.h` (section 3).

## 3. Construire le tableau de bord

Dans **Dashboards > New Dashboard**, ajoutez des blocs (bouton **+**) :

| Bloc | Relié à | Usage |
|---|---|---|
| **Line Chart** (graphique) | `cuve1`, `cuve2`, `cuve3` | Courbes de température |
| **Gauge** (jauge), facultatif | un feed `cuveX` | Température actuelle d'une cuve |
| **Text** (zone de texte) | `nom-cuve1`… | Renommer une cuve, par exemple « IPA » |
| **Slider** ou **Number Pad** | `seuil-cuve1`… | Régler le seuil d'alerte |

Quand vous changez un nom ou un seuil sur le tableau de bord, l'ESP32 le reçoit en
quelques secondes, l'affiche et le garde en mémoire, même après une coupure de courant.

## 4. Recevoir les alertes par email

L'alerte à l'écran est gérée par l'ESP32. Pour les **emails**, il faut créer une
règle dans Adafruit IO :

1. Menu **Actions > New Action**, type **Reactive**.
2. Règle : *si* le feed `cuve1` est **supérieur à** votre seuil, *alors* **envoyer un email**.
3. Faites de même pour `cuve2` et `cuve3`.

Le nombre d'actions disponibles dépend de votre formule Adafruit IO : vérifiez sur
votre compte.

> ⚠️ Le seuil de l'action email et le seuil `seuil-cuveX` affiché à l'écran sont **deux
> réglages séparés**. Si vous changez l'un, pensez à changer l'autre.

## Les limites du compte gratuit

- **30 valeurs par minute** au maximum. Le projet en envoie 9 par minute (3 cuves
  toutes les 20 secondes) : il reste de la marge. Au-delà, Adafruit IO refuse les
  envois et peut bloquer temporairement le compte.
- L'historique des données est conservé pendant une durée limitée. Pour garder un
  historique long, exportez régulièrement les données (bouton **Download** sur
  chaque feed).
