package net.hristrix.modexa.dialog;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public record DialogDefinition(String id, File file, YamlConfiguration config, String owner) {
    public boolean fileBacked() { return file != null; }
}
