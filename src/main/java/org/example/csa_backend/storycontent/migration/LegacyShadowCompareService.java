package org.example.csa_backend.storycontent.migration;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.example.csa_backend.storycontent.LegacyFairytaleReadAdapter;
import org.example.csa_backend.storycontent.LegacyType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class LegacyShadowCompareService {

    private final Map<LegacyType, LegacyFairytaleReadAdapter> adapters;
    private final LegacyContractNormalizer normalizer;
    private final ContractChecksum checksum;
    private final JsonDiff jsonDiff;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public LegacyShadowCompareService(
        List<LegacyFairytaleReadAdapter> adapters,
        LegacyContractNormalizer normalizer,
        ContractChecksum checksum,
        JsonDiff jsonDiff,
        JdbcTemplate jdbc
    ) {
        this(adapters, normalizer, checksum, jsonDiff, jdbc, Clock.systemUTC());
    }

    LegacyShadowCompareService(
        List<LegacyFairytaleReadAdapter> adapters,
        LegacyContractNormalizer normalizer,
        ContractChecksum checksum,
        JsonDiff jsonDiff,
        JdbcTemplate jdbc,
        Clock clock
    ) {
        this.adapters = new EnumMap<>(LegacyType.class);
        for (LegacyFairytaleReadAdapter adapter : adapters) {
            if (this.adapters.put(adapter.legacyType(), adapter) != null) {
                throw new IllegalArgumentException("Duplicate legacy adapter: " + adapter.legacyType());
            }
        }
        this.normalizer = normalizer;
        this.checksum = checksum;
        this.jsonDiff = jsonDiff;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ShadowCompareResult compare(LegacyType legacyType, long legacyId) {
        LegacyFairytaleReadAdapter adapter = adapters.get(legacyType);
        if (adapter == null) {
            throw new LegacyImportException("LEGACY_ADAPTER_NOT_FOUND", legacyType.name());
        }
        JsonNode legacy = normalizer.normalize(adapter.readLegacy(legacyId));
        JsonNode canonical = normalizer.normalize(adapter.readCanonical(legacyId));
        String legacyChecksum = checksum.ofBytes(normalizer.canonicalBytes(legacy));
        String canonicalChecksum = checksum.ofBytes(normalizer.canonicalBytes(canonical));
        Instant now = clock.instant();
        if (legacyChecksum.equals(canonicalChecksum)) {
            jdbc.update(
                "update legacy_shadow_mismatches set resolved_at = ? "
                    + "where legacy_type = ? and legacy_id = ? and resolved_at is null",
                Timestamp.from(now),
                legacyType.name(),
                legacyId
            );
            return ShadowCompareResult.match(legacyChecksum);
        }

        Map<String, Object> diff = jsonDiff.diff(legacy, canonical);
        jdbc.update(
            "insert into legacy_shadow_mismatches "
                + "(legacy_type, legacy_id, legacy_checksum, canonical_checksum, diff_json, created_at) "
                + "values (?, ?, ?, ?, cast(? as jsonb), ?) "
                + "on conflict (legacy_type, legacy_id) where resolved_at is null do update set "
                + "legacy_checksum = excluded.legacy_checksum, "
                + "canonical_checksum = excluded.canonical_checksum, "
                + "diff_json = excluded.diff_json, created_at = excluded.created_at",
            legacyType.name(),
            legacyId,
            legacyChecksum,
            canonicalChecksum,
            objectMapper.writeValueAsString(diff),
            Timestamp.from(now)
        );
        return ShadowCompareResult.mismatch(legacyChecksum, canonicalChecksum, diff);
    }
}
