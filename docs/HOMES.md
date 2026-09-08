# Modexa Homes

Modexa Homes is the included home-management module for Modexa.

Players can create, view, teleport to, rename, move and delete their saved homes through Modexa menus. Traditional commands are also available.

## Commands

```text
/homes
/home <name>
/sethome [name]
/delhome <name>
/renamehome <old> <new>
/updatehome <name>
```

## Default limit

The default limit is configured in:

```text
plugins/ModexaHomes/config.yml
```

Example:

```yaml
max-homes: 5
name-max-length: 24
```

## Existing homes

When Modexa Homes starts for the first time and does not yet have its own `homes.yml`, it can import the previous file from:

```text
plugins/DynamicDialogsHomes/homes.yml
```

or:

```text
plugins/DialogHomes/homes.yml
```

The existing file is copied. It is not moved or deleted.

Modexa Homes keeps the legacy slot structure, including keys such as `SG9tZSAx`, so existing player homes and duplicate visible names remain safe.

## Backups

Automatic backups are enabled by default:

```yaml
automatic-backups: true
backup-count: 10
```

Keep your own normal server backups as well.
