package org.example.csa_backend.storycontent.migration;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@RequiredArgsConstructor
public class ContentWriteActivityTracker {

    private final ContentMigrationGate gate;
    private final ReentrantReadWriteLock admissionLock = new ReentrantReadWriteLock(true);
    private final AtomicInteger activeWrites = new AtomicInteger();

    public <T> T execute(ContentWriteKind kind, Supplier<T> action) {
        AtomicBoolean releaseAfterCompletion = new AtomicBoolean(false);
        admissionLock.readLock().lock();
        try {
            activeWrites.incrementAndGet();
            try {
                gate.assertWritesAllowed(kind);
                registerTransactionCompletion(releaseAfterCompletion);
            } catch (RuntimeException | Error exception) {
                activeWrites.decrementAndGet();
                throw exception;
            }
        } finally {
            admissionLock.readLock().unlock();
        }

        try {
            return action.get();
        } finally {
            if (!releaseAfterCompletion.get()) {
                activeWrites.decrementAndGet();
            }
        }
    }

    public void whenQuiescent(Runnable acknowledgement) {
        admissionLock.writeLock().lock();
        try {
            if (activeWrites.get() == 0) {
                acknowledgement.run();
            }
        } finally {
            admissionLock.writeLock().unlock();
        }
    }

    private void registerTransactionCompletion(AtomicBoolean releaseAfterCompletion) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Active content transaction requires synchronization");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                activeWrites.decrementAndGet();
            }
        });
        releaseAfterCompletion.set(true);
    }
}
