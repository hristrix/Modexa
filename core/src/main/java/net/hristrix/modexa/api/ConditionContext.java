package net.hristrix.modexa.api;

import org.bukkit.entity.Player;

import java.util.Map;

public record ConditionContext(
        Player player,
        Map<String, Object> inputs,
        Map<String, Object> condition,
        ModexaApi api
) {}
