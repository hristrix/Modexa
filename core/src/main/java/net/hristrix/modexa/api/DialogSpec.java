package net.hristrix.modexa.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Programmatic representation of the same schema accepted by dialog YAML files.
 * Raw properties are intentionally supported so new YAML features do not require an API redesign.
 */
public record DialogSpec(String id, Map<String, Object> values) {
    public DialogSpec {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(values, "values");
        values = Collections.unmodifiableMap(deepCopyMap(values));
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static Map<String, Object> map(Object... keyValues) {
        if (keyValues.length % 2 != 0) throw new IllegalArgumentException("map() requires key/value pairs");
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return map;
    }

    @SafeVarargs
    public static <T> List<T> list(T... values) {
        return List.of(values);
    }

    public static Map<String, Object> action(String type, Object... keyValues) {
        Map<String, Object> map = map(keyValues);
        map.put("type", type);
        return map;
    }

    public static Map<String, Object> button(String id, String label, List<Map<String, Object>> actions) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (id != null && !id.isBlank()) map.put("id", id);
        map.put("label", label);
        map.put("actions", actions);
        return map;
    }

    private static Map<String, Object> deepCopyMap(Map<?, ?> source) {
        Map<String, Object> out = new LinkedHashMap<>();
        source.forEach((k, v) -> out.put(String.valueOf(k), deepCopy(v)));
        return out;
    }

    private static Object deepCopy(Object value) {
        if (value instanceof Map<?, ?> map) return deepCopyMap(map);
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>();
            for (Object item : list) out.add(deepCopy(item));
            return Collections.unmodifiableList(out);
        }
        return value;
    }

    public static final class Builder {
        private final String id;
        private final Map<String, Object> values = new LinkedHashMap<>();

        private Builder(String id) {
            this.id = Objects.requireNonNull(id, "id");
            values.put("id", id);
            values.put("enabled", true);
            values.put("type", "multi_action");
            values.put("columns", 1);
        }

        public Builder title(String title) { values.put("title", title); return this; }
        public Builder externalTitle(String title) { values.put("external-title", title); return this; }
        public Builder type(String type) { values.put("type", type); return this; }
        public Builder columns(int columns) { values.put("columns", columns); return this; }
        public Builder permission(String permission) { values.put("permission", permission); return this; }
        public Builder property(String key, Object value) { values.put(key, value); return this; }
        public Builder body(List<Map<String, Object>> body) { values.put("body", body); return this; }
        public Builder inputs(List<Map<String, Object>> inputs) { values.put("inputs", inputs); return this; }
        public Builder buttons(List<Map<String, Object>> buttons) { values.put("buttons", buttons); return this; }

        @SuppressWarnings("unchecked")
        public Builder addButton(Map<String, Object> button) {
            List<Map<String, Object>> buttons = (List<Map<String, Object>>) values.computeIfAbsent("buttons", k -> new ArrayList<>());
            buttons.add(button);
            return this;
        }

        @SuppressWarnings("unchecked")
        public Builder addBody(Map<String, Object> body) {
            List<Map<String, Object>> bodies = (List<Map<String, Object>>) values.computeIfAbsent("body", k -> new ArrayList<>());
            bodies.add(body);
            return this;
        }

        @SuppressWarnings("unchecked")
        public Builder addInput(Map<String, Object> input) {
            List<Map<String, Object>> inputs = (List<Map<String, Object>>) values.computeIfAbsent("inputs", k -> new ArrayList<>());
            inputs.add(input);
            return this;
        }

        public DialogSpec build() { return new DialogSpec(id, values); }
    }
}
