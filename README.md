# Modexa

**A modular menu and feature platform for Minecraft Paper 26.2.**

Modexa gives your server one clean system for menus, forms and expandable features. You can build a custom server menu, open it from Minecraft's pause menu, add buttons and forms with YAML, and install extra Modexa modules such as Homes without turning everything into one huge plugin.

The goal is simple: **make powerful server features easy to manage and easy to expand.**

## ✨ What Modexa can do

- Add a custom **Server Menu** to Minecraft's pause / ESC menu.
- Build menus and forms from easy-to-edit YAML files.
- Add buttons, text fields, checkboxes, sliders and selectable options.
- Decide how many columns a menu uses.
- Open one menu from another to create complete menu flows.
- Run commands, send messages, play sounds, teleport players and more from buttons.
- Use permissions to show different options to different players.
- Reload normal menu changes without rebuilding the plugin.
- Add completely separate feature modules without changing Modexa Core.

Modexa is not limited to one type of menu. It can be the base for things such as:

**Homes · Warps · Kits · Shops · Claims · Friends · Profiles · Server Rules · Admin Tools · Custom Forms · Teleport Menus**

## 🧩 Included in this repository

### Modexa Core

The main platform. It handles the Server Menu, configurable dialogs, actions, inputs, permissions, placeholders, conditions and module support.

### Modexa Homes

A complete Homes module powered by Modexa. Players can:

- Create homes
- View all homes
- Teleport to a home
- Rename a home
- Update a home's location
- Delete a home safely

It also keeps compatibility with the older `homes.yml` format used by DialogHomes / DynamicDialogsHomes.

## 🎮 The player experience

With the game-menu option enabled, a player can press **ESC**, choose your server button, and open the Modexa Server Menu.

Your `main-menu.yml` decides what appears there. For example:

```text
[ Homes ] [ Shop ] [ Profile ]
[ Rules ] [ Warps ] [ Settings ]
```

Each button can open another Modexa menu or perform an action.

## 🚀 Installation

This repository contains the **source code** for Modexa and Modexa Homes.

To run it on a server you need:

- **Paper 26.2**
- **Java 25**
- The built `Modexa` JAR
- Any Modexa module JARs you want, such as `ModexaHomes`

Place the JAR files in your server's `plugins` folder and restart the server.

```text
plugins/
├── Modexa-3.0.0.jar
└── ModexaHomes-3.0.0.jar
```

After the first start, Modexa creates its configuration in:

```text
plugins/Modexa/
```

and Homes stores its data in:

```text
plugins/ModexaHomes/
```

## 🏠 Existing homes are protected

If you are upgrading from the earlier versions we worked on, Modexa Homes can safely import existing home data.

When `plugins/ModexaHomes/homes.yml` does not exist yet, it can copy homes from:

```text
plugins/DynamicDialogsHomes/homes.yml
plugins/DialogHomes/homes.yml
```

The old file is **copied, not moved or deleted**.

The legacy Base64 slot keys are kept intact, so renaming a visible home name does not change the permanent slot identity. Automatic backups are also supported.

## 🖱️ Editing the Server Menu

The main menu is here:

```text
plugins/Modexa/dialogs/main-menu.yml
```

A simple button looks like this:

```yaml
- id: homes
  label: "<green><bold>Homes</bold>"
  tooltip: "<gray>Manage your saved homes"
  show-in-game-menu: true

  actions:
    - type: open-dialog
      dialog: homes:main
```

You can add more buttons for your own server features in the same file.

## 🧱 Menus can use columns

For example:

```yaml
type: multi_action
columns: 3
```

Six buttons will naturally appear as two rows of three:

```text
[ Button 1 ] [ Button 2 ] [ Button 3 ]
[ Button 4 ] [ Button 5 ] [ Button 6 ]
```

## 📝 Forms are configurable too

Modexa supports native Minecraft inputs such as:

- Text input
- Multi-line text
- Yes / No checkbox
- Number slider
- Single-choice options

A menu can collect information and then use those values when a player presses a button.

## 🔄 Useful commands

```text
/modexa
/modexa reload
/modexa dialogs
/modexa modules
/modexa validate
```

Short alias:

```text
/mx
```

Homes also includes familiar commands such as:

```text
/homes
/home <name>
/sethome [name]
/delhome <name>
/renamehome <old> <new>
/updatehome <name>
```

## 🧠 How modules work

Think of **Modexa as the foundation** and modules as optional feature packs.

```text
Modexa
├── Homes
├── Warps
├── Kits
├── Claims
├── Friends
└── Your own modules
```

A module can add its own menus, buttons, commands, saved data and actions while still using the same Modexa interface.

That means your server can grow without putting every feature into one giant plugin.

## 📁 Repository layout

```text
Modexa/
├── core/                 Modexa itself
├── api/                  API used by modules
├── modules/
│   └── homes/            Included Homes module
├── docs/
│   └── DEVELOPER.md      Detailed developer documentation
├── build.gradle.kts
└── settings.gradle.kts
```

## 👨‍💻 Want to create a Modexa module?

Developers can use the public Modexa API to create completely separate plugins that plug into Modexa.

The technical API, module registration, custom actions, placeholders, conditions and dynamic-player-menu examples are documented here:

**[Developer Documentation](docs/DEVELOPER.md)**

## 🔨 Building from source

If you only want to use Modexa on your server, you do not need to understand the code itself. You only need the built JAR files.

For anyone building the project from source:

```bash
gradle :core:build :modules:homes:build
```

The main outputs are:

```text
core/build/libs/Modexa-3.0.0.jar
modules/homes/build/libs/ModexaHomes-3.0.0.jar
```

## 💜 Why Modexa?

Minecraft servers often end up with lots of unrelated commands and separate menus. Modexa is intended to give those features a common home while keeping every major system modular.

**One platform. Your menus. Your modules. Your server.**
