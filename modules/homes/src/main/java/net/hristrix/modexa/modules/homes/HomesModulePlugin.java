package net.hristrix.modexa.modules.homes;

import net.hristrix.modexa.api.*;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.*;

import static net.hristrix.modexa.api.DialogSpec.*;

public final class HomesModulePlugin extends JavaPlugin {
    private ModexaApi api;
    private HomeManager homes;
    private final Map<UUID, String> selectedHome = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveBundledDialogs();
        registerPermissions();

        this.api = Modexa.get();
        this.homes = new HomeManager(this);

        api.registerModule(this, new ModuleInfo(
                "homes", "Modexa Homes", getPluginMeta().getVersion(),
                "Legacy-safe homes CRUD module built entirely on the Modexa API."));

        registerExtensions();
        registerDialogs();
        api.registerReloadHook(this, this::reloadModule);

        registerCommand("homes", "Open your homes dialog.", List.of("homemenu"), new HomesCommand(this, HomesCommand.Mode.HOMES));
        registerCommand("home", "Teleport to a named home.", List.of(), new HomesCommand(this, HomesCommand.Mode.HOME));
        registerCommand("sethome", "Create a home.", List.of(), new HomesCommand(this, HomesCommand.Mode.SETHOME));
        registerCommand("delhome", "Delete a named home.", List.of("deletehome"), new HomesCommand(this, HomesCommand.Mode.DELHOME));
        registerCommand("renamehome", "Rename a named home.", List.of(), new HomesCommand(this, HomesCommand.Mode.RENAMEHOME));
        registerCommand("updatehome", "Move a named home to your current location.", List.of(), new HomesCommand(this, HomesCommand.Mode.UPDATEHOME));

        getLogger().info("Modexa Homes enabled with legacy-safe homes.yml compatibility.");
    }

    @Override
    public void onDisable() {
        if (api != null) api.unregisterOwner(this);
        selectedHome.clear();
    }

    private void reloadModule() {
        reloadConfig();
        homes.load();
        api.dialogs().unregisterOwner(this);
        registerDialogs();
        getLogger().info("Homes module configuration and dialog YAML reloaded.");
    }

    private void registerExtensions() {
        api.registerPlaceholder(this, "homes:count", ctx -> Integer.toString(homes.count(ctx.player().getUniqueId())));
        api.registerPlaceholder(this, "homes:max", ctx -> ctx.player().hasPermission("dynamichomes.bypass-limit")
                ? "∞" : Integer.toString(Math.max(1, getConfig().getInt("max-homes", 5))));
        api.registerPlaceholder(this, "homes:selected-name", ctx -> selected(ctx.player()).map(HomeRecord::name).orElse("Unknown"));
        api.registerPlaceholder(this, "homes:selected-slot", ctx -> selected(ctx.player()).map(HomeRecord::slot).orElse("Unknown"));
        api.registerPlaceholder(this, "homes:selected-world", ctx -> selected(ctx.player()).map(HomeRecord::worldName).orElse("Unknown"));

        api.registerCondition(this, "homes:has-homes", ctx -> homes.count(ctx.player().getUniqueId()) > 0);
        api.registerCondition(this, "homes:has-selection", ctx -> selected(ctx.player()).isPresent());

        api.registerAction(this, "homes:select", ctx -> {
            String key = ctx.option("key", "");
            if (homes.getHomeByKey(ctx.player().getUniqueId(), key).isEmpty()) {
                ctx.player().sendMessage(Component.text("That home no longer exists."));
                openLater(ctx.player(), "homes:main");
                return;
            }
            selectedHome.put(ctx.player().getUniqueId(), key);
            openLater(ctx.player(), "homes:detail");
        });

        api.registerAction(this, "homes:create", ctx -> {
            HomeManager.OperationResult result = homes.create(ctx.player(), ctx.input("name"));
            sendResult(ctx.player(), result);
            if (result.success()) openLater(ctx.player(), "homes:main");
            else openLater(ctx.player(), "homes:create");
        });

        api.registerAction(this, "homes:rename", ctx -> {
            HomeRecord selected = selected(ctx.player()).orElse(null);
            if (selected == null) { noSelection(ctx.player()); return; }
            HomeManager.OperationResult result = homes.rename(ctx.player().getUniqueId(), selected.storageKey(), ctx.input("name"));
            sendResult(ctx.player(), result);
            openLater(ctx.player(), result.success() ? "homes:detail" : "homes:rename");
        });

        api.registerAction(this, "homes:teleport", ctx -> {
            HomeRecord selected = selected(ctx.player()).orElse(null);
            if (selected == null) { noSelection(ctx.player()); return; }
            Location location = selected.toLocation();
            if (location == null) {
                ctx.player().sendMessage(Component.text("World '" + selected.worldName() + "' is not currently loaded."));
                return;
            }
            ctx.player().closeDialog();
            ctx.player().teleportAsync(location).thenAccept(success -> {
                if (!success) ctx.player().sendMessage(Component.text("Teleport failed."));
            });
        });

        api.registerAction(this, "homes:update", ctx -> {
            HomeRecord selected = selected(ctx.player()).orElse(null);
            if (selected == null) { noSelection(ctx.player()); return; }
            HomeManager.OperationResult result = homes.updateLocation(ctx.player(), selected.storageKey());
            sendResult(ctx.player(), result);
            openLater(ctx.player(), "homes:detail");
        });

        api.registerAction(this, "homes:delete", ctx -> {
            HomeRecord selected = selected(ctx.player()).orElse(null);
            if (selected == null) { noSelection(ctx.player()); return; }
            HomeManager.OperationResult result = homes.delete(ctx.player().getUniqueId(), selected.storageKey());
            sendResult(ctx.player(), result);
            if (result.success()) selectedHome.remove(ctx.player().getUniqueId());
            openLater(ctx.player(), "homes:main");
        });
    }

    private void registerDialogs() {
        try {
            api.dialogs().registerDirectory(this, getDataFolder().toPath().resolve("dialogs"), "homes");
        } catch (IOException ex) {
            getLogger().severe("Could not load Homes module dialog YAML: " + ex.getMessage());
        }
        api.dialogs().registerProvider(this, "homes:main", this::buildMainDialog);
        api.dialogs().registerProvider(this, "homes:detail", this::buildDetailDialog);
    }

    private DialogSpec buildMainDialog(DialogContext context) {
        Player player = context.player();
        List<HomeRecord> playerHomes = homes.getHomes(player.getUniqueId());
        List<Map<String, Object>> buttons = new ArrayList<>();

        for (HomeRecord home : playerHomes) {
            buttons.add(map(
                    "id", "slot-" + home.slot().toLowerCase(Locale.ROOT).replace(' ', '-'),
                    "label", "<green><bold>" + home.name() + "</bold></green>",
                    "tooltip", "<gray>" + home.slot() + " • " + home.worldName() + " • "
                            + fmt(home.x()) + ", " + fmt(home.y()) + ", " + fmt(home.z()),
                    "width", getConfig().getInt("ui.home-button-width", 150),
                    "actions", list(action("homes:select", "key", home.storageKey()))
            ));
        }

        buttons.add(map(
                "id", "create",
                "label", "<aqua><bold>+ Set New Home</bold></aqua>",
                "tooltip", "<gray>Save your current location",
                "width", getConfig().getInt("ui.home-button-width", 150),
                "permission", "dynamichomes.create",
                "actions", list(action("open-dialog", "dialog", "homes:create"))
        ));

        return DialogSpec.builder("homes:main")
                .title(getConfig().getString("ui.main-title", "<gradient:#7c3aed:#c084fc><bold>Your Homes</bold></gradient>"))
                .externalTitle(getConfig().getString("ui.external-title", "<light_purple>Homes</light_purple>"))
                .columns(Math.max(1, Math.min(10, getConfig().getInt("ui.columns", 3))))
                .permission("dynamichomes.use")
                .addBody(map("type", "text", "text", "<gray>Homes: <white>{homes:count}</white>/<white>{homes:max}</white>", "width", 320))
                .buttons(buttons)
                .property("exit-button", map("enabled", true, "label", "<red>Close", "actions", list(action("close-dialog"))))
                .build();
    }

    private DialogSpec buildDetailDialog(DialogContext context) {
        HomeRecord home = selected(context.player()).orElse(null);
        if (home == null) return buildMainDialog(context);

        return DialogSpec.builder("homes:detail")
                .title("<gradient:#7c3aed:#c084fc><bold>" + home.name() + "</bold></gradient>")
                .columns(2)
                .addBody(map("type", "text", "text",
                        "<gray>Slot: <white>" + home.slot() + "</white>\n<gray>World: <white>" + home.worldName()
                                + "</white>\n<gray>Location: <white>" + fmt(home.x()) + ", " + fmt(home.y()) + ", " + fmt(home.z()) + "</white>",
                        "width", 320))
                .addButton(button("teleport", "<green><bold>Teleport</bold></green>", list(action("homes:teleport"))))
                .addButton(button("rename", "<yellow>Rename</yellow>", list(action("open-dialog", "dialog", "homes:rename"))))
                .addButton(button("update", "<aqua>Update Location</aqua>", list(action("homes:update"))))
                .addButton(button("delete", "<red>Delete</red>", list(action("open-dialog", "dialog", "homes:delete-confirm"))))
                .property("exit-button", map("enabled", true, "label", "<gray>Back", "actions", list(action("open-dialog", "dialog", "homes:main"))))
                .build();
    }

    Optional<HomeRecord> selected(Player player) {
        String key = selectedHome.get(player.getUniqueId());
        return key == null ? Optional.empty() : homes.getHomeByKey(player.getUniqueId(), key);
    }

    HomeManager homes() { return homes; }
    ModexaApi api() { return api; }

    void select(Player player, String key) { selectedHome.put(player.getUniqueId(), key); }

    void openLater(Player player, String id) {
        Bukkit.getScheduler().runTask(this, () -> {
            if (player.isOnline()) api.dialogs().show(player, id);
        });
    }

    private void noSelection(Player player) {
        player.sendMessage(Component.text("No home is selected."));
        openLater(player, "homes:main");
    }

    private void sendResult(Player player, HomeManager.OperationResult result) {
        player.sendMessage(Component.text(result.message()));
    }

    private void saveBundledDialogs() {
        for (String resource : List.of("dialogs/create.yml", "dialogs/rename.yml", "dialogs/delete-confirm.yml")) {
            if (!getDataFolder().toPath().resolve(resource).toFile().exists()) saveResource(resource, false);
        }
    }

    private void registerPermissions() {
        ensurePermission("dynamichomes.use", "Open the Homes module.", PermissionDefault.TRUE);
        ensurePermission("dynamichomes.create", "Create homes.", PermissionDefault.TRUE);
        ensurePermission("dynamichomes.teleport", "Teleport to homes.", PermissionDefault.TRUE);
        ensurePermission("dynamichomes.rename", "Rename homes.", PermissionDefault.TRUE);
        ensurePermission("dynamichomes.update", "Update home locations.", PermissionDefault.TRUE);
        ensurePermission("dynamichomes.delete", "Delete homes.", PermissionDefault.TRUE);
        ensurePermission("dynamichomes.bypass-limit", "Bypass maximum home count.", PermissionDefault.OP);
    }

    private void ensurePermission(String node, String description, PermissionDefault defaultValue) {
        if (getServer().getPluginManager().getPermission(node) == null) {
            getServer().getPluginManager().addPermission(new Permission(node, description, defaultValue));
        }
    }

    private static String fmt(double n) { return String.format(Locale.ROOT, "%.1f", n); }
}
