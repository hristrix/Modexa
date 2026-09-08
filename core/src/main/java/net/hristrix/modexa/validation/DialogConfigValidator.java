package net.hristrix.modexa.validation;

import net.hristrix.modexa.ModexaPlugin;
import net.hristrix.modexa.bootstrap.GameMenuSupport;
import net.hristrix.modexa.dialog.DialogDefinition;
import net.hristrix.modexa.dialog.DialogManager;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class DialogConfigValidator {
    private static final Set<String> DIALOG_TYPES = Set.of("multi_action", "multi-action", "form", "menu", "notice", "confirmation", "confirm");
    private static final Set<String> INPUT_TYPES = Set.of("text", "boolean", "bool", "checkbox", "number", "range", "number_range", "select", "single_option", "single-option");
    private static final Set<String> ACTION_TYPES = Set.of(
            "message", "send-message", "broadcast", "actionbar", "action-bar", "title",
            "player-command", "command", "console-command", "open-dialog", "dialog", "close-dialog", "close",
            "sound", "play-sound", "teleport", "give-item", "take-item", "gamemode", "set-gamemode",
            "set-health", "set-food", "kick", "delay", "wait", "nothing", "none"
    );

    private final ModexaPlugin plugin;

    public DialogConfigValidator(ModexaPlugin plugin) {
        this.plugin = plugin;
    }

    public Report validate(DialogDefinition definition) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        ConfigurationSection cfg = definition.config();

        String id = cfg.getString("id", definition.id());
        if (!DialogManager.isSafeId(id)) {
            errors.add("id must be a valid lowercase dialog id; modules may use namespaced ids such as homes:main");
        }
        String title = cfg.getString("title", "");
        if (title.isBlank()) warnings.add("title is empty");

        String type = cfg.getString("type", "multi_action").toLowerCase(Locale.ROOT);
        if (!DIALOG_TYPES.contains(type)) errors.add("unsupported dialog type: " + type);

        String after = cfg.getString("after-action", "CLOSE").toUpperCase(Locale.ROOT).replace('-', '_');
        if (!Set.of("CLOSE", "NONE", "WAIT_FOR_RESPONSE").contains(after)) {
            errors.add("after-action must be CLOSE, NONE or WAIT_FOR_RESPONSE");
        }

        validateBodies(cfg.getMapList("body"), errors, warnings);
        Set<String> inputKeys = validateInputs(cfg.getMapList("inputs"), errors, warnings);

        switch (type) {
            case "notice" -> validateButtonSection(cfg.getConfigurationSection("button"), "button", inputKeys, errors, warnings);
            case "confirmation", "confirm" -> {
                validateButtonSection(cfg.getConfigurationSection("yes-button"), "yes-button", inputKeys, errors, warnings);
                validateButtonSection(cfg.getConfigurationSection("no-button"), "no-button", inputKeys, errors, warnings);
            }
            default -> {
                List<Map<?, ?>> buttons = cfg.getMapList("buttons");
                if (buttons.isEmpty()) warnings.add("no buttons configured; a fallback Close button will be shown");
                for (int i = 0; i < buttons.size(); i++) {
                    validateButton(buttons.get(i), "buttons[" + i + "]", inputKeys, errors, warnings);
                }
                validateGameMenuButtonIds(definition, buttons, errors, warnings);
                ConfigurationSection exit = cfg.getConfigurationSection("exit-button");
                if (exit != null) validateButtonSection(exit, "exit-button", inputKeys, errors, warnings);
            }
        }

        return new Report(errors, warnings);
    }


    private void validateGameMenuButtonIds(DialogDefinition definition, List<Map<?, ?>> buttons,
                                           List<String> errors, List<String> warnings) {
        if (!plugin.getConfig().getBoolean("game-menu.enabled", true)) return;
        String configuredMain = plugin.getConfig().getString("game-menu.main-dialog", "main-menu");
        if (configuredMain == null || !definition.id().equalsIgnoreCase(configuredMain)) return;

        Set<String> ids = new HashSet<>();
        for (int i = 0; i < buttons.size(); i++) {
            Map<?, ?> button = buttons.get(i);
            if (!bool(button, "show-in-game-menu", true)) continue;
            String id = GameMenuSupport.buttonId(button, i);
            if (!ids.add(id)) {
                errors.add("buttons[" + i + "] resolves to duplicate game-menu id: " + id);
            }
            Object explicit = button.get("id");
            if (explicit == null || String.valueOf(explicit).isBlank()) {
                warnings.add("buttons[" + i + "] has no stable id; add `id: ...` so /modexa reload can safely refresh its actions without depending on button order");
            }
        }
        if (!definition.config().getMapList("inputs").isEmpty()) {
            warnings.add("the native ESC/game-menu mirror does not render inputs; keep the configured main menu button-only or open input dialogs from its buttons");
        }
    }

    private void validateBodies(List<Map<?, ?>> bodies, List<String> errors, List<String> warnings) {
        for (int i = 0; i < bodies.size(); i++) {
            Map<?, ?> body = bodies.get(i);
            String type = string(body, "type", "text").toLowerCase(Locale.ROOT);
            if (type.equals("item")) {
                String material = string(body, "material", "STONE");
                if (Material.matchMaterial(material) == null) {
                    errors.add("body[" + i + "] has unknown material: " + material);
                }
            } else if (!Set.of("text", "message", "plain").contains(type)) {
                errors.add("body[" + i + "] has unknown type: " + type);
            }
        }
    }

    private Set<String> validateInputs(List<Map<?, ?>> inputs, List<String> errors, List<String> warnings) {
        Set<String> keys = new LinkedHashSet<>();
        for (int i = 0; i < inputs.size(); i++) {
            Map<?, ?> input = inputs.get(i);
            String key = string(input, "key", "");
            String type = string(input, "type", "text").toLowerCase(Locale.ROOT);
            String path = "inputs[" + i + "]";
            if (!key.matches("[A-Za-z0-9_.-]{1,64}")) errors.add(path + " has invalid key: " + key);
            if (!keys.add(key)) errors.add(path + " duplicates input key: " + key);
            if (!INPUT_TYPES.contains(type)) errors.add(path + " has unknown type: " + type);

            if (type.equals("text")) {
                int max = integer(input, "max-length", 256);
                int min = integer(input, "min-length", 0);
                if (max < 1) errors.add(path + " max-length must be at least 1");
                if (min < 0 || min > max) errors.add(path + " min-length must be between 0 and max-length");
                String regex = string(input, "regex", "");
                if (!regex.isBlank()) {
                    try { Pattern.compile(regex); }
                    catch (PatternSyntaxException ex) { errors.add(path + " regex is invalid: " + ex.getMessage()); }
                }
            }
            if (Set.of("number", "range", "number_range").contains(type)) {
                double min = decimal(input, "min", decimal(input, "start", 0));
                double max = decimal(input, "max", decimal(input, "end", 100));
                if (min >= max) errors.add(path + " number min/start must be lower than max/end");
            }
            if (Set.of("select", "single_option", "single-option").contains(type)) {
                Object optionObject = input.get("options");
                if (!(optionObject instanceof List<?> list) || list.isEmpty()) {
                    errors.add(path + " select requires at least one option");
                }
            }
        }
        return keys;
    }

    private void validateButtonSection(ConfigurationSection section, String path, Set<String> inputKeys,
                                       List<String> errors, List<String> warnings) {
        if (section == null) {
            warnings.add(path + " is missing; a fallback button will be used");
            return;
        }
        validateButton(section.getValues(false), path, inputKeys, errors, warnings);
    }

    private void validateButton(Map<?, ?> button, String path, Set<String> inputKeys,
                                List<String> errors, List<String> warnings) {
        String label = string(button, "label", "");
        if (label.isBlank()) warnings.add(path + " has an empty label");
        Object actionObject = button.get("actions");
        if (!(actionObject instanceof List<?> list) || list.isEmpty()) {
            warnings.add(path + " has no actions");
            return;
        }
        int actionIndex = 0;
        for (Object rawAction : list) {
            if (!(rawAction instanceof Map<?, ?> action)) {
                errors.add(path + ".actions[" + actionIndex + "] is not a YAML map");
                actionIndex++;
                continue;
            }
            String type = string(action, "type", "").toLowerCase(Locale.ROOT);
            if (!ACTION_TYPES.contains(type) && plugin.api().action(type).isEmpty()) errors.add(path + ".actions[" + actionIndex + "] has unknown type: " + type);
            if (Set.of("open-dialog", "dialog").contains(type)) {
                String target = string(action, "dialog", "");
                if (target.isBlank()) errors.add(path + ".actions[" + actionIndex + "] open-dialog requires dialog");
                else if (!target.contains("{") && plugin.dialogs().get(target).isEmpty()) {
                    warnings.add(path + ".actions[" + actionIndex + "] references missing dialog: " + target);
                }
            }
            if (Set.of("give-item", "take-item").contains(type)) {
                String material = string(action, "material", "STONE");
                if (!material.contains("{") && Material.matchMaterial(material) == null) {
                    errors.add(path + ".actions[" + actionIndex + "] has unknown material: " + material);
                }
            }
            actionIndex++;
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

    private static double decimal(Map<?, ?> map, String key, double fallback) {
        Object value = map.get(key);
        if (value instanceof Number n) return n.doubleValue();
        try { return value == null ? fallback : Double.parseDouble(String.valueOf(value)); }
        catch (NumberFormatException ex) { return fallback; }
    }

    public record Report(List<String> errors, List<String> warnings) {
        public boolean valid() {
            return errors.isEmpty();
        }
    }
}
