package net.hristrix.modexa.dialog;

import io.papermc.paper.dialog.DialogResponseView;
import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class InputSnapshot {
    private InputSnapshot() {
    }

    public static Map<String, Object> capture(ConfigurationSection dialog, DialogResponseView response) {
        Map<String, Object> values = new LinkedHashMap<>();
        List<Map<?, ?>> inputs = dialog.getMapList("inputs");
        for (Map<?, ?> raw : inputs) {
            Object keyObject = raw.get("key");
            if (keyObject == null) continue;
            String key = String.valueOf(keyObject);
            Object typeObject = raw.get("type");
            String type = (typeObject == null ? "text" : String.valueOf(typeObject)).toLowerCase();
            Object value = switch (type) {
                case "boolean", "bool", "checkbox" -> response.getBoolean(key);
                case "number", "range", "number_range" -> response.getFloat(key);
                default -> response.getText(key);
            };
            values.put(key, value);
        }
        return values;
    }
}
