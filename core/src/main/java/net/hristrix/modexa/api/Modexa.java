package net.hristrix.modexa.api;

import org.bukkit.Bukkit;

/** Safe service lookup for modules. Do not shade the API into a module JAR. */
public final class Modexa {
    private Modexa() {}

    public static ModexaApi get() {
        ModexaApi api = Bukkit.getServicesManager().load(ModexaApi.class);
        if (api == null) {
            throw new IllegalStateException("Modexa is not enabled or its API service is unavailable.");
        }
        return api;
    }
}
