package com.localmind.common.transaction;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

/**
 * Unit tests for {@link TransactionHooks}: after-commit and rollback actions run only on their outcome, immediately or
 * never without a transaction, and failing callbacks are swallowed.
 */
class TransactionHooksTest {

    private final List<String> calls = new ArrayList<>();

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("onOutcome() runs only the after-commit action when the transaction commits")
    void onOutcomeCommit() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionHooks.onOutcome(() -> calls.add("commit"), () -> calls.add("rollback"));
        assertEquals(List.of(), calls);

        commit();

        assertEquals(List.of("commit"), calls);
    }

    @Test
    @DisplayName("onOutcome() runs only the rollback action when the transaction rolls back")
    void onOutcomeRollback() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionHooks.onOutcome(() -> calls.add("commit"), () -> calls.add("rollback"));

        TransactionSynchronizationUtils.triggerAfterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertEquals(List.of("rollback"), calls);
    }

    @Test
    @DisplayName("afterCommit() runs on commit and does nothing on rollback")
    void afterCommitOnlyOnCommit() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionHooks.afterCommit(() -> calls.add("commit"));
        TransactionSynchronizationUtils.triggerAfterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertEquals(List.of(), calls);

        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.initSynchronization();
        TransactionHooks.afterCommit(() -> calls.add("commit"));
        commit();

        assertEquals(List.of("commit"), calls);
    }

    @Test
    @DisplayName("Without an active transaction the after-commit action runs immediately and rollback never runs")
    void withoutTransactionRunsImmediately() {
        TransactionHooks.onOutcome(() -> calls.add("commit"), () -> calls.add("rollback"));
        TransactionHooks.afterCommit(() -> calls.add("after-commit"));

        assertEquals(List.of("commit", "after-commit"), calls);
    }

    @Test
    @DisplayName("A failing callback is logged, never thrown")
    void callbackFailuresAreSwallowed() {
        Runnable failing = () -> {
            throw new IllegalStateException("boom");
        };

        assertDoesNotThrow(() -> TransactionHooks.afterCommit(failing));

        TransactionSynchronizationManager.initSynchronization();
        TransactionHooks.onOutcome(failing, failing);
        assertDoesNotThrow(() -> TransactionSynchronizationUtils
                .triggerAfterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
    }

    @Test
    @DisplayName("onRollback() runs only on rollback, and never without a transaction")
    void onRollbackOnlyOnRollback() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionHooks.onRollback(() -> calls.add("committed?"));
        commit();
        assertEquals(List.of(), calls);
        TransactionSynchronizationManager.clearSynchronization();

        TransactionSynchronizationManager.initSynchronization();
        TransactionHooks.onRollback(() -> calls.add("rollback"));
        TransactionSynchronizationUtils.triggerAfterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertEquals(List.of("rollback"), calls);
        TransactionSynchronizationManager.clearSynchronization();

        TransactionHooks.onRollback(() -> calls.add("no transaction"));
        assertEquals(List.of("rollback"), calls);
    }

    private static void commit() {
        TransactionSynchronizationUtils.triggerAfterCommit();
        TransactionSynchronizationUtils.triggerAfterCompletion(TransactionSynchronization.STATUS_COMMITTED);
    }
}
