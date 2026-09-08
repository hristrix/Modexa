package net.hristrix.modexa.api;

import org.bukkit.entity.Player;

import java.util.Map;

public record PlaceholderContext(Player player, Map<String, Object> inputs, ModexaApi api) {}
