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
| `/kit share <kit>` | posts a clickable preview of the kit in chat, once every 30 seconds |
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

### Importing from other kit plugins

Keep the other plugin's folder in `plugins/`, then run `/kit import <source>`:

| Source | Reads |
|---|---|
| `essentials` | `plugins/Essentials/kits.yml` (or the `kits` section of its `config.yml`), item aliases from `items.json`, and each player's last claim from `userdata/` |
| `ultimatekits` | `plugins/UltimateKits/kit.yml` and each player's last claim from `data.yml` |

Items keep their amount, name, lore and enchantments; Essentials `@` serialized items and
UltimateKits NBT items are restored exactly. Armor goes to its armor slot, everything else fills the
inventory in order. Delays become cooldowns, a negative delay becomes a single use kit, `$` lines
become money rewards and `/` lines become console commands with `<player>`. Last claims are carried
over so running cooldowns keep going.

A kit whose id already exists in Loadout is skipped, so running the import twice does not duplicate
anything. Anything that could not be carried over is listed in the console after the import.

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

### Conditions on any placeholder

With PlaceholderAPI, a kit can require any value from any plugin:

```yaml
requirements:
  placeholders:
    - "%player_level% >= 10"
    - check: "%luckperms_primary_group_name% == vip"
      label: "VIP rank"
```

Operators are `==`, `!=`, `>`, `>=`, `<`, `<=` and `contains`. Numbers compare as numbers, anything
else compares as text without case. The label shows in the menu next to the other conditions.

### Points currency

Kits can cost and give points from any points plugin (PlayerPoints, TokenManager, ...). Loadout reads
the balance through a placeholder and moves points with console commands:

```yaml
points:
  name: "Tokens"
  balance: "%playerpoints_points%"
  take: "points take {player} {amount}"
  give: "points give {player} {amount}"
```

Use `cost.shards` and `rewards.shards` in `kits.yml` for the amounts. If the claim fails after the
points were taken, they are given back. Without a `balance` placeholder, kits that cost points stay
locked.

### Team kits

A kit with `options.team: true` shares one cooldown and one set of uses across a team. The team is
read from a placeholder, so it works with any team, clan or town plugin:

```yaml
teams:
  placeholder: "%betterteams_name%"
```

Players whose placeholder is empty, `none` or `-` have no team and cannot claim team kits.

When the inventory is full, extra items drop at the player's feet.

## Placeholders

With PlaceholderAPI: `%loadout_kit_featured%`, `%loadout_kit_featured_discount%`,
`%loadout_kits_total%`, `%loadout_kits_collection%`, `%loadout_kits_available%` and
`%loadout_kit_<kit>_<info>%` where `<info>` is `status`, `ready`, `cooldown`, `uses`, `tier`,
`level`, `streak`, `best` or `name`.

## Developer API

Add Loadout as a `depend` or `softdepend`, then get the service:

```java
LoadoutApi.get().ifPresent(loadout -> {
    if (loadout.available(player, "daily")) {
        loadout.claim(player, "daily");
    }
    loadout.giveVouchers(player, "vip", 1);
});
```

`LoadoutApi` covers kit ids, availability, cooldowns, use counts, claiming with every rule applied,
giving a kit while skipping the rules, vouchers, resets and opening the menu or a preview. Unknown
kits and non-positive amounts throw `IllegalArgumentException`.

Events:

| Event | When |
|---|---|
| `KitClaimEvent` | before anything is paid or given, cancellable, with the kit, the source and the price |
| `KitClaimedEvent` | after the kit was delivered, with the items given and the new mastery level |

The source is one of `menu`, `command`, `voucher`, `gift`, `first-join`, `respawn`, `bulk` or
`admin`.

## Reloading, updates and metrics

`/kit reload` rereads `config.yml`, the language files and `kits.yml`. Changing `language` takes
full effect after a restart.

On start Loadout checks the latest GitHub release and tells the console and players with
`loadout.admin.kits` when a newer version exists. Set `update-checker: false` in `config.yml` to
turn it off. Anonymous usage statistics go through bStats and follow the global bStats opt-out in
`plugins/bStats/config.yml`.

## Building

```bash
mvn package
```

The plugin is built to `target/Loadout.jar`. Java 21 is required.
