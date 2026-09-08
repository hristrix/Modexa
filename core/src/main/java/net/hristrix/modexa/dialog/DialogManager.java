package net.hristrix.modexa.dialog;

import net.hristrix.modexa.ModexaPlugin;
import net.hristrix.modexa.api.DialogContext;
import net.hristrix.modexa.api.DialogProvider;
import net.hristrix.modexa.api.DialogSpec;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

public final class DialogManager {
    private static final Pattern SAFE_ID = Pattern.compile("[a-z0-9][a-z0-9_.-]{0,63}(?::[a-z0-9][a-z0-9_./-]{0,95})?");
    private static final Pattern FILE_ID = Pattern.compile("[a-z0-9_-]{1,64}");

    private final ModexaPlugin plugin;
    private final Map<String, DialogDefinition> fileDialogs = new LinkedHashMap<>();
    private final Map<String, RuntimeDialog> runtimeDialogs = new LinkedHashMap<>();
    private final Map<String, RuntimeProvider> providers = new LinkedHashMap<>();
    private final File dialogsFolder;
    private final File deletedFolder;

    public DialogManager(ModexaPlugin plugin) {
        this.plugin = plugin;
        this.dialogsFolder = new File(plugin.getDataFolder(), "dialogs");
        this.deletedFolder = new File(plugin.getDataFolder(), "deleted");
    }

    public void ensureFoldersAndExamples() {
        if (!dialogsFolder.exists() && !dialogsFolder.mkdirs()) plugin.getLogger().warning("Could not create dialogs folder: " + dialogsFolder);
        if (!deletedFolder.exists() && !deletedFolder.mkdirs()) plugin.getLogger().warning("Could not create deleted folder: " + deletedFolder);
        saveExample("dialogs/main-menu.yml");
        if (plugin.getConfig().getBoolean("create-example-dialogs", true)) {
            saveExample("dialogs/example-form.yml");
            saveExample("dialogs/example-confirm.yml");
            saveExample("dialogs/example-navigation.yml");
        }
    }

    private void saveExample(String resource) {
        File out = new File(plugin.getDataFolder(), resource);
        if (!out.exists()) plugin.saveResource(resource, false);
    }

    /** Reloads only Core-owned YAML. Module registrations remain active. */
    public synchronized int reloadAll() {
        fileDialogs.clear();
        File[] files = dialogsFolder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) return 0;
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File file : files) loadFileDialog(file);
        return size();
    }

    private void loadFileDialog(File file) {
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            String fallbackId = file.getName().substring(0, file.getName().length() - 4).toLowerCase(Locale.ROOT);
            String id = normalizeId(yaml.getString("id", fallbackId));
            if (!isSafeId(id)) {
                plugin.getLogger().warning("Skipping " + file.getName() + ": invalid dialog id '" + id + "'.");
                return;
            }
            if (fileDialogs.containsKey(id)) {
                plugin.getLogger().warning("Skipping duplicate dialog id '" + id + "' from " + file.getName());
                return;
            }
            fileDialogs.put(id, new DialogDefinition(id, file, yaml, plugin.getName()));
        } catch (Exception ex) {
            plugin.getLogger().severe("Failed to load dialog file " + file.getName() + ": " + ex.getMessage());
        }
    }

    public synchronized Optional<DialogDefinition> get(String id) {
        String key = normalizeId(id);
        RuntimeDialog runtime = runtimeDialogs.get(key);
        if (runtime != null) return Optional.of(runtime.definition());
        return Optional.ofNullable(fileDialogs.get(key));
    }

    public synchronized Optional<DialogDefinition> resolve(Player player, String id) {
        String key = normalizeId(id);
        RuntimeProvider provider = providers.get(key);
        if (provider != null) {
            try {
                DialogSpec spec = provider.provider().build(new DialogContext(player, plugin.api(), key));
                if (spec == null) return Optional.empty();
                return Optional.of(fromSpec(key, spec, provider.owner().getName()));
            } catch (Exception ex) {
                plugin.getLogger().warning("Dynamic dialog provider '" + key + "' from " + provider.owner().getName()
                        + " failed: " + ex.getMessage());
                if (plugin.debug()) ex.printStackTrace();
                return Optional.empty();
            }
        }
        return get(key);
    }

    public synchronized Collection<DialogDefinition> all() {
        List<DialogDefinition> all = new ArrayList<>(fileDialogs.values());
        runtimeDialogs.values().forEach(r -> all.add(r.definition()));
        return Collections.unmodifiableList(all);
    }

    public synchronized Set<String> ids() {
        LinkedHashSet<String> ids = new LinkedHashSet<>(fileDialogs.keySet());
        ids.addAll(runtimeDialogs.keySet());
        ids.addAll(providers.keySet());
        return Collections.unmodifiableSet(ids);
    }

    public synchronized int size() { return ids().size(); }

    public synchronized void register(Plugin owner, DialogSpec spec) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(spec, "spec");
        String id = normalizeId(spec.id());
        requireSafeId(id);
        assertRuntimeOwnership(owner, id);
        runtimeDialogs.put(id, new RuntimeDialog(owner, fromSpec(id, spec, owner.getName())));
    }

    public synchronized void registerProvider(Plugin owner, String id, DialogProvider provider) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(provider, "provider");
        id = normalizeId(id);
        requireSafeId(id);
        assertRuntimeOwnership(owner, id);
        providers.put(id, new RuntimeProvider(owner, provider));
    }

    public synchronized int registerDirectory(Plugin owner, Path directory, String namespace) throws IOException {
        Objects.requireNonNull(owner, "owner");
        if (!Files.isDirectory(directory)) return 0;
        String ns = normalizeNamespace(namespace);
        int count = 0;
        try (var stream = Files.list(directory)) {
            List<Path> files = stream.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".yml"))
                    .sorted().toList();
            for (Path path : files) {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(path.toFile());
                String fileName = path.getFileName().toString();
                String fallback = fileName.substring(0, fileName.length() - 4).toLowerCase(Locale.ROOT);
                String localId = normalizeId(yaml.getString("id", fallback));
                String fullId = localId.contains(":") || ns.isBlank() ? localId : ns + ":" + localId;
                requireSafeId(fullId);
                assertRuntimeOwnership(owner, fullId);
                yaml.set("id", fullId);
                runtimeDialogs.put(fullId, new RuntimeDialog(owner,
                        new DialogDefinition(fullId, path.toFile(), yaml, owner.getName())));
                count++;
            }
        }
        return count;
    }

    public synchronized void unregisterOwner(Plugin owner) {
        runtimeDialogs.entrySet().removeIf(e -> e.getValue().owner() == owner);
        providers.entrySet().removeIf(e -> e.getValue().owner() == owner);
    }

    private void assertRuntimeOwnership(Plugin owner, String id) {
        RuntimeDialog dialog = runtimeDialogs.get(id);
        if (dialog != null && dialog.owner() != owner) throw conflict(id, dialog.owner());
        RuntimeProvider provider = providers.get(id);
        if (provider != null && provider.owner() != owner) throw conflict(id, provider.owner());
        if (fileDialogs.containsKey(id)) {
            throw new IllegalStateException("Dialog id '" + id + "' is already defined by Modexa YAML.");
        }
    }

    private IllegalStateException conflict(String id, Plugin currentOwner) {
        return new IllegalStateException("Dialog id '" + id + "' is already owned by " + currentOwner.getName());
    }

    private DialogDefinition fromSpec(String id, DialogSpec spec, String ownerName) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, Object> e : spec.values().entrySet()) yaml.set(e.getKey(), e.getValue());
        yaml.set("id", id);
        return new DialogDefinition(id, null, yaml, ownerName);
    }

    public boolean createTemplate(String id) throws IOException {
        id = normalizeId(id);
        if (!FILE_ID.matcher(id).matches()) throw new IllegalArgumentException("File dialog IDs may only contain lowercase letters, numbers, _ and - (max 64 chars).");
        File file = fileFor(id);
        if (file.exists() || ids().contains(id)) return false;
        String template = """
            id: %s
            enabled: true
            title: "<gradient:#7c3aed:#c084fc>%s</gradient>"
            external-title: "<light_purple>%s</light_purple>"
            permission: ""
            can-close-with-escape: true
            pause: false
            after-action: CLOSE
            type: multi_action
            columns: 1
            body:
              - type: text
                text: "<gray>Edit this YAML and run <yellow>/modexa reload</yellow>."
                width: 320
            buttons:
              - id: hello
                label: "<green>Hello"
                actions:
                  - type: message
                    text: "<green>Hello {player}!"
            exit-button:
              label: "<red>Close"
              actions:
                - type: close-dialog
            """.formatted(id, id, id);
        Files.writeString(file.toPath(), template);
        reloadAll();
        return true;
    }

    public boolean cloneDialog(String sourceId, String newId) throws IOException {
        DialogDefinition source = get(sourceId).orElse(null);
        if (source == null || !source.fileBacked()) return false;
        newId = normalizeId(newId);
        if (!FILE_ID.matcher(newId).matches()) throw new IllegalArgumentException("Invalid destination ID.");
        File target = fileFor(newId);
        if (target.exists() || ids().contains(newId)) throw new IllegalStateException("Destination dialog already exists.");
        String content = Files.readString(source.file().toPath());
        content = content.replaceFirst("(?m)^id\\s*:\\s*[^\\r\\n]+", "id: " + newId);
        Files.writeString(target.toPath(), content);
        reloadAll();
        return true;
    }

    public boolean delete(String id) throws IOException {
        DialogDefinition definition = fileDialogs.get(normalizeId(id));
        if (definition == null || !definition.fileBacked()) return false;
        if (plugin.getConfig().getBoolean("safe-delete", true)) {
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            File destination = new File(deletedFolder, definition.id() + "-" + stamp + ".yml");
            Files.move(definition.file().toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } else Files.deleteIfExists(definition.file().toPath());
        reloadAll();
        return true;
    }

    public File fileFor(String id) { return new File(dialogsFolder, normalizeId(id) + ".yml"); }
    public static boolean isSafeId(String id) { return id != null && SAFE_ID.matcher(id).matches(); }

    private static void requireSafeId(String id) {
        if (!isSafeId(id)) throw new IllegalArgumentException("Invalid dialog id '" + id + "'. Use lowercase namespaced IDs such as homes:main.");
    }

    private static String normalizeNamespace(String ns) {
        String out = normalizeId(ns);
        if (out.endsWith(":")) out = out.substring(0, out.length() - 1);
        if (!out.isBlank() && !out.matches("[a-z0-9][a-z0-9_.-]{0,63}")) throw new IllegalArgumentException("Invalid namespace '" + ns + "'.");
        return out;
    }

    private static String normalizeId(String id) { return id == null ? "" : id.toLowerCase(Locale.ROOT).trim(); }

    private record RuntimeDialog(Plugin owner, DialogDefinition definition) {}
    private record RuntimeProvider(Plugin owner, DialogProvider provider) {}
}
