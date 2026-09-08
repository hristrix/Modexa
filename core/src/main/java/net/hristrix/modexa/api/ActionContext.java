package net.hristrix.modexa.api;

import org.bukkit.entity.Player;

import java.util.Map;

public record ActionContext(
        Player player,
        String dialogId,
        Map<String, Object> inputs,
        Map<String, Object> action,
        ModexaApi api
) {
    public String input(String key) {
        Object value = inputs.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    public String option(String key, String fallback) {
        Object value = action.get(key);
        return value == null ? fallback : String.valueOf(value);
    }
}
