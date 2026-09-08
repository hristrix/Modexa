package net.hristrix.modexa.validation;

import net.hristrix.modexa.ModexaPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class InputValidator {
    private final ModexaPlugin plugin;

    public InputValidator(ModexaPlugin plugin) {
        this.plugin = plugin;
    }

    public ValidationResult validate(Player player, ConfigurationSection dialog, Map<String, Object> values) {
        for (Map<?, ?> raw : dialog.getMapList("inputs")) {
            String key = string(raw, "key", "");
            if (key.isBlank()) continue;
            String type = string(raw, "type", "text").toLowerCase(Locale.ROOT);
            Object value = values.get(key);

            switch (type) {
                case "text" -> {
                    String text = value == null ? "" : String.valueOf(value);
                    boolean required = bool(raw, "required", false);
                    int min = integer(raw, "min-length", required ? 1 : 0);
                    int max = integer(raw, "max-length", integer(raw, "maxLength", 256));
                    if (required && text.isBlank()) {
                        return fail(raw, "<red>Please fill in <white>" + key + "</white>.");
                    }
                    if (text.length() < min) {
                        return fail(raw, "<red>" + key + " must contain at least " + min + " characters.");
                    }
                    if (text.length() > max) {
                        return fail(raw, "<red>" + key + " may contain at most " + max + " characters.");
                    }
                    String regex = string(raw, "regex", "");
                    if (!regex.isBlank()) {
                        try {
                            if (!Pattern.matches(regex, text)) {
                                return fail(raw, "<red>The value entered for <white>" + key + "</white> is not valid.");
                            }
                        } catch (PatternSyntaxException ex) {
                            plugin.getLogger().warning("Invalid regex on input '" + key + "': " + ex.getMessage());
                            return new ValidationResult(false, "<red>This form is misconfigured. Please contact an administrator.");
                        }
                    }
                }
                case "boolean", "bool", "checkbox" -> {
                    if (bool(raw, "required-true", false) && !Boolean.TRUE.equals(value)) {
                        return fail(raw, "<red>You must enable <white>" + key + "</white> to continue.");
                    }
                }
                case "number", "range", "number_range" -> {
                    if (!(value instanceof Number number)) {
                        return fail(raw, "<red>Please choose a valid value for <white>" + key + "</white>.");
                    }
                    double numberValue = number.doubleValue();
                    double min = decimal(raw, "min", decimal(raw, "start", 0));
                    double max = decimal(raw, "max", decimal(raw, "end", 100));
                    if (!Double.isFinite(numberValue) || numberValue < min || numberValue > max) {
                        return fail(raw, "<red>" + key + " must be between " + min + " and " + max + ".");
                    }
                }
                case "select", "single_option", "single-option" -> {
                    String selected = value == null ? "" : String.valueOf(value);
                    Set<String> allowed = new HashSet<>();
                    Object options = raw.get("options");
                    if (options instanceof List<?> list) {
                        for (Object optionObject : list) {
                            if (optionObject instanceof Map<?, ?> option) {
                                allowed.add(string(option, "id", ""));
                            } else if (optionObject != null) {
                                allowed.add(String.valueOf(optionObject));
                            }
                        }
                    }
                    if (!allowed.contains(selected)) {
                        return fail(raw, "<red>Please select a valid option for <white>" + key + "</white>.");
                    }
                }
                default -> {
                    return new ValidationResult(false, "<red>Unknown input type <white>" + type + "</white>.");
                }
            }
        }
        return ValidationResult.ok();
    }

    private ValidationResult fail(Map<?, ?> raw, String fallback) {
        return new ValidationResult(false, string(raw, "error-message", fallback));
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
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static double decimal(Map<?, ?> map, String key, double fallback) {
        Object value = map.get(key);
        if (value instanceof Number n) return n.doubleValue();
        try {
            return value == null ? fallback : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    public record ValidationResult(boolean success, String message) {

        public static ValidationResult ok() {
            return new ValidationResult(true, "");
        }
    }
}
