package net.hristrix.modexa.bootstrap;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.registry.event.RegistryEvents;
import io.papermc.paper.registry.keys.DialogKeys;
import io.papermc.paper.registry.keys.tags.DialogTagKeys;
import io.papermc.paper.registry.TypedKey;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * Registers the one registry-backed dialog that Minecraft can expose from the ESC/game menu.
 * The live Modexa renderer remains runtime/YAML driven; this class only creates the
 * static client-facing mirror needed by minecraft:pause_screen_additions.
 */
public final class ModexaBootstrap implements PluginBootstrap {
    private static final TypedKey<Dialog> GAME_MENU_DIALOG = DialogKeys.create(Key.key(GameMenuSupport.REGISTRY_KEY));
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    @Override
    public void bootstrap(BootstrapContext context) {
        Path data = context.getDataDirectory();
        try {
            Files.createDirectories(data);
            migrateLegacyDynamicDialogs(context, data);
            Files.createDirectories(data.resolve("dialogs"));
            copyBundledIfMissing("config.yml", data.resolve("config.yml"));
            copyBundledIfMissing("dialogs/main-menu.yml", data.resolve("dialogs/main-menu.yml"));
        } catch (IOException ex) {
            context.getLogger().warn("Could not prepare Modexa bootstrap files: {}", ex.getMessage());
        }

        YamlConfiguration global = YamlConfiguration.loadConfiguration(data.resolve("config.yml").toFile());
        if (!global.getBoolean("game-menu.enabled", true)) {
            context.getLogger().info("Modexa game-menu integration is disabled.");
            return;
        }

        String dialogId = global.getString("game-menu.main-dialog", "main-menu");
        if (dialogId == null || !dialogId.matches("[a-z0-9_-]{1,64}")) {
            context.getLogger().warn("game-menu.main-dialog has an invalid id: {}", dialogId);
            return;
        }

        Path mainMenuFile = data.resolve("dialogs").resolve(dialogId + ".yml");
        if (!Files.isRegularFile(mainMenuFile)) {
            context.getLogger().warn("Game-menu integration is enabled but {} does not exist.", mainMenuFile);
            return;
        }

        YamlConfiguration menu = YamlConfiguration.loadConfiguration(mainMenuFile.toFile());
        PauseMirror mirror;
        try {
            mirror = parseMirror(menu, context);
        } catch (IllegalArgumentException ex) {
            context.getLogger().warn("Could not register game-menu dialog from {}: {}", mainMenuFile.getFileName(), ex.getMessage());
            return;
        }

        context.getLifecycleManager().registerEventHandler(RegistryEvents.DIALOG.compose(), event ->
                event.registry().register(GAME_MENU_DIALOG, builder -> builder
                        .base(mirror.base())
                        .type(mirror.type())
                )
        );

        // Add, never replace, so other plugins/datapacks keep their pause-screen entries.
        context.getLifecycleManager().registerEventHandler(
                LifecycleEvents.TAGS.postFlatten(RegistryKey.DIALOG),
                event -> event.registrar().addToTag(
                        DialogTagKeys.PAUSE_SCREEN_ADDITIONS,
                        Set.of(GAME_MENU_DIALOG)
                )
        );

        context.getLogger().info("Registered Modexa pause/game-menu entry from dialogs/{}.yml ({} button(s)).",
                dialogId, mirror.buttonCount());
    }


    private static void migrateLegacyDynamicDialogs(BootstrapContext context, Path data) throws IOException {
        Path pluginDirectory = data.getParent();
        if (pluginDirectory == null) return;

        Path legacy = pluginDirectory.resolve("DynamicDialogs");
        if (!Files.isDirectory(legacy) || legacy.equals(data)) return;

        // If Modexa already has a config, obey its migration switch. Before the
        // first copy there is no Modexa config yet, so migration defaults to true.
        boolean enabled = true;
        Path currentConfig = data.resolve("config.yml");
        if (Files.isRegularFile(currentConfig)) {
            YamlConfiguration current = YamlConfiguration.loadConfiguration(currentConfig.toFile());
            enabled = current.getBoolean("migration.import-dynamicdialogs", true);
        }
        if (!enabled) return;

        int copied = 0;
        copied += copyLegacyFileIfMissing(legacy.resolve("config.yml"), data.resolve("config.yml"));
        copied += copyLegacyDirectoryIfMissing(legacy.resolve("dialogs"), data.resolve("dialogs"));
        copied += copyLegacyDirectoryIfMissing(legacy.resolve("deleted"), data.resolve("deleted"));

        if (copied > 0) {
            context.getLogger().info("Migrated {} file(s) from plugins/DynamicDialogs into Modexa. The legacy folder was left untouched.", copied);
        }
    }

    private static int copyLegacyDirectoryIfMissing(Path source, Path target) throws IOException {
        if (!Files.isDirectory(source)) return 0;
        final int[] count = {0};
        try (var paths = Files.walk(source)) {
            for (Path sourcePath : paths.toList()) {
                Path relative = source.relativize(sourcePath);
                Path targetPath = target.resolve(relative);
                if (Files.isDirectory(sourcePath)) {
                    Files.createDirectories(targetPath);
                } else {
                    count[0] += copyLegacyFileIfMissing(sourcePath, targetPath);
                }
            }
        }
        return count[0];
    }

    private static int copyLegacyFileIfMissing(Path source, Path target) throws IOException {
        if (!Files.isRegularFile(source) || Files.exists(target)) return 0;
        Path parent = target.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
        return 1;
    }

    private static PauseMirror parseMirror(YamlConfiguration menu, BootstrapContext context) {
        if (!menu.getBoolean("enabled", true)) {
            throw new IllegalArgumentException("the configured main menu is disabled");
        }

        String titleText = menu.getString("title", "<white>Server Menu");
        String externalText = menu.getString("external-title", titleText);
        Component title = MINI.deserialize(titleText == null ? "<white>Server Menu" : titleText);
        Component externalTitle = MINI.deserialize(externalText == null ? "<white>Server Menu" : externalText);

        DialogBase.Builder base = DialogBase.builder(title)
                .externalTitle(externalTitle)
                .canCloseWithEscape(menu.getBoolean("can-close-with-escape", true))
                .pause(menu.getBoolean("pause", false))
                .afterAction(parseAfterAction(menu.getString("after-action", "CLOSE")));

        List<DialogBody> bodies = new ArrayList<>();
        for (Map<?, ?> raw : menu.getMapList("body")) {
            String type = string(raw, "type", "text").toLowerCase(Locale.ROOT);
            if (Set.of("text", "message", "plain").contains(type)) {
                String text = string(raw, "text", "");
                int width = clamp(integer(raw, "width", 320), 1, 1024);
                bodies.add(DialogBody.plainMessage(MINI.deserialize(text), width));
            } else {
                context.getLogger().warn("Game-menu bootstrap mirror skips unsupported body type '{}'. The runtime dialog still supports it.", type);
            }
        }
        base.body(bodies);

        if (!menu.getMapList("inputs").isEmpty()) {
            context.getLogger().warn("The pause/game-menu mirror does not render inputs. Keep dialogs/{} as a button menu; inputs still work when opened normally with /modexa open.",
                    menu.getString("id", "main-menu"));
        }

        List<ActionButton> buttons = new ArrayList<>();
        List<Map<?, ?>> configured = menu.getMapList("buttons");
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < configured.size(); i++) {
            Map<?, ?> raw = configured.get(i);
            if (!bool(raw, "show-in-game-menu", true)) continue;

            String id = GameMenuSupport.buttonId(raw, i);
            if (!ids.add(id)) {
                throw new IllegalArgumentException("duplicate game-menu button id '" + id + "'");
            }
            String label = string(raw, "label", "Button");
            String tooltip = string(raw, "tooltip", "");
            int width = clamp(integer(raw, "width", 150), 1, 1024);

            buttons.add(ActionButton.create(
                    MINI.deserialize(label),
                    tooltip.isBlank() ? null : MINI.deserialize(tooltip),
                    width,
                    DialogAction.customClick(Key.key(GameMenuSupport.actionKey(id)), null)
            ));
        }

        if (buttons.isEmpty()) {
            buttons.add(ActionButton.builder(MINI.deserialize("<gray>Close")).width(150).build());
        }

        ActionButton exit = null;
        ConfigurationSection exitSection = menu.getConfigurationSection("exit-button");
        if (exitSection != null && exitSection.getBoolean("enabled", true)) {
            Map<String, Object> raw = exitSection.getValues(false);
            String label = string(raw, "label", "Close");
            String tooltip = string(raw, "tooltip", "");
            int width = clamp(integer(raw, "width", 150), 1, 1024);
            exit = ActionButton.create(
                    MINI.deserialize(label),
                    tooltip.isBlank() ? null : MINI.deserialize(tooltip),
                    width,
                    DialogAction.customClick(Key.key(GameMenuSupport.actionKey("__exit")), null)
            );
        }

        int columns = clamp(menu.getInt("columns", 1), 1, 10);
        return new PauseMirror(base.build(), DialogType.multiAction(buttons, exit, columns), buttons.size());
    }

    private static DialogBase.DialogAfterAction parseAfterAction(String raw) {
        try {
            return DialogBase.DialogAfterAction.valueOf((raw == null ? "CLOSE" : raw)
                    .toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ex) {
            return DialogBase.DialogAfterAction.CLOSE;
        }
    }

    private static void copyBundledIfMissing(String resource, Path target) throws IOException {
        if (Files.exists(target)) return;
        Files.createDirectories(target.getParent());
        try (InputStream input = ModexaBootstrap.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) return;
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
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
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record PauseMirror(DialogBase base, DialogType type, int buttonCount) {
    }
}
