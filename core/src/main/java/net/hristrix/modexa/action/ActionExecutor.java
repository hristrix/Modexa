package net.hristrix.modexa.action;

import net.hristrix.modexa.ModexaPlugin;
import net.hristrix.modexa.validation.ConditionEvaluator;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ActionExecutor {
    private final ModexaPlugin plugin;
    private final ConditionEvaluator conditions;

    public ActionExecutor(ModexaPlugin plugin) {
        this.plugin = plugin;
        this.conditions = new ConditionEvaluator(plugin);
    }

    public void execute(Player player, String sourceDialog, List<Map<?, ?>> actions, Map<String, Object> inputs) {
        int maximum = Math.max(1, plugin.getConfig().getInt("max-actions-per-click", 64));
        if (actions.size() > maximum) {
            plugin.getLogger().warning("Dialog '" + sourceDialog + "' attempted " + actions.size()
                    + " actions in one click; maximum is " + maximum + ".");
        }
        executeFrom(player, sourceDialog, actions, inputs, 0, Math.min(actions.size(), maximum));
    }

    private void executeFrom(Player player, String sourceDialog, List<Map<?, ?>> actions,
                             Map<String, Object> inputs, int index, int limit) {
        if (!player.isOnline() || index >= limit) return;

        for (int i = index; i < limit; i++) {
            Map<?, ?> action = actions.get(i);
            String type = string(action, "type", "").toLowerCase(Locale.ROOT);
            if (type.isBlank()) continue;

            ConditionEvaluator.Result condition = conditions.evaluate(player, action.get("conditions"), inputs);
            if (!condition.success()) {
                if (bool(action, "show-deny-message", false)) {
                    plugin.texts().send(player, condition.message(), inputs);
                }
                if (bool(action, "stop-on-condition-fail", false)) return;
                continue;
            }

            if (type.equals("delay") || type.equals("wait")) {
                long ticks = Math.max(1L, longNumber(action, "ticks", 20L));
                int next = i + 1;
                plugin.getServer().getScheduler().runTaskLater(plugin,
                        () -> executeFrom(player, sourceDialog, actions, inputs, next, limit), ticks);
                return;
            }

            try {
                runOne(player, sourceDialog, action, inputs);
            } catch (Exception ex) {
                plugin.getLogger().warning("Action '" + type + "' in dialog '" + sourceDialog
                        + "' failed: " + ex.getMessage());
                if (plugin.debug()) ex.printStackTrace();
                if (bool(action, "stop-on-error", false)) return;
            }
        }
    }

    private void runOne(Player player, String sourceDialog, Map<?, ?> action, Map<String, Object> inputs) throws Exception {
        String type = string(action, "type", "").toLowerCase(Locale.ROOT);
        switch (type) {
            case "message", "send-message" -> player.sendMessage(plugin.texts().component(
                    player, string(action, "text", ""), inputs));

            case "broadcast" -> plugin.getServer().broadcast(plugin.texts().component(
                    player, string(action, "text", ""), inputs));

            case "actionbar", "action-bar" -> player.sendActionBar(plugin.texts().component(
                    player, string(action, "text", ""), inputs));

            case "title" -> {
                String titleText = string(action, "title", "");
                String subtitleText = string(action, "subtitle", "");
                long fadeIn = Math.max(0, longNumber(action, "fade-in-ms", 250));
                long stay = Math.max(0, longNumber(action, "stay-ms", 2000));
                long fadeOut = Math.max(0, longNumber(action, "fade-out-ms", 500));
                player.showTitle(Title.title(
                        plugin.texts().component(player, titleText, inputs),
                        plugin.texts().component(player, subtitleText, inputs),
                        Title.Times.times(Duration.ofMillis(fadeIn), Duration.ofMillis(stay), Duration.ofMillis(fadeOut))
                ));
            }

            case "player-command", "command" -> {
                String command = plugin.texts().resolvePlain(player, string(action, "command", ""), inputs);
                if (command.startsWith("/")) command = command.substring(1);
                if (!command.isBlank()) plugin.getServer().dispatchCommand(player, command);
            }

            case "console-command" -> {
                String command = plugin.texts().resolvePlain(player, string(action, "command", ""), inputs);
                if (command.startsWith("/")) command = command.substring(1);
                if (!command.isBlank()) plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), command);
            }

            case "open-dialog", "dialog" -> {
                String target = plugin.texts().resolvePlain(player, string(action, "dialog", ""), inputs);
                plugin.getServer().getScheduler().runTask(plugin, () -> plugin.renderer().show(player, target));
            }

            case "close-dialog", "close" -> player.closeDialog();

            case "sound", "play-sound" -> {
                String sound = plugin.texts().resolvePlain(player, string(action, "sound", "minecraft:ui.button.click"), inputs);
                SoundCategory category;
                try {
                    category = SoundCategory.valueOf(string(action, "category", "MASTER").toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    category = SoundCategory.MASTER;
                }
                float volume = (float) decimal(action, "volume", 1.0);
                float pitch = (float) decimal(action, "pitch", 1.0);
                player.playSound(player.getLocation(), sound, category, volume, pitch);
            }

            case "teleport" -> {
                String worldName = plugin.texts().resolvePlain(player, string(action, "world", "{world}"), inputs);
                World world = Bukkit.getWorld(worldName);
                if (world == null) throw new IllegalArgumentException("Unknown world '" + worldName + "'");
                double x = resolvedDouble(player, action, "x", player.getX(), inputs);
                double y = resolvedDouble(player, action, "y", player.getY(), inputs);
                double z = resolvedDouble(player, action, "z", player.getZ(), inputs);
                float yaw = (float) resolvedDouble(player, action, "yaw", player.getYaw(), inputs);
                float pitch = (float) resolvedDouble(player, action, "pitch", player.getPitch(), inputs);
                player.teleport(new Location(world, x, y, z, yaw, pitch));
            }

            case "give-item" -> {
                String materialName = plugin.texts().resolvePlain(player, string(action, "material", "STONE"), inputs);
                Material material = Material.matchMaterial(materialName);
                if (material == null) throw new IllegalArgumentException("Unknown material '" + materialName + "'");
                int amount = Math.max(1, integer(action, "amount", 1));
                ItemStack stack = new ItemStack(material, amount);
                String displayName = string(action, "name", "");
                if (!displayName.isBlank()) {
                    ItemMeta meta = stack.getItemMeta();
                    meta.displayName(plugin.texts().component(player, displayName, inputs));
                    stack.setItemMeta(meta);
                }
                player.getInventory().addItem(stack);
            }

            case "take-item" -> {
                String materialName = plugin.texts().resolvePlain(player, string(action, "material", "STONE"), inputs);
                Material material = Material.matchMaterial(materialName);
                if (material == null) throw new IllegalArgumentException("Unknown material '" + materialName + "'");
                int amount = Math.max(1, integer(action, "amount", 1));
                removeMaterial(player, material, amount);
            }

            case "gamemode", "set-gamemode" -> {
                GameMode gameMode = GameMode.valueOf(string(action, "gamemode", "SURVIVAL").toUpperCase(Locale.ROOT));
                player.setGameMode(gameMode);
            }

            case "set-health" -> {
                double health = Math.max(0, Math.min(player.getMaxHealth(), decimal(action, "value", player.getHealth())));
                player.setHealth(health);
            }

            case "set-food" -> player.setFoodLevel(Math.max(0, Math.min(20, integer(action, "value", player.getFoodLevel()))));

            case "kick" -> player.kick(plugin.texts().component(player, string(action, "reason", "Disconnected"), inputs));

            case "nothing", "none" -> {
            }

            default -> {
                var custom = plugin.api().action(type).orElse(null);
                if (custom == null) throw new IllegalArgumentException("Unknown action type '" + type + "'");
                Map<String, Object> actionMap = new LinkedHashMap<>();
                action.forEach((k, v) -> actionMap.put(String.valueOf(k), v));
                custom.execute(new net.hristrix.modexa.api.ActionContext(
                        player, sourceDialog, Map.copyOf(inputs), Map.copyOf(actionMap), plugin.api()));
            }
        }
    }

    private void removeMaterial(Player player, Material material, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || stack.getType() != material) continue;
            if (stack.getAmount() <= remaining) {
                remaining -= stack.getAmount();
                contents[slot] = null;
            } else {
                stack.setAmount(stack.getAmount() - remaining);
                remaining = 0;
            }
        }
        player.getInventory().setStorageContents(contents);
    }

    private double resolvedDouble(Player player, Map<?, ?> action, String key, double fallback, Map<String, Object> inputs) {
        Object raw = action.get(key);
        if (raw == null) return fallback;
        String resolved = plugin.texts().resolvePlain(player, String.valueOf(raw), inputs);
        try {
            return Double.parseDouble(resolved);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("'" + key + "' is not a number after placeholder resolution: " + resolved);
        }
    }

    private static String string(Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static boolean bool(Map<?, ?> map, String key, boolean fallback) {
        Object value = map.get(key);
        return value instanceof Boolean b ? b : value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    private static int integer(Map<?, ?> map, String key, int fallback) {
        Object value = map.get(key);
        if (value instanceof Number n) return n.intValue();
        try { return value == null ? fallback : Integer.parseInt(String.valueOf(value)); }
        catch (NumberFormatException ex) { return fallback; }
    }

    private static long longNumber(Map<?, ?> map, String key, long fallback) {
        Object value = map.get(key);
        if (value instanceof Number n) return n.longValue();
        try { return value == null ? fallback : Long.parseLong(String.valueOf(value)); }
        catch (NumberFormatException ex) { return fallback; }
    }

    private static double decimal(Map<?, ?> map, String key, double fallback) {
        Object value = map.get(key);
        if (value instanceof Number n) return n.doubleValue();
        try { return value == null ? fallback : Double.parseDouble(String.valueOf(value)); }
        catch (NumberFormatException ex) { return fallback; }
    }
}
