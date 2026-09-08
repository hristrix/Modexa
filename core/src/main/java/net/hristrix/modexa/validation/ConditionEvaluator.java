package net.hristrix.modexa.validation;

import net.hristrix.modexa.ModexaPlugin;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class ConditionEvaluator {
    private final ModexaPlugin plugin;

    public ConditionEvaluator(ModexaPlugin plugin) {
        this.plugin = plugin;
    }

    public Result evaluate(Player player, Object conditionObject, Map<String, Object> inputs) {
        if (!(conditionObject instanceof List<?> conditions) || conditions.isEmpty()) {
            return Result.pass();
        }
        for (Object rawObject : conditions) {
            if (!(rawObject instanceof Map<?, ?> raw)) continue;
            String type = string(raw, "type", "").toLowerCase(Locale.ROOT);
            String key = string(raw, "key", "");
            String expected = string(raw, "value", "");
            String actual = key.isBlank() ? "" : String.valueOf(inputs.getOrDefault(key, ""));
            boolean pass;

            switch (type) {
                case "permission" -> pass = player.hasPermission(string(raw, "permission", expected));
                case "input-equals", "equals" -> pass = actual.equals(expected);
                case "input-equals-ignore-case", "equals-ignore-case" -> pass = actual.equalsIgnoreCase(expected);
                case "input-not-equals", "not-equals" -> pass = !actual.equals(expected);
                case "input-contains", "contains" -> pass = actual.contains(expected);
                case "input-matches", "matches" -> {
                    try {
                        pass = Pattern.matches(expected, actual);
                    } catch (PatternSyntaxException ex) {
                        plugin.getLogger().warning("Invalid condition regex '" + expected + "': " + ex.getMessage());
                        pass = false;
                    }
                }
                case "input-true", "true" -> pass = Boolean.parseBoolean(actual);
                case "input-false", "false" -> pass = !Boolean.parseBoolean(actual);
                case "number-min" -> pass = number(actual, Double.NEGATIVE_INFINITY) >= number(expected, Double.POSITIVE_INFINITY);
                case "number-max" -> pass = number(actual, Double.POSITIVE_INFINITY) <= number(expected, Double.NEGATIVE_INFINITY);
                default -> {
                    var custom = plugin.api().condition(type).orElse(null);
                    if (custom == null) {
                        plugin.getLogger().warning("Unknown dialog condition type: " + type);
                        pass = false;
                    } else {
                        try {
                            Map<String, Object> conditionMap = new LinkedHashMap<>();
                            raw.forEach((k, v) -> conditionMap.put(String.valueOf(k), v));
                            pass = custom.test(new net.hristrix.modexa.api.ConditionContext(
                                    player, Map.copyOf(inputs), Map.copyOf(conditionMap), plugin.api()));
                        } catch (Exception ex) {
                            plugin.getLogger().warning("Custom condition '" + type + "' failed: " + ex.getMessage());
                            pass = false;
                        }
                    }
                }
            }

            boolean negate = bool(raw, "negate", false);
            if (negate) pass = !pass;
            if (!pass) {
                return new Result(false, string(raw, "deny-message", "<red>The conditions for this action were not met."));
            }
        }
        return Result.pass();
    }

    private static double number(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            return fallback;
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

    public record Result(boolean success, String message) {
        public static Result pass() {
            return new Result(true, "");
        }
    }
}
