package net.hristrix.modexa.util;

import net.hristrix.modexa.ModexaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Texts {
    private final ModexaPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public Texts(ModexaPlugin plugin) {
        this.plugin = plugin;
    }

    public Component component(Player player, String source, Map<String, Object> inputs) {
        return miniMessage.deserialize(resolveForMiniMessage(player, source, inputs));
    }

    public Component component(Player player, String source) {
        return component(player, source, Map.of());
    }

    public Component component(String source) {
        return miniMessage.deserialize(source == null ? "" : source);
    }

    public String prefix() {
        return plugin.getConfig().getString("prefix", "<dark_gray>[<light_purple>Dialogs</light_purple><dark_gray>] <gray>");
    }

    public void send(Player player, String message, Map<String, Object> inputs) {
        player.sendMessage(component(player, prefix() + message, inputs));
    }

    public void send(Player player, String message) {
        send(player, message, Map.of());
    }

    public String resolvePlain(Player player, String source, Map<String, Object> inputs) {
        String out = source == null ? "" : source;
        Map<String, String> replacements = builtIns(player);
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            out = out.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        for (Map.Entry<String, Object> entry : inputs.entrySet()) {
            String value = entry.getValue() == null ? "" : String.valueOf(entry.getValue());
            out = out.replace("{input:" + entry.getKey() + "}", value);
        }
        out = plugin.api().resolvePlaceholders(player, out, inputs, false);
        return applyPlaceholderApi(player, out);
    }

    public String resolveForMiniMessage(Player player, String source, Map<String, Object> inputs) {
        String out = source == null ? "" : source;
        Map<String, String> replacements = builtIns(player);
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            out = out.replace("{" + entry.getKey() + "}", miniMessage.escapeTags(entry.getValue()));
        }
        for (Map.Entry<String, Object> entry : inputs.entrySet()) {
            String value = entry.getValue() == null ? "" : String.valueOf(entry.getValue());
            out = out.replace("{input:" + entry.getKey() + "}", miniMessage.escapeTags(value));
        }
        out = plugin.api().resolvePlaceholders(player, out, inputs, true);
        return applyPlaceholderApi(player, out);
    }

    private Map<String, String> builtIns(Player player) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("player", player.getName());
        map.put("uuid", player.getUniqueId().toString());
        map.put("world", player.getWorld().getName());
        map.put("x", format(player.getX()));
        map.put("y", format(player.getY()));
        map.put("z", format(player.getZ()));
        map.put("yaw", format(player.getYaw()));
        map.put("pitch", format(player.getPitch()));
        map.put("health", format(player.getHealth()));
        map.put("food", Integer.toString(player.getFoodLevel()));
        map.put("level", Integer.toString(player.getLevel()));
        return map;
    }

    private String format(double value) {
        if (Math.rint(value) == value) {
            return Long.toString((long) value);
        }
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private String applyPlaceholderApi(Player player, String text) {
        if (plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return text;
        }
        try {
            Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            Method method = papi.getMethod("setPlaceholders", org.bukkit.OfflinePlayer.class, String.class);
            Object result = method.invoke(null, player, text);
            return result instanceof String s ? s : text;
        } catch (ReflectiveOperationException ex) {
            if (plugin.debug()) {
                plugin.getLogger().warning("PlaceholderAPI hook failed: " + ex.getMessage());
            }
            return text;
        }
    }
}
