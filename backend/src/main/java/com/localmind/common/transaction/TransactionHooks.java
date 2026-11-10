package com.localmind.common.transaction;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Ties side effects outside the database (files, vector store, async jobs) to the outcome of the current transaction.
 */
@Slf4j
public final class TransactionHooks {

    private TransactionHooks() {
    }

    /**
     * Runs {@code afterCommit} once the current transaction commits and {@code afterRollback} if it rolls back. Without
     * an active transaction, {@code afterCommit} runs immediately. Callback failures are logged, never thrown: the
     * transaction outcome is already final.
     */
    public static void onOutcome(Runnable afterCommit, Runnable afterRollback) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            runSafely(afterCommit);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runSafely(afterCommit);
            }

            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    runSafely(afterRollback);
                }
            }
        });
    }

    public static void afterCommit(Runnable action) {
        onOutcome(action, () -> {
        });
    }

    private static void runSafely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.warn("Post-transaction action failed: {}", e.getMessage(), e);
        }
    }
}
