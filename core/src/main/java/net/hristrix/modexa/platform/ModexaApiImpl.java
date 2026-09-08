package net.hristrix.modexa.platform;

import net.hristrix.modexa.ModexaPlugin;
import net.hristrix.modexa.api.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public final class ModexaApiImpl implements ModexaApi {
    private final ModexaPlugin plugin;
    private final DialogService dialogService;
    private final Map<String, Owned<ActionHandler>> actions = new LinkedHashMap<>();
    private final Map<String, Owned<PlaceholderResolver>> placeholders = new LinkedHashMap<>();
    private final Map<String, Owned<ConditionHandler>> conditions = new LinkedHashMap<>();
    private final Map<String, Owned<ModuleInfo>> modules = new LinkedHashMap<>();
    private final List<Owned<Runnable>> reloadHooks = new ArrayList<>();

    public ModexaApiImpl(ModexaPlugin plugin) {
        this.plugin = plugin;
        this.dialogService = new CoreDialogService(plugin);
    }

    @Override public String apiVersion() { return "3.0"; }
    @Override public DialogService dialogs() { return dialogService; }

    @Override
    public synchronized void registerAction(Plugin owner, String type, ActionHandler handler) {
        register(actions, owner, normalizeType(type), handler, "action");
    }

    @Override
    public synchronized void registerPlaceholder(Plugin owner, String key, PlaceholderResolver resolver) {
        String normalized = normalizeToken(key);
        if (!normalized.contains(":")) throw new IllegalArgumentException("Module placeholders must be namespaced, e.g. homes:count");
        register(placeholders, owner, normalized, resolver, "placeholder");
    }

    @Override
    public synchronized void registerCondition(Plugin owner, String type, ConditionHandler handler) {
        register(conditions, owner, normalizeType(type), handler, "condition");
    }

    @Override
    public synchronized void registerModule(Plugin owner, ModuleInfo module) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(module, "module");
        String id = normalizeToken(module.id());
        Owned<ModuleInfo> existing = modules.get(id);
        if (existing != null && existing.owner() != owner) {
            throw new IllegalStateException("Module id '" + id + "' is already owned by " + existing.owner().getName());
        }
        modules.put(id, new Owned<>(owner, module));
    }

    @Override
    public synchronized void registerReloadHook(Plugin owner, Runnable hook) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(hook, "hook");
        reloadHooks.add(new Owned<>(owner, hook));
    }

    @Override
    public synchronized void unregisterOwner(Plugin owner) {
        dialogService.unregisterOwner(owner);
        actions.entrySet().removeIf(e -> e.getValue().owner() == owner);
        placeholders.entrySet().removeIf(e -> e.getValue().owner() == owner);
        conditions.entrySet().removeIf(e -> e.getValue().owner() == owner);
        modules.entrySet().removeIf(e -> e.getValue().owner() == owner);
        reloadHooks.removeIf(e -> e.owner() == owner);
    }

    public synchronized Optional<ActionHandler> action(String type) {
        Owned<ActionHandler> owned = actions.get(normalizeType(type));
        return owned == null ? Optional.empty() : Optional.of(owned.value());
    }

    public synchronized Optional<ConditionHandler> condition(String type) {
        Owned<ConditionHandler> owned = conditions.get(normalizeType(type));
        return owned == null ? Optional.empty() : Optional.of(owned.value());
    }

    public synchronized String resolvePlaceholders(Player player, String text, Map<String, Object> inputs, boolean escapeMiniMessage) {
        String out = text;
        for (Map.Entry<String, Owned<PlaceholderResolver>> entry : placeholders.entrySet()) {
            String token = "{" + entry.getKey() + "}";
            if (!out.contains(token)) continue;
            String value;
            try {
                value = entry.getValue().value().resolve(new PlaceholderContext(player, inputs, this));
            } catch (Exception ex) {
                plugin.getLogger().warning("Placeholder '" + entry.getKey() + "' from " + entry.getValue().owner().getName()
                        + " failed: " + ex.getMessage());
                value = "";
            }
            if (value == null) value = "";
            if (escapeMiniMessage) value = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().escapeTags(value);
            out = out.replace(token, value);
        }
        return out;
    }


    public void reloadExtensions() {
        List<Owned<Runnable>> snapshot;
        synchronized (this) { snapshot = List.copyOf(reloadHooks); }
        for (Owned<Runnable> hook : snapshot) {
            try { hook.value().run(); }
            catch (Exception ex) { plugin.getLogger().warning("Reload hook from " + hook.owner().getName() + " failed: " + ex.getMessage()); }
        }
    }

    public synchronized Collection<ModuleInfo> moduleInfos() {
        return modules.values().stream().map(Owned::value).toList();
    }

    private static <T> void register(Map<String, Owned<T>> map, Plugin owner, String key, T value, String kind) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(value, kind);
        if (key.isBlank()) throw new IllegalArgumentException(kind + " id cannot be blank");
        Owned<T> existing = map.get(key);
        if (existing != null && existing.owner() != owner) {
            throw new IllegalStateException("The " + kind + " '" + key + "' is already owned by " + existing.owner().getName());
        }
        map.put(key, new Owned<>(owner, value));
    }

    private static String normalizeType(String type) {
        return normalizeToken(type).replace('_', '-');
    }

    private static String normalizeToken(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    private record Owned<T>(Plugin owner, T value) {}

    private static final class CoreDialogService implements DialogService {
        private final ModexaPlugin plugin;
        private CoreDialogService(ModexaPlugin plugin) { this.plugin = plugin; }

        @Override public boolean show(Player player, String id) { return plugin.renderer().show(player, id); }
        @Override public void register(Plugin owner, DialogSpec spec) { plugin.dialogs().register(owner, spec); }
        @Override public void registerProvider(Plugin owner, String id, DialogProvider provider) { plugin.dialogs().registerProvider(owner, id, provider); }
        @Override public void registerDirectory(Plugin owner, Path directory, String namespace) throws IOException {
            plugin.dialogs().registerDirectory(owner, directory, namespace);
        }
        @Override public void unregisterOwner(Plugin owner) { plugin.dialogs().unregisterOwner(owner); }
        @Override public Set<String> ids() { return plugin.dialogs().ids(); }
    }
}
