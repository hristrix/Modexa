package net.hristrix.modexa.modules.homes;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HomeManager {
    private static final Pattern VALID_NAME = Pattern.compile("^[\\p{L}\\p{N}_ -]+$");
    private static final Pattern HOME_SLOT = Pattern.compile("^Home\\s+(\\d+)$", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter BACKUP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private final HomesModulePlugin plugin;
    private final File file;
    private final File backupDirectory;
    private final Map<UUID, LinkedHashMap<String, HomeRecord>> homes = new LinkedHashMap<>();

    private YamlConfiguration yaml = new YamlConfiguration();
    private boolean storageWriteSafe = true;

    public HomeManager(HomesModulePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "homes.yml");
        this.backupDirectory = new File(plugin.getDataFolder(), "backups");
        importLegacyFileIfNeeded();
        load();
        createStartupBackup();
    }

    private void importLegacyFileIfNeeded() {
        if (file.exists()) return;

        File plugins = plugin.getDataFolder().getParentFile();
        if (plugins == null) return;

        if (plugin.getConfig().getBoolean("auto-import-legacy-modexa-homes", true)) {
            File previousModular = new File(plugins, "DynamicDialogsHomes/homes.yml");
            if (copyLegacyHomes(previousModular, "DynamicDialogsHomes")) return;
        }

        if (plugin.getConfig().getBoolean("auto-import-legacy-dialoghomes", true)) {
            File original = new File(plugins, "DialogHomes/homes.yml");
            copyLegacyHomes(original, "DialogHomes");
        }
    }

    private boolean copyLegacyHomes(File legacy, String sourceName) {
        if (!legacy.isFile() || file.exists()) return false;
        try {
            File parent = file.getParentFile();
            if (parent != null) Files.createDirectories(parent.toPath());
            Files.copy(legacy.toPath(), file.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
            plugin.getLogger().info("Copied legacy " + sourceName + "/homes.yml into ModexaHomes. The original file was NOT modified.");
            return true;
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not safely copy legacy " + sourceName + "/homes.yml", ex);
            return false;
        }
    }

    /**
     * Loads the legacy-compatible format exactly as supplied:
     *
     * homes:
     *   <player-uuid>:
     *     <base64-slot-key>:
     *       slot: Home 1
     *       name: My Home
     *       world: world
     *       x: ...
     *       y: ...
     *       z: ...
     *       yaw: ...
     *       pitch: ...
     *
     * The base64 slot key is the stable identity of the home. The visible
     * 'name' is deliberately NOT used as a key because duplicate names are
     * valid in existing data.
     */
    public synchronized void load() {
        homes.clear();
        storageWriteSafe = true;
        yaml = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();

        ConfigurationSection players = yaml.getConfigurationSection("homes");
        if (players == null) {
            if (file.exists() && file.length() > 0L) {
                storageWriteSafe = false;
                plugin.getLogger().severe(
                        "Existing homes.yml does not contain the expected 'homes:' root. " +
                                "CRUD writes are disabled to prevent accidental data loss."
                );
            }
            return;
        }

        int loadedPlayers = 0;
        int loadedHomes = 0;

        for (String uuidText : players.getKeys(false)) {
            UUID playerId;
            try {
                playerId = UUID.fromString(uuidText);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Leaving invalid UUID section untouched in homes.yml: " + uuidText);
                continue;
            }

            ConfigurationSection playerSection = players.getConfigurationSection(uuidText);
            if (playerSection == null) {
                continue;
            }

            LinkedHashMap<String, HomeRecord> playerHomes = new LinkedHashMap<>();
            for (String storageKey : playerSection.getKeys(false)) {
                ConfigurationSection section = playerSection.getConfigurationSection(storageKey);
                if (section == null) {
                    continue;
                }

                String slot = section.getString("slot");
                if (slot == null || slot.isBlank()) {
                    slot = decodeSlot(storageKey).orElse(storageKey);
                }

                String name = section.getString("name", slot);
                String worldName = section.getString("world");
                if (worldName == null || worldName.isBlank()) {
                    plugin.getLogger().warning(
                            "Home '" + name + "' for " + playerId + " has no world. " +
                                    "It will be kept untouched in homes.yml but cannot be used until fixed."
                    );
                    continue;
                }

                HomeRecord home = new HomeRecord(
                        storageKey,
                        slot,
                        name,
                        worldName,
                        section.getDouble("x"),
                        section.getDouble("y"),
                        section.getDouble("z"),
                        (float) section.getDouble("yaw"),
                        (float) section.getDouble("pitch")
                );
                playerHomes.put(storageKey, home);
                loadedHomes++;
            }

            if (!playerHomes.isEmpty()) {
                homes.put(playerId, playerHomes);
                loadedPlayers++;
            }
        }

        plugin.getLogger().info("Loaded " + loadedHomes + " existing homes for " + loadedPlayers + " players from homes.yml.");
    }

    /**
     * Saves the current YAML through a temporary file and then replaces
     * homes.yml. Unknown/unreadable sections remain in the YamlConfiguration
     * and therefore are not intentionally discarded by CRUD operations.
     */
    public synchronized boolean save() {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().severe("Could not create plugin data directory: " + parent);
            return false;
        }

        File temp = new File(parent, file.getName() + ".tmp");
        try {
            yaml.save(temp);
            try {
                Files.move(
                        temp.toPath(),
                        file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not safely save homes.yml", ex);
            if (temp.exists() && !temp.delete()) {
                temp.deleteOnExit();
            }
            return false;
        }
    }

    public synchronized List<HomeRecord> getHomes(UUID playerId) {
        LinkedHashMap<String, HomeRecord> playerHomes = homes.get(playerId);
        if (playerHomes == null) {
            return List.of();
        }

        List<HomeRecord> result = new ArrayList<>(playerHomes.values());
        result.sort(Comparator
                .comparingInt((HomeRecord home) -> slotNumber(home.slot()))
                .thenComparing(HomeRecord::slot, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(HomeRecord::name, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(result);
    }

    public synchronized Optional<HomeRecord> getHomeByKey(UUID playerId, String storageKey) {
        LinkedHashMap<String, HomeRecord> playerHomes = homes.get(playerId);
        if (playerHomes == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(playerHomes.get(storageKey));
    }

    public synchronized List<HomeRecord> findHomesByName(UUID playerId, String name) {
        String wanted = normalize(name);
        if (wanted.isEmpty()) {
            return List.of();
        }

        return getHomes(playerId).stream()
                .filter(home -> normalize(home.name()).equals(wanted))
                .toList();
    }

    public synchronized Optional<HomeRecord> getUniqueHomeByName(UUID playerId, String name) {
        List<HomeRecord> matches = findHomesByName(playerId, name);
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    public synchronized int count(UUID playerId) {
        ConfigurationSection rawPlayerSection = yaml.getConfigurationSection("homes." + playerId);
        if (rawPlayerSection != null) {
            // Count every stored slot, including one we cannot currently parse.
            // This prevents an unreadable legacy record from being overwritten.
            return rawPlayerSection.getKeys(false).size();
        }
        return 0;
    }

    public synchronized OperationResult create(Player player, String name) {
        String validation = validateName(name);
        if (validation != null) {
            return OperationResult.failure(validation);
        }
        if (!storageWriteSafe) {
            return OperationResult.failure("homes.yml is in safety read-only mode. Check the server console before changing homes.");
        }

        UUID playerId = player.getUniqueId();
        int limit = Math.max(1, plugin.getConfig().getInt("max-homes", 5));
        if (!player.hasPermission("dynamichomes.bypass-limit") && count(playerId) >= limit) {
            return OperationResult.failure("You have reached your home limit of " + limit + ".");
        }

        LinkedHashMap<String, HomeRecord> playerHomes = homes.computeIfAbsent(playerId, ignored -> new LinkedHashMap<>());
        String slot = findNextFreeSlot(playerId, playerHomes);
        String storageKey = encodeSlot(slot);
        String cleanName = cleanName(name);
        HomeRecord record = HomeRecord.fromLocation(storageKey, slot, cleanName, player.getLocation());

        playerHomes.put(storageKey, record);
        writeHome(playerId, record);

        if (!save()) {
            load();
            return OperationResult.failure("The home could not be saved safely. Your existing homes were left unchanged.");
        }

        return OperationResult.success("Home '" + cleanName + "' created in " + slot + ".");
    }

    /** Rename changes ONLY the visible 'name' field. The legacy storage key and slot stay unchanged. */
    public synchronized OperationResult rename(UUID playerId, String storageKey, String newName) {
        String validation = validateName(newName);
        if (validation != null) {
            return OperationResult.failure(validation);
        }
        if (!storageWriteSafe) {
            return OperationResult.failure("homes.yml is in safety read-only mode. Check the server console before changing homes.");
        }

        LinkedHashMap<String, HomeRecord> playerHomes = homes.get(playerId);
        if (playerHomes == null) {
            return OperationResult.failure("Home not found.");
        }

        HomeRecord current = playerHomes.get(storageKey);
        if (current == null) {
            return OperationResult.failure("Home not found.");
        }

        HomeRecord renamed = current.withName(cleanName(newName));
        playerHomes.put(storageKey, renamed);
        yaml.set(path(playerId, storageKey) + ".name", renamed.name());

        if (!save()) {
            load();
            return OperationResult.failure("The rename could not be saved safely. The previous home data was restored.");
        }

        return OperationResult.success("Home renamed to '" + renamed.name() + "'.");
    }

    /** Update changes ONLY world/coordinates/yaw/pitch. It never changes slot, key, or visible name. */
    public synchronized OperationResult updateLocation(Player player, String storageKey) {
        if (!storageWriteSafe) {
            return OperationResult.failure("homes.yml is in safety read-only mode. Check the server console before changing homes.");
        }
        UUID playerId = player.getUniqueId();
        LinkedHashMap<String, HomeRecord> playerHomes = homes.get(playerId);
        if (playerHomes == null) {
            return OperationResult.failure("Home not found.");
        }

        HomeRecord current = playerHomes.get(storageKey);
        if (current == null) {
            return OperationResult.failure("Home not found.");
        }

        HomeRecord updated = current.withLocation(player.getLocation());
        playerHomes.put(storageKey, updated);
        writeHome(playerId, updated);

        if (!save()) {
            load();
            return OperationResult.failure("The new location could not be saved safely. The previous home data was restored.");
        }

        return OperationResult.success("Home '" + current.name() + "' moved to your current location.");
    }

    public synchronized OperationResult delete(UUID playerId, String storageKey) {
        if (!storageWriteSafe) {
            return OperationResult.failure("homes.yml is in safety read-only mode. Check the server console before changing homes.");
        }
        LinkedHashMap<String, HomeRecord> playerHomes = homes.get(playerId);
        if (playerHomes == null) {
            return OperationResult.failure("Home not found.");
        }

        HomeRecord removed = playerHomes.remove(storageKey);
        if (removed == null) {
            return OperationResult.failure("Home not found.");
        }

        String path = path(playerId, storageKey);
        yaml.set(path, null);

        if (!save()) {
            // Reloading from disk restores the old record because the failed temp save never replaced homes.yml.
            load();
            return OperationResult.failure("The home could not be deleted safely. Your previous data was restored.");
        }

        if (playerHomes.isEmpty()) {
            homes.remove(playerId);
        }

        return OperationResult.success("Home '" + removed.name() + "' deleted.");
    }

    public synchronized OperationResult renameByName(UUID playerId, String oldName, String newName) {
        ResolveResult resolved = resolveUniqueByName(playerId, oldName);
        if (!resolved.success()) {
            return OperationResult.failure(resolved.message());
        }
        return rename(playerId, resolved.home().storageKey(), newName);
    }

    public synchronized OperationResult updateLocationByName(Player player, String name) {
        ResolveResult resolved = resolveUniqueByName(player.getUniqueId(), name);
        if (!resolved.success()) {
            return OperationResult.failure(resolved.message());
        }
        return updateLocation(player, resolved.home().storageKey());
    }

    public synchronized OperationResult deleteByName(UUID playerId, String name) {
        ResolveResult resolved = resolveUniqueByName(playerId, name);
        if (!resolved.success()) {
            return OperationResult.failure(resolved.message());
        }
        return delete(playerId, resolved.home().storageKey());
    }

    public synchronized ResolveResult resolveUniqueByName(UUID playerId, String name) {
        List<HomeRecord> matches = findHomesByName(playerId, name);
        if (matches.isEmpty()) {
            return ResolveResult.failure("Home '" + name + "' was not found.");
        }
        if (matches.size() > 1) {
            return ResolveResult.failure(
                    "More than one home is named '" + name + "'. Use /homes and select the exact slot you want."
            );
        }
        return ResolveResult.success(matches.getFirst());
    }

    public String validateName(String name) {
        if (name == null) {
            return "Please enter a home name.";
        }

        String cleaned = cleanName(name);
        int maxLength = plugin.getConfig().getInt("name-max-length", 24);
        if (cleaned.isEmpty()) {
            return "Home name cannot be empty.";
        }
        if (cleaned.length() > maxLength) {
            return "Home name cannot be longer than " + maxLength + " characters.";
        }
        if (!VALID_NAME.matcher(cleaned).matches()) {
            return "Use only letters, numbers, spaces, underscores and hyphens.";
        }
        return null;
    }

    public static String normalize(String name) {
        return Normalizer.normalize(cleanName(name), Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
    }

    private static String cleanName(String name) {
        if (name == null) {
            return "";
        }
        return Normalizer.normalize(name.trim().replaceAll("\\s+", " "), Normalizer.Form.NFC);
    }

    private void writeHome(UUID playerId, HomeRecord home) {
        String base = path(playerId, home.storageKey());
        yaml.set(base + ".slot", home.slot());
        yaml.set(base + ".name", home.name());
        yaml.set(base + ".world", home.worldName());
        yaml.set(base + ".x", home.x());
        yaml.set(base + ".y", home.y());
        yaml.set(base + ".z", home.z());
        yaml.set(base + ".yaw", home.yaw());
        yaml.set(base + ".pitch", home.pitch());
    }

    private String findNextFreeSlot(UUID playerId, Map<String, HomeRecord> playerHomes) {
        ConfigurationSection rawPlayerSection = yaml.getConfigurationSection("homes." + playerId);

        for (int number = 1; number < Integer.MAX_VALUE; number++) {
            String slot = "Home " + number;
            String storageKey = encodeSlot(slot);

            boolean keyUsedInMemory = playerHomes.containsKey(storageKey);
            boolean slotUsedInMemory = playerHomes.values().stream()
                    .anyMatch(home -> home.slot().equalsIgnoreCase(slot));

            boolean keyUsedInYaml = rawPlayerSection != null && rawPlayerSection.contains(storageKey);
            boolean slotUsedInYaml = false;
            if (rawPlayerSection != null) {
                for (String existingKey : rawPlayerSection.getKeys(false)) {
                    ConfigurationSection existing = rawPlayerSection.getConfigurationSection(existingKey);
                    if (existing != null && slot.equalsIgnoreCase(existing.getString("slot", ""))) {
                        slotUsedInYaml = true;
                        break;
                    }
                }
            }

            if (!keyUsedInMemory && !slotUsedInMemory && !keyUsedInYaml && !slotUsedInYaml) {
                return slot;
            }
        }
        throw new IllegalStateException("Could not allocate a free home slot.");
    }

    private static String encodeSlot(String slot) {
        return Base64.getEncoder().encodeToString(slot.getBytes(StandardCharsets.UTF_8));
    }

    private static Optional<String> decodeSlot(String storageKey) {
        try {
            byte[] decoded = Base64.getDecoder().decode(storageKey);
            return Optional.of(new String(decoded, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private static int slotNumber(String slot) {
        Matcher matcher = HOME_SLOT.matcher(slot == null ? "" : slot.trim());
        if (!matcher.matches()) {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ex) {
            return Integer.MAX_VALUE;
        }
    }

    private static String path(UUID playerId, String storageKey) {
        return "homes." + playerId + "." + storageKey;
    }

    private void createStartupBackup() {
        if (!file.exists() || !plugin.getConfig().getBoolean("automatic-backups", true)) {
            return;
        }

        try {
            if (!backupDirectory.exists() && !backupDirectory.mkdirs()) {
                plugin.getLogger().warning("Could not create homes backup directory.");
                return;
            }

            String timestamp = LocalDateTime.now().format(BACKUP_FORMAT);
            File backup = new File(backupDirectory, "homes-" + timestamp + ".yml");
            Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
            trimBackups();
            plugin.getLogger().info("Created homes.yml safety backup: " + backup.getName());
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not create startup backup of homes.yml", ex);
        }
    }

    private void trimBackups() {
        int keep = Math.max(1, plugin.getConfig().getInt("backup-count", 10));
        File[] files = backupDirectory.listFiles((dir, name) -> name.startsWith("homes-") && name.endsWith(".yml"));
        if (files == null || files.length <= keep) {
            return;
        }

        List<File> sorted = new ArrayList<>(List.of(files));
        sorted.sort(Comparator.comparingLong(File::lastModified).reversed());
        for (int i = keep; i < sorted.size(); i++) {
            if (!sorted.get(i).delete()) {
                plugin.getLogger().warning("Could not remove old homes backup: " + sorted.get(i).getName());
            }
        }
    }

    public record OperationResult(boolean success, String message) {
        public static OperationResult success(String message) {
            return new OperationResult(true, message);
        }

        public static OperationResult failure(String message) {
            return new OperationResult(false, message);
        }
    }

    public record ResolveResult(boolean success, HomeRecord home, String message) {
        public static ResolveResult success(HomeRecord home) {
            return new ResolveResult(true, home, "");
        }

        public static ResolveResult failure(String message) {
            return new ResolveResult(false, null, message);
        }
    }
}
