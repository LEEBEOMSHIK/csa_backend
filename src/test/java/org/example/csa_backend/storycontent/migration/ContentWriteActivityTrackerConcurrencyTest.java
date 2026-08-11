package org.example.csa_backend.storycontent.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ContentWriteActivityTrackerConcurrencyTest {

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @AfterEach
    void shutDownExecutor() {
        executor.shutdownNow();
    }

    @Test
    void acknowledgementCannotRunWhileAdmittedWriterIsActive() throws Exception {
        ContentMigrationGate gate = mock(ContentMigrationGate.class);
        ContentWriteActivityTracker tracker = new ContentWriteActivityTracker(gate);
        CountDownLatch actionStarted = new CountDownLatch(1);
        CountDownLatch allowActionReturn = new CountDownLatch(1);
        AtomicBoolean acknowledged = new AtomicBoolean(false);

        Future<?> writer = executor.submit(() -> tracker.execute(ContentWriteKind.LEGACY_AI, () -> {
            actionStarted.countDown();
            await(allowActionReturn);
            return null;
        }));

        assertThat(actionStarted.await(2, TimeUnit.SECONDS)).isTrue();
        tracker.whenQuiescent(() -> acknowledged.set(true));
        assertThat(acknowledged).isFalse();

        allowActionReturn.countDown();
        writer.get(2, TimeUnit.SECONDS);
        tracker.whenQuiescent(() -> acknowledged.set(true));
        assertThat(acknowledged).isTrue();
    }

    @Test
    void writerAdmittedAfterAcknowledgementSeesFreezeAndNeverRunsAction() {
        ContentMigrationGate gate = mock(ContentMigrationGate.class);
        AtomicBoolean frozen = new AtomicBoolean(false);
        doAnswer(invocation -> {
            if (frozen.get()) {
                throw ContentMigrationException.serviceUnavailable("CONTENT_MIGRATION_FREEZE", 9L);
            }
            return null;
        }).when(gate).assertWritesAllowed(any());
        ContentWriteActivityTracker tracker = new ContentWriteActivityTracker(gate);
        AtomicBoolean actionInvoked = new AtomicBoolean(false);

        tracker.whenQuiescent(() -> frozen.set(true));

        assertThatThrownBy(() -> tracker.execute(ContentWriteKind.CANONICAL_AUTHORING, () -> {
            actionInvoked.set(true);
            return null;
        }))
            .isInstanceOf(ContentMigrationException.class)
            .hasMessage("CONTENT_MIGRATION_FREEZE");
        assertThat(actionInvoked).isFalse();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) {
                throw new AssertionError("latch timeout");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }
}
