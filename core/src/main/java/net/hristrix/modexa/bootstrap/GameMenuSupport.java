package net.hristrix.modexa.bootstrap;

import java.util.Locale;
import java.util.Map;

public final class GameMenuSupport {
    public static final String ACTION_NAMESPACE = "modexa";
    public static final String ACTION_PREFIX = "pause/";
    public static final String REGISTRY_KEY = "modexa:game_menu";

    private GameMenuSupport() {
    }

    public static String buttonId(Map<?, ?> button, int index) {
        Object raw = button.get("id");
        String candidate = raw == null || String.valueOf(raw).isBlank()
                ? "button-" + (index + 1)
                : String.valueOf(raw);
        return sanitize(candidate);
    }

    public static String sanitize(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
        normalized = normalized.replaceAll("[^a-z0-9_.-]", "_");
        normalized = normalized.replaceAll("_+", "_");
        if (normalized.isBlank()) normalized = "button";
        if (normalized.length() > 48) normalized = normalized.substring(0, 48);
        return normalized;
    }

    public static String actionKey(String buttonId) {
        return ACTION_NAMESPACE + ":" + ACTION_PREFIX + sanitize(buttonId);
    }
}
