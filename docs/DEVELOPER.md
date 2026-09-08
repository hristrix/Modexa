# Modexa Platform 3.0 — Paper 26.2

Modexa is a modular Paper platform. Its dialog/YAML system is one Core capability, while independent modules can add complete features such as Homes, Warps, Kits, Claims, Friends, shops, administration tools, or their own dynamic UI systems.

## Projects

- `core/` — the `Modexa` Paper platform. It provides the public API, module registry, native dialog/UI engine, YAML parser, actions, validation, conditions, placeholders and ESC/game-menu integration.
- `api/` — developer compile API. It compiles the exact API package bundled by Core. Do **not** shade this into modules.
- `modules/homes/` — working example module implementing full Homes CRUD through the Core API while preserving the legacy `homes.yml` structure.

## Architecture

```text
Paper 26.2
   |
   +-- Modexa Core JAR
   |      +-- YAML Dialog Engine
   |      +-- Native Paper Dialog Renderer
   |      +-- Action Registry
   |      +-- Placeholder Registry
   |      +-- Condition Registry
   |      +-- Dynamic Dialog Providers
   |      +-- Module Registry / Reload Hooks
   |      +-- Game Menu integration
   |
   +-- ModexaHomes.jar
   |      +-- depends on Core API
   |      +-- homes.yml storage
   |      +-- homes:* actions
   |      +-- {homes:*} placeholders
   |      +-- homes:* dialogs
   |
   +-- YourFutureModule.jar
          +-- depends on Core API
          +-- its own data/business logic
          +-- its own YAML and/or generated dialogs
```

## Building

Requires **Java 25** and Gradle.

```bash
gradle :core:build :modules:homes:build
```

Outputs:

```text
core/build/libs/Modexa-3.0.0.jar
modules/homes/build/libs/ModexaHomes-3.0.0.jar
api/build/libs/ModexaAPI-3.0.0.jar   # developer compile artifact
```

Install both JARs into `plugins/`.

## Upgrading from DynamicDialogs

The Core plugin is now named `Modexa`, so Paper uses `plugins/Modexa/` as its data folder. On first bootstrap, Modexa safely copies missing configuration/dialog files from `plugins/DynamicDialogs/` when `migration.import-dynamicdialogs` is enabled. The old folder is left untouched.

The Homes module is now `ModexaHomes`. If `plugins/ModexaHomes/homes.yml` does not exist, it checks the previous `plugins/DynamicDialogsHomes/homes.yml` first and then `plugins/DialogHomes/homes.yml`, copying rather than moving the file.

The Homes module declares a required Paper server dependency on `Modexa` with `join-classpath: true`, so it uses the API classes supplied by Core at runtime.

## API lookup

```java
ModexaApi api = Modexa.get();
```

A module should compile against `:api` (or a separately published API artifact) with `compileOnly`; do not bundle/shade the API into the module.

## Register a module

```java
api.registerModule(this, new ModuleInfo(
    "warps",
    "Modexa Warps",
    getPluginMeta().getVersion(),
    "Configurable warp system"
));
```

`/modexa modules` lists registered modules.

## Register YAML dialogs owned by your module

Store YAML files in your module's own data folder:

```text
plugins/MyModule/dialogs/main.yml
plugins/MyModule/dialogs/create.yml
```

Register the directory with a namespace:

```java
api.dialogs().registerDirectory(
    this,
    getDataFolder().toPath().resolve("dialogs"),
    "warps"
);
```

A YAML file with:

```yaml
id: create
```

is exposed as:

```text
warps:create
```

and can be opened from any Core dialog:

```yaml
- type: open-dialog
  dialog: warps:create
```

## Register a custom YAML action

```java
api.registerAction(this, "warps:create", context -> {
    String name = context.input("name");
    // save warp...
    context.api().dialogs().show(context.player(), "warps:main");
});
```

Then YAML can use it exactly like a built-in action:

```yaml
buttons:
  - label: "<green>Create"
    actions:
      - type: warps:create
```

## Register module placeholders

```java
api.registerPlaceholder(this, "warps:count", context -> "12");
```

YAML:

```yaml
text: "<gray>You have <white>{warps:count}</white> warps."
```

Module placeholders must be namespaced.

## Register module conditions

```java
api.registerCondition(this, "warps:has-any", context -> hasWarps(context.player()));
```

YAML:

```yaml
conditions:
  - type: warps:has-any
```

## Dynamic/player-specific dialogs

This is the important part for systems such as Homes, Warps, Friends, Mail, Claims, etc.

```java
api.dialogs().registerProvider(this, "warps:main", context -> {
    Player player = context.player();

    List<Map<String, Object>> buttons = new ArrayList<>();
    for (Warp warp : getWarps(player)) {
        buttons.add(DialogSpec.button(
            "warp-" + warp.id(),
            "<green>" + warp.name(),
            DialogSpec.list(
                DialogSpec.action("warps:teleport", "id", warp.id())
            )
        ));
    }

    return DialogSpec.builder("warps:main")
        .title("<gold><bold>Your Warps</bold></gold>")
        .columns(3)
        .buttons(buttons)
        .build();
});
```

The dialog is built fresh for the player each time it opens.

## Mix YAML + generated dialogs

A module can use both at the same time:

- `homes:main` — dynamically generated because every player has different homes.
- `homes:detail` — dynamically generated from the selected home.
- `homes:create` — YAML because the form layout is static and should be administrator-editable.
- `homes:rename` — YAML with `{homes:selected-name}`.
- `homes:delete-confirm` — YAML confirmation dialog.

This pattern keeps business logic in Java and visual/layout configuration in YAML.

## Module reloads

Modules can hook into `/modexa reload`:

```java
api.registerReloadHook(this, () -> {
    reloadConfig();
    api.dialogs().unregisterOwner(this);
    registerMyDialogsAgain();
});
```

The included Homes module already does this.

## Automatic ownership cleanup

On module disable:

```java
api.unregisterOwner(this);
```

Core removes that plugin's:

- static registered dialogs
- dynamic dialog providers
- custom actions
- custom placeholders
- custom conditions
- module metadata
- reload hooks

No stale module registrations remain.

# Included Homes module

The Homes module demonstrates the full platform.

### Dialogs

```text
homes:main             dynamic provider
homes:detail           dynamic provider
homes:create           YAML
homes:rename           YAML
homes:delete-confirm   YAML
```

### Actions

```text
homes:select
homes:create
homes:rename
homes:teleport
homes:update
homes:delete
```

### Placeholders

```text
{homes:count}
{homes:max}
{homes:selected-name}
{homes:selected-slot}
{homes:selected-world}
```

### Conditions

```text
homes:has-homes
homes:has-selection
```

### Commands

```text
/homes
/home <name>
/sethome [name]
/delhome <name>
/renamehome <old> <new>
/updatehome <name>
```

## Legacy homes.yml protection

The module intentionally keeps the previous format:

```yaml
homes:
  <uuid>:
    SG9tZSAx:
      slot: Home 1
      name: My Base
      world: world
      x: 10.5
      y: 64.0
      z: -20.5
      yaw: 90.0
      pitch: 0.0
```

The Base64 key such as `SG9tZSAx` remains the permanent home identity. Renaming changes only `name:`.

On first startup, if:

```text
plugins/ModexaHomes/homes.yml
```

does not exist but:

```text
plugins/DialogHomes/homes.yml
```

does exist, the module **copies** the legacy file. It never moves or deletes the original.

It also keeps the legacy safety features:

- startup backups
- atomic/temp-file save strategy
- unknown YAML sections retained
- safety read-only mode if an existing file has an unexpected root
- duplicate visible home names do not overwrite each other

## Add Homes to your Core main menu

In `plugins/Modexa/dialogs/main-menu.yml`:

```yaml
buttons:
  - id: homes
    label: "<green><bold>Homes</bold></green>"
    tooltip: "<gray>Manage your saved homes"
    show-in-game-menu: true
    actions:
      - type: open-dialog
        dialog: homes:main
```

The existing pause/game-menu registry limitation still applies: changing the visible ESC-menu button structure requires a full server restart. Runtime dialog/action changes can be reloaded.

# Starting another module

Use `modules/homes` as the reference implementation. A new module generally needs only:

1. `paper-plugin.yml` dependency on `Modexa`.
2. `compileOnly(project(":api"))`.
3. `Modexa.get()` in `onEnable()`.
4. Register its module metadata.
5. Register custom actions/placeholders/conditions.
6. Register YAML directories and/or dynamic providers.
7. Call `api.unregisterOwner(this)` in `onDisable()`.

The Core is deliberately unaware of Homes. Removing `ModexaHomes.jar` removes the feature without modifying Core.
