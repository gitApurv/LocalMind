package com.localmind.common.transaction;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Ties side effects outside the database (files, vector store, async jobs) to the outcome of the current transaction.
 * Without an active transaction there is no outcome to wait for, so the after-commit action runs immediately. Callback
 * failures are logged, never thrown: the transaction outcome is already final.
 */
@Slf4j
public final class TransactionHooks {

    private TransactionHooks() {
    }

    public static void onOutcome(Runnable afterCommit, Runnable afterRollback) {
        register(new AfterCommitSynchronization(afterCommit) {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    runSafely(afterRollback);
                }
            }
        });
    }

    public static void afterCommit(Runnable action) {
        register(new AfterCommitSynchronization(action));
    }

    public static void onRollback(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    runSafely(action);
                }
            }
        });
    }

    private static void register(AfterCommitSynchronization synchronization) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            synchronization.afterCommit();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(synchronization);
    }

    private static void runSafely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.warn("Post-transaction action failed: {}", e.getMessage(), e);
        }
    }

    @RequiredArgsConstructor
    private static class AfterCommitSynchronization implements TransactionSynchronization {

        private final Runnable action;

        @Override
        public void afterCommit() {
            runSafely(action);
        }
    }
}
