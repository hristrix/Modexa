package net.hristrix.modexa.listener;

import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import net.hristrix.modexa.ModexaPlugin;
import net.hristrix.modexa.bootstrap.GameMenuSupport;
import net.hristrix.modexa.dialog.DialogDefinition;
import net.kyori.adventure.key.Key;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.List;
import java.util.Map;

public final class PauseMenuClickListener implements Listener {
    private final ModexaPlugin plugin;

    public PauseMenuClickListener(ModexaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onCustomClick(PlayerCustomClickEvent event) {
        Key key = event.getIdentifier();
        if (!GameMenuSupport.ACTION_NAMESPACE.equals(key.namespace())) return;
        if (!key.value().startsWith(GameMenuSupport.ACTION_PREFIX)) return;
        if (!(event.getCommonConnection() instanceof PlayerGameConnection connection)) return;

        Player player = connection.getPlayer();
        String buttonId = key.value().substring(GameMenuSupport.ACTION_PREFIX.length());

        plugin.getServer().getScheduler().runTask(plugin, () -> handle(player, buttonId));
    }

    private void handle(Player player, String buttonId) {
        if (!player.isOnline()) return;
        if (!plugin.getConfig().getBoolean("game-menu.enabled", true)) {
            plugin.texts().send(player, "<red>The game menu is currently disabled.");
            return;
        }

        String dialogId = plugin.getConfig().getString("game-menu.main-dialog", "main-menu");
        DialogDefinition definition = plugin.dialogs().get(dialogId).orElse(null);
        if (definition == null) {
            plugin.texts().send(player, "<red>The configured main menu could not be found. Ask an administrator to restart/reload the server configuration.");
            return;
        }

        ConfigurationSection cfg = definition.config();
        if (!cfg.getBoolean("enabled", true)) {
            plugin.texts().send(player, "<red>This menu is currently disabled.");
            return;
        }

        String permission = cfg.getString("permission", "");
        if (permission != null && !permission.isBlank() && !player.hasPermission(permission)) {
            plugin.texts().send(player, cfg.getString("no-permission-message",
                    "<red>You do not have permission to open this dialog."));
            return;
        }

        if (buttonId.equals("__exit")) {
            ConfigurationSection exit = cfg.getConfigurationSection("exit-button");
            if (exit != null) {
                plugin.renderer().executeConfiguredButton(player, definition, exit.getValues(false), Map.of(), false);
            } else {
                player.closeDialog();
            }
            return;
        }

        List<Map<?, ?>> buttons = cfg.getMapList("buttons");
        for (int i = 0; i < buttons.size(); i++) {
            Map<?, ?> button = buttons.get(i);
            if (!bool(button, "show-in-game-menu", true)) continue;
            if (!GameMenuSupport.buttonId(button, i).equals(buttonId)) continue;

            plugin.renderer().executeConfiguredButton(player, definition, button, Map.of(), false);
            return;
        }

        plugin.texts().send(player,
                "<yellow>This game-menu button no longer exists in the current YAML. Restart the server to rebuild the pause-menu layout.");
    }

    private static boolean bool(Map<?, ?> map, String key, boolean fallback) {
        Object value = map.get(key);
        return value instanceof Boolean b ? b : value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }
}
