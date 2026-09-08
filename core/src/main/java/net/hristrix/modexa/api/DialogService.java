package net.hristrix.modexa.api;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

public interface DialogService {
    boolean show(Player player, String id);
    void register(Plugin owner, DialogSpec spec);
    void registerProvider(Plugin owner, String id, DialogProvider provider);
    void registerDirectory(Plugin owner, Path directory, String namespace) throws IOException;
    void unregisterOwner(Plugin owner);
    Set<String> ids();
}
