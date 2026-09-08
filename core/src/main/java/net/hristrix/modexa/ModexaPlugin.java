package net.hristrix.modexa;

import net.hristrix.modexa.action.ActionExecutor;
import net.hristrix.modexa.api.ModexaApi;
import net.hristrix.modexa.command.ModexaCommand;
import net.hristrix.modexa.dialog.DialogManager;
import net.hristrix.modexa.dialog.DialogRenderer;
import net.hristrix.modexa.listener.PauseMenuClickListener;
import net.hristrix.modexa.platform.ModexaApiImpl;
import net.hristrix.modexa.util.Texts;
import net.hristrix.modexa.validation.DialogConfigValidator;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class ModexaPlugin extends JavaPlugin {
    private DialogManager dialogManager;
    private DialogRenderer dialogRenderer;
    private ActionExecutor actionExecutor;
    private DialogConfigValidator configValidator;
    private Texts texts;
    private ModexaApiImpl api;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        registerPermissions();

        this.texts = new Texts(this);
        this.dialogManager = new DialogManager(this);
        this.configValidator = new DialogConfigValidator(this);
        this.api = new ModexaApiImpl(this);
        this.actionExecutor = new ActionExecutor(this);
        this.dialogRenderer = new DialogRenderer(this);

        getServer().getServicesManager().register(ModexaApi.class, api, this, ServicePriority.Normal);

        dialogManager.ensureFoldersAndExamples();
        dialogManager.reloadAll();

        ModexaCommand command = new ModexaCommand(this);
        registerCommand("modexa", "Manage the Modexa platform.", List.of("mx", "dd"), command);
        getServer().getPluginManager().registerEvents(new PauseMenuClickListener(this), this);

        getLogger().info("Modexa platform enabled. API " + api.apiVersion() + ", " + dialogManager.size() + " dialog(s) loaded.");
        if (getConfig().getBoolean("game-menu.enabled", true)) {
            getLogger().info("Game-menu integration enabled. Registry-backed layout changes still require a server restart.");
        }
    }

    @Override
    public void onDisable() {
        if (api != null) api.unregisterOwner(this);
        getServer().getServicesManager().unregisterAll(this);
    }

    private void registerPermissions() {
        ensurePermission("modexa.admin", "Full administration access.", PermissionDefault.OP);
        ensurePermission("modexa.open", "Allows opening dialogs through the command.", PermissionDefault.TRUE);
        ensurePermission("modexa.reload", "Allows reloading dialog files.", PermissionDefault.OP);
        ensurePermission("modexa.create", "Allows creating dialog YAML templates.", PermissionDefault.OP);
        ensurePermission("modexa.delete", "Allows deleting/archiving dialog YAML files.", PermissionDefault.OP);
    }

    private void ensurePermission(String node, String description, PermissionDefault defaultValue) {
        if (getServer().getPluginManager().getPermission(node) != null) return;
        getServer().getPluginManager().addPermission(new Permission(node, description, defaultValue));
    }

    public DialogManager dialogs() { return dialogManager; }
    public DialogRenderer renderer() { return dialogRenderer; }
    public ActionExecutor actions() { return actionExecutor; }
    public DialogConfigValidator validator() { return configValidator; }
    public Texts texts() { return texts; }
    public ModexaApiImpl api() { return api; }
    public boolean debug() { return getConfig().getBoolean("debug", false); }
}
