package net.hristrix.modexa.modules.homes;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

public final class HomesCommand implements BasicCommand {
    enum Mode { HOMES, HOME, SETHOME, DELHOME, RENAMEHOME, UPDATEHOME }

    private final HomesModulePlugin plugin;
    private final Mode mode;

    HomesCommand(HomesModulePlugin plugin, Mode mode) {
        this.plugin = plugin;
        this.mode = mode;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by a player.");
            return;
        }

        switch (mode) {
            case HOMES -> plugin.api().dialogs().show(player, "homes:main");
            case HOME -> teleport(player, args);
            case SETHOME -> create(player, args);
            case DELHOME -> delete(player, args);
            case RENAMEHOME -> rename(player, args);
            case UPDATEHOME -> update(player, args);
        }
    }

    private void teleport(Player player, String[] args) {
        if (!require(player, "dynamichomes.teleport")) return;
        if (args.length == 0) { player.sendMessage(Component.text("Usage: /home <name>")); return; }
        String name = String.join(" ", args);
        HomeManager.ResolveResult resolved = plugin.homes().resolveUniqueByName(player.getUniqueId(), name);
        if (!resolved.success()) { player.sendMessage(Component.text(resolved.message())); return; }
        Location target = resolved.home().toLocation();
        if (target == null) { player.sendMessage(Component.text("World '" + resolved.home().worldName() + "' is not loaded.")); return; }
        player.teleportAsync(target).thenAccept(ok -> { if (!ok) player.sendMessage(Component.text("Teleport failed.")); });
    }

    private void create(Player player, String[] args) {
        if (!require(player, "dynamichomes.create")) return;
        if (args.length == 0) { plugin.api().dialogs().show(player, "homes:create"); return; }
        send(player, plugin.homes().create(player, String.join(" ", args)));
    }

    private void delete(Player player, String[] args) {
        if (!require(player, "dynamichomes.delete")) return;
        if (args.length == 0) { player.sendMessage(Component.text("Usage: /delhome <name>")); return; }
        send(player, plugin.homes().deleteByName(player.getUniqueId(), String.join(" ", args)));
    }

    private void rename(Player player, String[] args) {
        if (!require(player, "dynamichomes.rename")) return;
        if (args.length < 2) { player.sendMessage(Component.text("Usage: /renamehome <old-name> <new-name> (use /homes for names containing spaces)")); return; }
        // Command fallback is intentionally simple; the dialog supports names with spaces unambiguously.
        send(player, plugin.homes().renameByName(player.getUniqueId(), args[0], String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length))));
    }

    private void update(Player player, String[] args) {
        if (!require(player, "dynamichomes.update")) return;
        if (args.length == 0) { player.sendMessage(Component.text("Usage: /updatehome <name>")); return; }
        send(player, plugin.homes().updateLocationByName(player, String.join(" ", args)));
    }

    private boolean require(Player player, String permission) {
        if (player.hasPermission(permission)) return true;
        player.sendMessage(Component.text("You do not have permission to do that."));
        return false;
    }

    private void send(Player player, HomeManager.OperationResult result) {
        player.sendMessage(Component.text(result.message()));
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        if (!(source.getSender() instanceof Player player)) return List.of();
        if (mode == Mode.HOME || mode == Mode.DELHOME || mode == Mode.UPDATEHOME || mode == Mode.RENAMEHOME) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return plugin.homes().getHomes(player.getUniqueId()).stream()
                    .map(HomeRecord::name)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .distinct()
                    .sorted()
                    .toList();
        }
        return List.of();
    }
}
