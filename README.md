# Loadout

Kits pour Paper et Folia 1.21 : recharges, prix, conditions, niveaux de maîtrise, séries,
collection, kit à la une, bons de kit à offrir, essayage avant de récupérer, cérémonies
d'ouverture animées et éditeur complet en jeu. Quatorze kits d'exemple sont fournis.

## Installation

1. Placez `Loadout.jar` dans `plugins/`.
2. Démarrez le serveur : `kits.yml`, `messages.yml` et la base `loadout.db` sont créés dans
   `plugins/Loadout/`.

Optionnel : Vault (kits payants et récompenses en argent), PlaceholderAPI (placeholders
`%loadout_...%` et conditions), Lootrift ou tout autre plugin de caisses (clés en récompense).

## Commandes

| Commande | Effet |
|---|---|
| `/kit` | menu des kits |
| `/kit <kit>` | récupère un kit |
| `/kit apercu <kit>` | aperçu du contenu |
| `/kit essayer <kit>` | essayage de l'équipement avant de le récupérer |
| `/kit offrir <joueur> <kit>` | offre un kit |
| `/kit tout` | récupère tous les kits disponibles |
| `/kit collection`, `/kit maitrise`, `/kit historique` | progression et historique |
| `/kit admin` | menu d'administration |
| `/kit creer <id>`, `/kit editer <kit>`, `/kit capturer <kit>` | créer, éditer, capturer son inventaire dans un kit |
| `/kit supprimer <kit>`, `/kit stock <kit>` | supprimer, gérer le stock |
| `/kit donner <joueur> <kit>`, `/kit bon <joueur> <kit>` | donner un kit ou un bon |
| `/kit reset <joueur> <kit\|tout>`, `/kit joueur <joueur>` | recharges d'un joueur |
| `/kit ceremonie ...` | tester une cérémonie d'ouverture |
| `/kit reload` | recharge `kits.yml` |

## Permissions

| Permission | Par défaut | Effet |
|---|---|---|
| `loadout.kit.use` | tous | `/kit` et récupérer les kits |
| `loadout.kit.gift` | tous | offrir un kit |
| `loadout.admin.kits` | op | administration |
| `loadout.kit.bypass` | non | ignore recharge, prix, conditions et permissions |
| `loadout.kit.bypass.cooldown`, `.cost`, `.requirements`, `.permission` | non | chaque contournement séparément |
| `loadout.alerts.kits` | op | alertes de configuration |

## Configuration

`kits.yml` contient les réglages (`settings`), la progression (`progression` : maîtrise, séries,
collection, kit à la une), les catégories du menu et les kits sous `kits`. Tout se modifie aussi
depuis `/kit admin`.

### Clés de caisse en récompense

Les kits peuvent offrir des clés. Elles sont remises par une commande console, ce qui fonctionne
avec n'importe quel plugin de caisses :

```yaml
crate-keys:
  command: "cle give {player} {crate} {amount}"
  crates:
    commune: "<green>Caisse Commune"
```

La commande par défaut est celle de Lootrift. Si `crates` est vide et que Lootrift est installé, la
liste des caisses est lue directement dans `plugins/Lootrift/crates.yml`.

### Différences avec la version NexusSMP

Les coûts et récompenses en fragments ne sont pas disponibles : un kit qui coûte des fragments ne
peut pas être récupéré. Les kits d'équipe se comportent comme des kits personnels. Quand
l'inventaire est plein, les objets en trop tombent aux pieds du joueur.

## Placeholders

Avec PlaceholderAPI : `%loadout_kit_featured%`, `%loadout_kit_featured_discount%`,
`%loadout_kits_total%`, `%loadout_kits_collection%`, `%loadout_kits_available%` et
`%loadout_kit_<kit>_<info>%` où `<info>` vaut `status`, `ready`, `cooldown`, `uses`, `tier`, `level`, `streak`, `best` ou `name`.

## Compilation

```bash
mvn package
```

Le plugin se trouve dans `target/Loadout.jar`. Java 21 est requis.
