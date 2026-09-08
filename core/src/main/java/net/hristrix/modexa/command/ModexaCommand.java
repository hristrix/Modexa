package net.hristrix.modexa.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.hristrix.modexa.ModexaPlugin;
import net.hristrix.modexa.dialog.DialogDefinition;
import net.hristrix.modexa.validation.DialogConfigValidator;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.*;

public final class ModexaCommand implements BasicCommand {
    private final ModexaPlugin plugin;

    public ModexaCommand(ModexaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (args.length == 0) {
            help(sender);
            return;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "open" -> open(sender, args);
            case "list" -> list(sender);
            case "modules" -> modules(sender);
            case "reload" -> reload(sender);
            case "validate" -> validate(sender, args);
            case "create" -> create(sender, args);
            case "clone" -> cloneDialog(sender, args);
            case "delete", "remove" -> delete(sender, args);
            default -> help(sender);
        }
    }

    private void open(CommandSender sender, String[] args) {
        if (!sender.hasPermission("modexa.open") && !sender.hasPermission("modexa.admin")) {
            deny(sender); return;
        }
        if (args.length < 2) {
            plain(sender, "Usage: /modexa open <dialog> [player]"); return;
        }
        Player target;
        if (args.length >= 3) {
            if (!sender.hasPermission("modexa.admin")) { deny(sender); return; }
            target = Bukkit.getPlayerExact(args[2]);
            if (target == null) { plain(sender, "Player not found: " + args[2]); return; }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            plain(sender, "Console must specify a player: /modexa open <dialog> <player>"); return;
        }
        plugin.renderer().show(target, args[1]);
    }

    private void list(CommandSender sender) {
        if (!sender.hasPermission("modexa.admin")) { deny(sender); return; }
        plain(sender, "Loaded dialogs (" + plugin.dialogs().size() + "): " + String.join(", ", plugin.dialogs().ids()));
    }

    private void modules(CommandSender sender) {
        if (!sender.hasPermission("modexa.admin")) { deny(sender); return; }
        var modules = plugin.api().moduleInfos();
        if (modules.isEmpty()) { plain(sender, "No Modexa modules are registered."); return; }
        plain(sender, "Modexa modules (" + modules.size() + "):");
        for (var module : modules) {
            plain(sender, "- " + module.name() + " [" + module.id() + "] v" + module.version() + " - " + module.description());
        }
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("modexa.reload") && !sender.hasPermission("modexa.admin")) {
            deny(sender); return;
        }
        plugin.reloadConfig();
        int count = plugin.dialogs().reloadAll();
        plugin.api().reloadExtensions();
        int errors = 0;
        int warnings = 0;
        for (DialogDefinition def : plugin.dialogs().all()) {
            DialogConfigValidator.Report report = plugin.validator().validate(def);
            errors += report.errors().size();
            warnings += report.warnings().size();
        }
        plain(sender, "Reloaded " + count + " dialog(s). Validation: " + errors + " error(s), " + warnings
                + " warning(s). Runtime actions are refreshed. Pause/game-menu button layout changes require a server restart.");
    }

    private void validate(CommandSender sender, String[] args) {
        if (!sender.hasPermission("modexa.admin")) { deny(sender); return; }
        if (args.length >= 2) {
            DialogDefinition def = plugin.dialogs().get(args[1]).orElse(null);
            if (def == null) { plain(sender, "Unknown dialog: " + args[1]); return; }
            printReport(sender, def);
            return;
        }
        if (plugin.dialogs().size() == 0) {
            plain(sender, "No dialogs are loaded."); return;
        }
        for (DialogDefinition def : plugin.dialogs().all()) printReport(sender, def);
    }

    private void printReport(CommandSender sender, DialogDefinition def) {
        DialogConfigValidator.Report report = plugin.validator().validate(def);
        if (report.valid() && report.warnings().isEmpty()) {
            plain(sender, "[OK] " + def.id());
            return;
        }
        plain(sender, (report.valid() ? "[WARN] " : "[ERROR] ") + def.id());
        for (String error : report.errors()) plain(sender, "  ERROR: " + error);
        for (String warning : report.warnings()) plain(sender, "  WARN: " + warning);
    }

    private void create(CommandSender sender, String[] args) {
        if (!sender.hasPermission("modexa.create") && !sender.hasPermission("modexa.admin")) {
            deny(sender); return;
        }
        if (args.length < 2) { plain(sender, "Usage: /modexa create <id>"); return; }
        try {
            boolean created = plugin.dialogs().createTemplate(args[1]);
            plain(sender, created ? "Created dialog '" + args[1].toLowerCase(Locale.ROOT) + "'. Edit its YAML and run /modexa reload."
                    : "A dialog/file with that ID already exists.");
        } catch (IllegalArgumentException | IOException ex) {
            plain(sender, "Could not create dialog: " + ex.getMessage());
        }
    }

    private void cloneDialog(CommandSender sender, String[] args) {
        if (!sender.hasPermission("modexa.create") && !sender.hasPermission("modexa.admin")) {
            deny(sender); return;
        }
        if (args.length < 3) { plain(sender, "Usage: /modexa clone <existing> <new-id>"); return; }
        try {
            boolean cloned = plugin.dialogs().cloneDialog(args[1], args[2]);
            plain(sender, cloned ? "Cloned '" + args[1] + "' to '" + args[2].toLowerCase(Locale.ROOT) + "'."
                    : "Source dialog was not found.");
        } catch (IllegalArgumentException | IllegalStateException | IOException ex) {
            plain(sender, "Could not clone dialog: " + ex.getMessage());
        }
    }

    private void delete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("modexa.delete") && !sender.hasPermission("modexa.admin")) {
            deny(sender); return;
        }
        if (args.length < 2) { plain(sender, "Usage: /modexa delete <id>"); return; }
        try {
            boolean deleted = plugin.dialogs().delete(args[1]);
            if (!deleted) {
                plain(sender, "Unknown dialog: " + args[1]);
            } else if (plugin.getConfig().getBoolean("safe-delete", true)) {
                plain(sender, "Dialog archived to plugins/Modexa/deleted/ and unloaded.");
            } else {
                plain(sender, "Dialog deleted and unloaded.");
            }
        } catch (IOException ex) {
            plain(sender, "Could not delete dialog: " + ex.getMessage());
        }
    }

    private void help(CommandSender sender) {
        plain(sender, "Modexa commands:");
        plain(sender, "/modexa open <id> [player] - open a dialog");
        if (sender.hasPermission("modexa.admin")) {
            plain(sender, "/modexa list - list loaded dialogs");
            plain(sender, "/modexa modules - list API modules");
            plain(sender, "/modexa reload - reload YAML files");
            plain(sender, "/modexa validate [id] - validate YAML");
            plain(sender, "/modexa create <id> - create a template");
            plain(sender, "/modexa clone <id> <new-id> - clone a dialog");
            plain(sender, "/modexa delete <id> - archive/delete a dialog");
        }
    }

    private void deny(CommandSender sender) {
        plain(sender, "You do not have permission to use that command.");
    }

    private void plain(CommandSender sender, String text) {
        sender.sendMessage(text);
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (args.length == 1) {
            return filter(List.of("open", "list", "modules", "reload", "validate", "create", "clone", "delete"), args[0]);
        }
        if (args.length == 2 && Set.of("open", "validate", "clone", "delete").contains(args[0].toLowerCase(Locale.ROOT))) {
            return filter(new ArrayList<>(plugin.dialogs().ids()), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("open") && sender.hasPermission("modexa.admin")) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[2]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> source, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return source.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(lower)).sorted().toList();
    }
}
