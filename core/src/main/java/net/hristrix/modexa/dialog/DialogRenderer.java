package net.hristrix.modexa.dialog;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.body.PlainMessageDialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.hristrix.modexa.ModexaPlugin;
import net.hristrix.modexa.validation.InputValidator;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public final class DialogRenderer {
    private final ModexaPlugin plugin;
    private final InputValidator inputValidator;

    public DialogRenderer(ModexaPlugin plugin) {
        this.plugin = plugin;
        this.inputValidator = new InputValidator(plugin);
    }

    public boolean show(Player player, String id) {
        DialogDefinition definition = plugin.dialogs().resolve(player, id).orElse(null);
        if (definition == null) {
            plugin.texts().send(player, "<red>Unknown dialog <white>" + id + "</white>.");
            return false;
        }
        ConfigurationSection cfg = definition.config();
        if (!cfg.getBoolean("enabled", true)) {
            plugin.texts().send(player, "<red>This dialog is currently disabled.");
            return false;
        }
        String permission = cfg.getString("permission", "");
        if (!permission.isBlank() && !player.hasPermission(permission)) {
            plugin.texts().send(player, cfg.getString("no-permission-message",
                    "<red>You do not have permission to open this dialog."));
            return false;
        }

        try {
            Dialog dialog = build(player, definition);
            player.showDialog(dialog);
            return true;
        } catch (Exception ex) {
            plugin.getLogger().severe("Could not build dialog '" + definition.id() + "': " + ex.getMessage());
            if (plugin.debug()) ex.printStackTrace();
            plugin.texts().send(player, "<red>This dialog could not be opened because its YAML is invalid.");
            return false;
        }
    }

    private Dialog build(Player player, DialogDefinition definition) {
        ConfigurationSection cfg = definition.config();
        List<DialogBody> bodies = buildBodies(player, cfg);
        List<DialogInput> inputs = buildInputs(player, cfg);

        DialogBase.Builder baseBuilder = DialogBase.builder(plugin.texts().component(player,
                cfg.getString("title", "<white>Dialog")));
        String externalTitle = cfg.getString("external-title", "");
        if (!externalTitle.isBlank()) {
            baseBuilder.externalTitle(plugin.texts().component(player, externalTitle));
        }
        baseBuilder
                .canCloseWithEscape(cfg.getBoolean("can-close-with-escape", true))
                .pause(cfg.getBoolean("pause", false))
                .afterAction(parseAfterAction(cfg.getString("after-action", "CLOSE")))
                .body(bodies)
                .inputs(inputs);

        String typeName = cfg.getString("type", "multi_action").toLowerCase(Locale.ROOT);
        DialogType type = switch (typeName) {
            case "notice" -> DialogType.notice(buildRequiredButton(player, definition, cfg.getConfigurationSection("button"), "OK"));
            case "confirmation", "confirm" -> DialogType.confirmation(
                    buildRequiredButton(player, definition, cfg.getConfigurationSection("yes-button"), "Yes"),
                    buildRequiredButton(player, definition, cfg.getConfigurationSection("no-button"), "No")
            );
            case "multi_action", "multi-action", "form", "menu" -> {
                List<ActionButton> buttons = new ArrayList<>();
                List<Map<?, ?>> configured = cfg.getMapList("buttons");
                for (Map<?, ?> raw : configured) {
                    ActionButton button = buildButton(player, definition, raw);
                    if (button != null) buttons.add(button);
                }
                if (buttons.isEmpty()) {
                    buttons.add(ActionButton.create(plugin.texts().component(player, "<gray>Close"), null, 150,
                            DialogAction.customClick((response, audience) -> {
                                if (audience instanceof Player p) p.closeDialog();
                            }, ClickCallback.Options.builder().uses(1).build())));
                }
                ActionButton exit = null;
                ConfigurationSection exitSection = cfg.getConfigurationSection("exit-button");
                if (exitSection != null && exitSection.getBoolean("enabled", true)) {
                    exit = buildRequiredButton(player, definition, exitSection, "Close");
                }
                int columns = clamp(cfg.getInt("columns", 1), 1, 10);
                yield DialogType.multiAction(buttons, exit, columns);
            }
            default -> throw new IllegalArgumentException("Unsupported dialog type '" + typeName + "'");
        };

        DialogBase base = baseBuilder.build();
        return Dialog.create(builder -> builder.empty().base(base).type(type));
    }

    private List<DialogBody> buildBodies(Player player, ConfigurationSection cfg) {
        List<DialogBody> result = new ArrayList<>();
        for (Map<?, ?> raw : cfg.getMapList("body")) {
            String type = string(raw, "type", "text").toLowerCase(Locale.ROOT);
            switch (type) {
                case "text", "message", "plain" -> {
                    String text = string(raw, "text", "");
                    int width = clamp(integer(raw, "width", 320), 1, 1024);
                    result.add(DialogBody.plainMessage(plugin.texts().component(player, text), width));
                }
                case "item" -> result.add(buildItemBody(player, raw));
                default -> throw new IllegalArgumentException("Unknown body type '" + type + "'");
            }
        }
        return result;
    }

    private DialogBody buildItemBody(Player player, Map<?, ?> raw) {
        String materialName = string(raw, "material", "STONE");
        Material material = Material.matchMaterial(materialName);
        if (material == null) throw new IllegalArgumentException("Unknown item material '" + materialName + "'");
        int amount = clamp(integer(raw, "amount", 1), 1, material.getMaxStackSize());
        ItemStack item = new ItemStack(material, amount);
        String name = string(raw, "name", "");
        Object loreObject = raw.get("lore");
        if (!name.isBlank() || loreObject instanceof List<?>) {
            ItemMeta meta = item.getItemMeta();
            if (!name.isBlank()) meta.displayName(plugin.texts().component(player, name));
            if (loreObject instanceof List<?> loreList) {
                meta.lore(loreList.stream()
                        .map(line -> plugin.texts().component(player, String.valueOf(line)))
                        .toList());
            }
            item.setItemMeta(meta);
        }
        String descriptionText = string(raw, "description", "");
        PlainMessageDialogBody description = descriptionText.isBlank() ? null
                : DialogBody.plainMessage(plugin.texts().component(player, descriptionText),
                clamp(integer(raw, "description-width", 200), 1, 1024));
        return DialogBody.item(
                item,
                description,
                bool(raw, "show-decorations", true),
                bool(raw, "show-tooltip", true),
                clamp(integer(raw, "width", 16), 1, 256),
                clamp(integer(raw, "height", 16), 1, 256)
        );
    }

    private List<DialogInput> buildInputs(Player player, ConfigurationSection cfg) {
        List<DialogInput> result = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (Map<?, ?> raw : cfg.getMapList("inputs")) {
            String type = string(raw, "type", "text").toLowerCase(Locale.ROOT);
            String key = string(raw, "key", "").trim();
            if (!key.matches("[A-Za-z0-9_.-]{1,64}")) {
                throw new IllegalArgumentException("Invalid input key '" + key + "'");
            }
            if (!keys.add(key)) {
                throw new IllegalArgumentException("Duplicate input key '" + key + "'");
            }
            String label = string(raw, "label", key);
            switch (type) {
                case "text" -> {
                    TextDialogInput.MultilineOptions multiline = null;
                    if (bool(raw, "multiline", false)) {
                        Integer maxLines = nullableInteger(raw, "max-lines");
                        Integer height = nullableInteger(raw, "height");
                        multiline = TextDialogInput.MultilineOptions.create(maxLines, height);
                    }
                    result.add(DialogInput.text(
                            key,
                            clamp(integer(raw, "width", 300), 1, 1024),
                            plugin.texts().component(player, label),
                            bool(raw, "label-visible", true),
                            plugin.texts().resolvePlain(player, string(raw, "initial", ""), Map.of()),
                            Math.max(1, integer(raw, "max-length", 256)),
                            multiline
                    ));
                }
                case "boolean", "bool", "checkbox" -> result.add(DialogInput.bool(
                        key,
                        plugin.texts().component(player, label),
                        bool(raw, "initial", false),
                        string(raw, "on-true", "true"),
                        string(raw, "on-false", "false")
                ));
                case "number", "range", "number_range" -> {
                    float min = (float) decimal(raw, "min", decimal(raw, "start", 0));
                    float max = (float) decimal(raw, "max", decimal(raw, "end", 100));
                    Float initial = raw.containsKey("initial") ? (float) decimal(raw, "initial", min) : null;
                    Float step = raw.containsKey("step") ? (float) Math.max(0.000001, decimal(raw, "step", 1)) : null;
                    result.add(DialogInput.numberRange(
                            key,
                            clamp(integer(raw, "width", 300), 1, 1024),
                            plugin.texts().component(player, label),
                            string(raw, "label-format", "%s: %s"),
                            min,
                            max,
                            initial,
                            step
                    ));
                }
                case "select", "single_option", "single-option" -> {
                    List<SingleOptionDialogInput.OptionEntry> options = new ArrayList<>();
                    Object rawOptions = raw.get("options");
                    if (!(rawOptions instanceof List<?> list) || list.isEmpty()) {
                        throw new IllegalArgumentException("Select input '" + key + "' requires options.");
                    }
                    boolean initialSeen = false;
                    for (Object optionObject : list) {
                        if (!(optionObject instanceof Map<?, ?> option)) {
                            String id = String.valueOf(optionObject);
                            options.add(SingleOptionDialogInput.OptionEntry.create(id, plugin.texts().component(player, id), !initialSeen));
                            initialSeen = true;
                            continue;
                        }
                        String optionId = string(option, "id", "");
                        if (optionId.isBlank()) throw new IllegalArgumentException("Select option in '" + key + "' has no id.");
                        boolean initial = bool(option, "initial", false);
                        if (initial && initialSeen) throw new IllegalArgumentException("Select input '" + key + "' has multiple initial options.");
                        if (initial) initialSeen = true;
                        String display = string(option, "display", optionId);
                        options.add(SingleOptionDialogInput.OptionEntry.create(optionId,
                                plugin.texts().component(player, display), initial));
                    }
                    if (!initialSeen && !options.isEmpty()) {
                        SingleOptionDialogInput.OptionEntry first = options.getFirst();
                        options.set(0, SingleOptionDialogInput.OptionEntry.create(first.id(), first.display(), true));
                    }
                    result.add(DialogInput.singleOption(
                            key,
                            clamp(integer(raw, "width", 300), 1, 1024),
                            options,
                            plugin.texts().component(player, label),
                            bool(raw, "label-visible", true)
                    ));
                }
                default -> throw new IllegalArgumentException("Unknown input type '" + type + "'");
            }
        }
        return result;
    }

    private ActionButton buildRequiredButton(Player player, DialogDefinition definition,
                                             ConfigurationSection section, String fallbackLabel) {
        if (section == null) {
            return ActionButton.create(plugin.texts().component(player, fallbackLabel), null, 150,
                    DialogAction.customClick((response, audience) -> {
                        if (audience instanceof Player p) p.closeDialog();
                    }, ClickCallback.Options.builder().uses(1).build()));
        }
        Map<String, Object> map = section.getValues(false);
        ActionButton button = buildButton(player, definition, map);
        if (button == null) {
            return ActionButton.create(plugin.texts().component(player, fallbackLabel), null, 150,
                    DialogAction.customClick((response, audience) -> {
                        if (audience instanceof Player p) p.closeDialog();
                    }, ClickCallback.Options.builder().uses(1).build()));
        }
        return button;
    }

    private ActionButton buildButton(Player player, DialogDefinition definition, Map<?, ?> raw) {
        String permission = string(raw, "permission", "");
        boolean hasPermission = permission.isBlank() || player.hasPermission(permission);
        if (!hasPermission && bool(raw, "hide-without-permission", false)) {
            return null;
        }
        String label = string(raw, "label", "Button");
        String tooltip = string(raw, "tooltip", "");
        int width = clamp(integer(raw, "width", 150), 1, 1024);
        List<Map<?, ?>> actions = asMapList(raw.get("actions"));
        Object conditions = raw.get("conditions");
        String denyMessage = string(raw, "deny-message", "<red>You cannot use this button.");

        return ActionButton.create(
                plugin.texts().component(player, label),
                tooltip.isBlank() ? null : plugin.texts().component(player, tooltip),
                width,
                DialogAction.customClick((response, audience) -> {
                    if (!(audience instanceof Player clicked)) return;
                    Map<String, Object> values = InputSnapshot.capture(definition.config(), response);
                    plugin.getServer().getScheduler().runTask(plugin,
                            () -> executeConfiguredButton(clicked, definition, raw, values, true));
                }, ClickCallback.Options.builder().uses(1).build())
        );
    }


    public void executeConfiguredButton(Player clicked, DialogDefinition definition, Map<?, ?> raw,
                                        Map<String, Object> values, boolean inputsAvailable) {
        if (!clicked.isOnline()) return;

        String permission = string(raw, "permission", "");
        String denyMessage = string(raw, "deny-message", "<red>You cannot use this button.");
        Object conditions = raw.get("conditions");
        List<Map<?, ?>> actions = asMapList(raw.get("actions"));

        if (!permission.isBlank() && !clicked.hasPermission(permission)) {
            plugin.texts().send(clicked, denyMessage, values);
            reopenIfConfigured(clicked, definition.id(), raw, "reopen-on-deny", inputsAvailable);
            return;
        }

        var conditionResult = new net.hristrix.modexa.validation.ConditionEvaluator(plugin)
                .evaluate(clicked, conditions, values);
        if (!conditionResult.success()) {
            plugin.texts().send(clicked,
                    string(raw, "condition-deny-message", conditionResult.message()), values);
            reopenIfConfigured(clicked, definition.id(), raw, "reopen-on-deny", inputsAvailable);
            return;
        }

        boolean validate = bool(raw, "validate-inputs", true);
        if (validate && inputsAvailable) {
            InputValidator.ValidationResult validation = inputValidator.validate(clicked, definition.config(), values);
            if (!InputValidator.ValidationResult.ok().success()) {
                plugin.texts().send(clicked, validation.message(), values);
                reopenIfConfigured(clicked, definition.id(), raw, "reopen-on-validation-fail", true);
                return;
            }
        }

        plugin.actions().execute(clicked, definition.id(), actions, values);
    }

    private void reopenIfConfigured(Player player, String dialogId, Map<?, ?> raw, String key, boolean fallback) {
        if (bool(raw, key, fallback)) {
            plugin.getServer().getScheduler().runTask(plugin, () -> show(player, dialogId));
        }
    }

    private DialogBase.DialogAfterAction parseAfterAction(String value) {
        try {
            return DialogBase.DialogAfterAction.valueOf(value.toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ex) {
            return DialogBase.DialogAfterAction.CLOSE;
        }
    }

    private static List<Map<?, ?>> asMapList(Object object) {
        if (!(object instanceof List<?> list)) return List.of();
        List<Map<?, ?>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) result.add(map);
        }
        return result;
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

    private static Integer nullableInteger(Map<?, ?> map, String key) {
        if (!map.containsKey(key)) return null;
        return integer(map, key, 1);
    }

    private static double decimal(Map<?, ?> map, String key, double fallback) {
        Object value = map.get(key);
        if (value instanceof Number n) return n.doubleValue();
        try { return value == null ? fallback : Double.parseDouble(String.valueOf(value)); }
        catch (NumberFormatException ex) { return fallback; }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
