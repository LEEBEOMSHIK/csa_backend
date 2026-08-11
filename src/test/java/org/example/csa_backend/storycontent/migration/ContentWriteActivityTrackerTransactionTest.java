package org.example.csa_backend.storycontent.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

class ContentWriteActivityTrackerTransactionTest {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @AfterEach
    void cleanUp() {
        executor.shutdownNow();
        TransactionSynchronizationManager.clear();
    }

    @Test
    void actionReturnDoesNotReleaseWriterUntilRealCommitCompletes() throws Exception {
        ContentWriteActivityTracker tracker = new ContentWriteActivityTracker(mock(ContentMigrationGate.class));
        TransactionTemplate transaction = new TransactionTemplate(new TestTransactionManager());
        CountDownLatch actionReturned = new CountDownLatch(1);
        CountDownLatch allowCommit = new CountDownLatch(1);
        AtomicBoolean acknowledged = new AtomicBoolean(false);

        Future<?> worker = executor.submit(() -> transaction.executeWithoutResult(status ->
            tracker.execute(ContentWriteKind.LEGACY_AI, () -> {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void beforeCommit(boolean readOnly) {
                        actionReturned.countDown();
                        await(allowCommit);
                    }
                });
                return null;
            })
        ));

        assertThat(actionReturned.await(2, TimeUnit.SECONDS)).isTrue();
        tracker.whenQuiescent(() -> acknowledged.set(true));
        assertThat(acknowledged).isFalse();

        allowCommit.countDown();
        worker.get(2, TimeUnit.SECONDS);
        tracker.whenQuiescent(() -> acknowledged.set(true));
        assertThat(acknowledged).isTrue();
    }

    @Test
    void rollbackCompletionReleasesWriter() {
        ContentWriteActivityTracker tracker = new ContentWriteActivityTracker(mock(ContentMigrationGate.class));
        TransactionTemplate transaction = new TransactionTemplate(new TestTransactionManager());
        AtomicBoolean acknowledged = new AtomicBoolean(false);

        transaction.executeWithoutResult(status -> {
            tracker.execute(ContentWriteKind.CANONICAL_AUTHORING, () -> null);
            status.setRollbackOnly();
        });

        tracker.whenQuiescent(() -> acknowledged.set(true));
        assertThat(acknowledged).isTrue();
    }

    @Test
    void activeTransactionWithoutSynchronizationRejectsBeforeActionAndRestoresAdmission() {
        ContentWriteActivityTracker tracker = new ContentWriteActivityTracker(mock(ContentMigrationGate.class));
        AtomicBoolean actionInvoked = new AtomicBoolean(false);
        AtomicBoolean acknowledged = new AtomicBoolean(false);
        try {
            TransactionSynchronizationManager.setActualTransactionActive(true);

            assertThatThrownBy(() -> tracker.execute(ContentWriteKind.CANONICAL_AUTHORING, () -> {
                actionInvoked.set(true);
                return null;
            }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Active content transaction requires synchronization");
            assertThat(actionInvoked).isFalse();
        } finally {
            TransactionSynchronizationManager.clear();
        }

        tracker.whenQuiescent(() -> acknowledged.set(true));
        assertThat(acknowledged).isTrue();
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

    private static final class TestTransactionManager extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
