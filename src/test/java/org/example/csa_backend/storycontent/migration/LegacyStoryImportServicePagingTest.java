package org.example.csa_backend.storycontent.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.LongStream;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class LegacyStoryImportServicePagingTest {

    @Test
    void deltaKeysetsSyntheticIdsAndNeverBuildsABulkParentQueryOverOneThousand() {
        SyntheticPagingJdbcTemplate jdbc = new SyntheticPagingJdbcTemplate();
        LegacyStoryImportService service = new LegacyStoryImportService(
            jdbc,
            mock(LegacyStoryProjectionMapper.class),
            mock(LegacyMediaSnapshotStore.class),
            mock(CanonicalStoryWriter.class),
            new ContractChecksum()
        );

        ImportBatchResult result = service.importDelta(Instant.parse("2020-01-01T00:00:00Z"));

        assertThat(result.imported()).isZero();
        assertThat(jdbc.curatedDiscoveryAfter).containsExactly(0L, 1_000L);
        assertThat(jdbc.curatedParentBulkSizes).containsExactly(1_000, 1);
        assertThat(jdbc.maximumBulkSize()).isLessThanOrEqualTo(1_000);
        assertThat(jdbc.lockEvents).hasSize(5);
        assertThat(jdbc.lockEvents.get(0)).startsWith("LOCK:");
        assertThat(jdbc.lockEvents.get(1)).startsWith("LOCK:");
        assertThat(jdbc.lockEvents.get(0)).isNotEqualTo(jdbc.lockEvents.get(1));
        assertThat(jdbc.lockEvents.get(2))
            .isEqualTo(jdbc.lockEvents.get(1).replace("LOCK:", "UNLOCK:"));
        assertThat(jdbc.lockEvents.get(3))
            .isEqualTo(jdbc.lockEvents.get(0).replace("LOCK:", "UNLOCK:"));
        assertThat(jdbc.lockEvents.get(4)).isEqualTo("CONNECTION_CLOSE");
    }

    private static final class SyntheticPagingJdbcTemplate extends JdbcTemplate {

        private final List<Long> curatedDiscoveryAfter = new ArrayList<>();
        private final List<Integer> curatedParentBulkSizes = new ArrayList<>();
        private final List<Integer> allBulkSizes = new ArrayList<>();
        private final List<String> lockEvents;

        private SyntheticPagingJdbcTemplate() {
            this(sessionLockJdbc());
        }

        private SyntheticPagingJdbcTemplate(SessionLockJdbc lockJdbc) {
            super(lockJdbc.dataSource());
            this.lockEvents = lockJdbc.events();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            String normalized = sql.toLowerCase(java.util.Locale.ROOT);
            if (normalized.startsWith("select f.id from fairytales f") && normalized.contains("limit ?")) {
                long after = ((Number) args[0]).longValue();
                curatedDiscoveryAfter.add(after);
                if (after == 0L) {
                    return (List<T>) LongStream.rangeClosed(1, 1_000).boxed().toList();
                }
                if (after == 1_000L) {
                    return (List<T>) List.of(1_001L);
                }
                return List.of();
            }
            if (normalized.startsWith("select f.id from ai_fairytales f")
                && normalized.contains("limit ?")) {
                return List.of();
            }
            if (normalized.contains("where f.id in (")) {
                curatedParentBulkSizes.add(args.length);
                allBulkSizes.add(args.length);
                return List.of();
            }
            return List.of();
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (sql.toLowerCase(java.util.Locale.ROOT).contains("clock_timestamp")) {
                return requiredType.cast(Timestamp.from(Instant.parse("2026-08-11T09:00:00Z")));
            }
            throw new AssertionError("Unexpected queryForObject: " + sql);
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType) {
            return queryForObject(sql, requiredType, new Object[0]);
        }

        @Override
        public int update(String sql, Object... args) {
            return 1;
        }

        int maximumBulkSize() {
            return allBulkSizes.stream().mapToInt(Integer::intValue).max().orElse(0);
        }
    }

    private static SessionLockJdbc sessionLockJdbc() {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement lockStatement = mock(PreparedStatement.class);
        PreparedStatement unlockStatement = mock(PreparedStatement.class);
        ResultSet unlockResult = mock(ResultSet.class);
        List<String> events = new ArrayList<>();
        AtomicLong lockKey = new AtomicLong();
        AtomicLong unlockKey = new AtomicLong();
        try {
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.prepareStatement("select pg_advisory_lock(?)"))
                .thenReturn(lockStatement);
            when(connection.prepareStatement("select pg_advisory_unlock(?)"))
                .thenReturn(unlockStatement);
            doAnswer(invocation -> {
                lockKey.set(invocation.getArgument(1));
                return null;
            }).when(lockStatement).setLong(eq(1), anyLong());
            doAnswer(invocation -> {
                events.add("LOCK:" + lockKey.get());
                return true;
            }).when(lockStatement).execute();
            doAnswer(invocation -> {
                unlockKey.set(invocation.getArgument(1));
                return null;
            }).when(unlockStatement).setLong(eq(1), anyLong());
            doAnswer(invocation -> {
                events.add("UNLOCK:" + unlockKey.get());
                return unlockResult;
            }).when(unlockStatement).executeQuery();
            when(unlockResult.next()).thenReturn(true);
            when(unlockResult.getBoolean(1)).thenReturn(true);
            doAnswer(invocation -> {
                events.add("CONNECTION_CLOSE");
                return null;
            }).when(connection).close();
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
        return new SessionLockJdbc(dataSource, events);
    }

    private record SessionLockJdbc(DataSource dataSource, List<String> events) {
    }
}
