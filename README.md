# Loadout

Kits for Paper and Folia 1.21: cooldowns, prices, conditions, mastery tiers, streaks, collection,
a featured kit, giftable kit vouchers, trying gear on before claiming, animated opening ceremonies
and a full in-game editor. Fourteen example kits ship with the plugin.

## Installation

1. Drop `Loadout.jar` into `plugins/`.
2. Start the server: `config.yml`, `kits.yml`, the `lang/` folder and the `loadout.db` database
   are created in `plugins/Loadout/`.

Optional: Vault (paid kits and money rewards), PlaceholderAPI (`%loadout_...%` placeholders and
conditions), Lootrift or any other crate plugin (keys as rewards).

## Languages

`config.yml` holds `language: en`. English and French ship with the plugin (`en`, `fr`).

- `lang/messages_<language>.yml` holds the chat messages.
- `lang/<language>.yml` holds the menu and log texts, and can be edited to reword any of them.
- On first start, `kits.yml` is written in the configured language.

## Commands

| Command | Effect |
|---|---|
| `/kit` | kit menu |
| `/kit <kit>` | claims a kit |
| `/kit preview <kit>` | contents preview |
| `/kit tryon <kit>` | tries the gear on before claiming it |
| `/kit gift <player> <kit>` | gifts a kit |
| `/kit all` | claims every available kit |
| `/kit collection`, `/kit mastery`, `/kit history` | progression and history |
| `/kit admin` | admin menu |
| `/kit create <id>`, `/kit edit <kit>`, `/kit capture <kit>` | create, edit, capture your inventory into a kit |
| `/kit delete <kit>`, `/kit stock <kit>` | delete, manage the stock |
| `/kit give <player> <kit>`, `/kit voucher <player> <kit>` | give a kit or a voucher |
| `/kit reset <player> <kit\|all>`, `/kit player <player>` | a player's cooldowns |
| `/kit ceremony ...` | test an opening ceremony |
| `/kit reload` | reloads `kits.yml` |

The French subcommands (`apercu`, `offrir`, `tout`, `creer`, ...) keep working.

## Permissions

| Permission | Default | Effect |
|---|---|---|
| `loadout.kit.use` | everyone | `/kit` and claiming kits |
| `loadout.kit.gift` | everyone | gifting a kit |
| `loadout.admin.kits` | op | administration |
| `loadout.kit.bypass` | no | bypasses cooldown, price, conditions and permissions |
| `loadout.kit.bypass.cooldown`, `.cost`, `.requirements`, `.permission` | no | each bypass on its own |
| `loadout.alerts.kits` | op | configuration alerts |

## Configuration

`kits.yml` holds the settings (`settings`), progression (`progression`: mastery, streaks,
collection, featured kit), the menu categories and the kits under `kits`. Everything can also be
edited from `/kit admin`.

Durations accept `30s`, `10m`, `2h`, `1d`. Resets accept `daily`, `weekly`, `monthly`, and days
accept `monday` to `sunday`.

### Crate keys as rewards

Kits can give keys. They are delivered through a console command, which works with any crate
plugin:

```yaml
crate-keys:
  command: "cle give {player} {crate} {amount}"
  crates:
    common: "<green>Common Crate"
```

The default command is Lootrift's. If `crates` is empty and Lootrift is installed, the crate list
is read straight from `plugins/Lootrift/crates.yml`.

### Differences from the NexusSMP version

Fragment costs and rewards are not available: a kit that costs fragments cannot be claimed. Team
kits behave like personal kits. When the inventory is full, extra items drop at the player's feet.

## Placeholders

With PlaceholderAPI: `%loadout_kit_featured%`, `%loadout_kit_featured_discount%`,
`%loadout_kits_total%`, `%loadout_kits_collection%`, `%loadout_kits_available%` and
`%loadout_kit_<kit>_<info>%` where `<info>` is `status`, `ready`, `cooldown`, `uses`, `tier`,
`level`, `streak`, `best` or `name`.

## Building

```bash
mvn package
```

The plugin is built to `target/Loadout.jar`. Java 21 is required.
