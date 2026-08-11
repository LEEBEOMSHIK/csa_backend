package org.example.csa_backend.storycontent.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.example.csa_backend.storycontent.LegacyFairytaleReadAdapter;
import org.example.csa_backend.storycontent.LegacyType;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

class LegacyShadowCompareServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2030-01-02T03:04:05Z"), ZoneOffset.UTC);

    @Test
    void matchingSnapshotsResolveAnyOpenMismatch() {
        LegacyFairytaleReadAdapter adapter = mock(LegacyFairytaleReadAdapter.class);
        when(adapter.legacyType()).thenReturn(LegacyType.CURATED);
        when(adapter.readLegacy(7L)).thenReturn(Map.of("id", 7L, "title", "same"));
        when(adapter.readCanonical(7L)).thenReturn(Map.of("title", "same", "id", 7L));
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LegacyShadowCompareService service = service(adapter, jdbc);

        ShadowCompareResult result = service.compare(LegacyType.CURATED, 7L);

        assertThat(result.matches()).isTrue();
        assertThat(result.legacyChecksum()).isEqualTo(result.canonicalChecksum());
        verify(jdbc).update(
            contains("resolved_at"),
            any(),
            eq(LegacyType.CURATED.name()),
            eq(7L)
        );
    }

    @Test
    void mismatchingSnapshotsUpsertOneOpenMismatchWithCanonicalJsonDiff() {
        LegacyFairytaleReadAdapter adapter = mock(LegacyFairytaleReadAdapter.class);
        when(adapter.legacyType()).thenReturn(LegacyType.AI);
        when(adapter.readLegacy(8L)).thenReturn(Map.of("id", 8L, "title", "legacy"));
        when(adapter.readCanonical(8L)).thenReturn(Map.of("id", 8L, "title", "canonical"));
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LegacyShadowCompareService service = service(adapter, jdbc);

        ShadowCompareResult result = service.compare(LegacyType.AI, 8L);

        assertThat(result.matches()).isFalse();
        assertThat(result.diff()).containsKey("/title");
        verify(jdbc).update(
            contains("insert into legacy_shadow_mismatches"),
            eq(LegacyType.AI.name()),
            eq(8L),
            eq(result.legacyChecksum()),
            eq(result.canonicalChecksum()),
            any(String.class),
            any()
        );
    }

    @Test
    void canonicalInfrastructureFailurePropagatesWithoutWritingPartialAudit() {
        LegacyFairytaleReadAdapter adapter = mock(LegacyFairytaleReadAdapter.class);
        when(adapter.legacyType()).thenReturn(LegacyType.CURATED);
        when(adapter.readLegacy(9L)).thenReturn(Map.of("id", 9L, "title", "legacy"));
        when(adapter.readCanonical(9L))
            .thenThrow(new DataAccessResourceFailureException("canonical database unavailable"));
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LegacyShadowCompareService service = service(adapter, jdbc);

        assertThatThrownBy(() -> service.compare(LegacyType.CURATED, 9L))
            .isInstanceOf(DataAccessResourceFailureException.class);
        verifyNoInteractions(jdbc);
    }

    private LegacyShadowCompareService service(
        LegacyFairytaleReadAdapter adapter,
        JdbcTemplate jdbc
    ) {
        return new LegacyShadowCompareService(
            List.of(adapter),
            new LegacyContractNormalizer(),
            new ContractChecksum(),
            new JsonDiff(),
            jdbc,
            CLOCK
        );
    }
}
