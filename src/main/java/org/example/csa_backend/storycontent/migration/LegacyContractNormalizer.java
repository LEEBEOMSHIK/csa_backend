package org.example.csa_backend.storycontent.migration;

import java.util.Comparator;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Component
public class LegacyContractNormalizer {

    private final ObjectMapper objectMapper;

    public LegacyContractNormalizer() {
        this(new ObjectMapper());
    }

    public LegacyContractNormalizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode normalize(Object value) {
        if (value == null) {
            throw new LegacyImportException("SHADOW_SNAPSHOT_REQUIRED", null);
        }
        return canonicalize(objectMapper.valueToTree(value));
    }

    public byte[] canonicalBytes(JsonNode normalized) {
        if (normalized == null) {
            throw new LegacyImportException("SHADOW_SNAPSHOT_REQUIRED", null);
        }
        return objectMapper.writeValueAsBytes(canonicalize(normalized));
    }

    private JsonNode canonicalize(JsonNode value) {
        if (value.isObject()) {
            ObjectNode result = objectMapper.createObjectNode();
            value.properties().stream()
                .sorted(Comparator.comparing(java.util.Map.Entry::getKey))
                .forEach(entry -> result.set(entry.getKey(), canonicalize(entry.getValue())));
            return result;
        }
        if (value.isArray()) {
            ArrayNode result = objectMapper.createArrayNode();
            value.forEach(item -> result.add(canonicalize(item)));
            return result;
        }
        return value.deepCopy();
    }
}
