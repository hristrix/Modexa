package net.hristrix.modexa.api;

import org.bukkit.plugin.Plugin;

/** Public developer API exposed by Modexa. */
public interface ModexaApi {
    String apiVersion();
    DialogService dialogs();

    void registerAction(Plugin owner, String type, ActionHandler handler);
    void registerPlaceholder(Plugin owner, String key, PlaceholderResolver resolver);
    void registerCondition(Plugin owner, String type, ConditionHandler handler);
    void registerModule(Plugin owner, ModuleInfo module);
    void registerReloadHook(Plugin owner, Runnable hook);

    /** Removes dialogs, providers, actions, placeholders, conditions and metadata owned by the plugin. */
    void unregisterOwner(Plugin owner);
}
