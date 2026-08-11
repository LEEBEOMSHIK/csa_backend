package org.example.csa_backend.storycontent.migration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class JsonDiff {

    public Map<String, Object> diff(JsonNode legacy, JsonNode canonical) {
        Map<String, Object> differences = new LinkedHashMap<>();
        collect("", legacy, canonical, differences);
        return Collections.unmodifiableMap(differences);
    }

    private void collect(
        String path,
        JsonNode legacy,
        JsonNode canonical,
        Map<String, Object> differences
    ) {
        if (java.util.Objects.equals(legacy, canonical)) {
            return;
        }
        if (legacy != null && canonical != null && legacy.isObject() && canonical.isObject()) {
            Set<String> names = new TreeSet<>();
            names.addAll(legacy.propertyNames());
            names.addAll(canonical.propertyNames());
            for (String name : names) {
                collect(path + "/" + escape(name), legacy.get(name), canonical.get(name), differences);
            }
            return;
        }
        if (legacy != null && canonical != null && legacy.isArray() && canonical.isArray()) {
            int size = Math.max(legacy.size(), canonical.size());
            for (int index = 0; index < size; index++) {
                collect(
                    path + "/" + index,
                    index < legacy.size() ? legacy.get(index) : null,
                    index < canonical.size() ? canonical.get(index) : null,
                    differences
                );
            }
            return;
        }
        Map<String, Object> change = new LinkedHashMap<>();
        change.put("legacy", legacy);
        change.put("canonical", canonical);
        differences.put(path.isEmpty() ? "/" : path, change);
    }

    private String escape(String value) {
        return value.replace("~", "~0").replace("/", "~1");
    }
}
