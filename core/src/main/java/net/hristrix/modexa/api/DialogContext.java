package net.hristrix.modexa.api;

import org.bukkit.entity.Player;

public record DialogContext(Player player, ModexaApi api, String requestedId) {}
