package ru.dvdishka.backuper.backend;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class AsyncExecutorTest {
    @Test
    void nestedTasksAndControlRequestsRemainRunnable() throws Exception {
        AsyncExecutor executor = new AsyncExecutor();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            var parent = executor.submit(() -> executor.submit(() -> {
                assertTrue(Thread.currentThread().isVirtual());
                entered.countDown();
                try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }).join());
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            executor.submit(release::countDown).get(5, TimeUnit.SECONDS);
            parent.get(5, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            assertTrue(executor.stop(Duration.ofSeconds(5)));
        }
        assertThrows(RejectedExecutionException.class, () -> executor.submit(() -> {}));
    }

    @Test
    void shutdownTracksActualExecutionEvenAfterFutureCancellation() throws Exception {
        AsyncExecutor executor = new AsyncExecutor();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        var future = executor.submit(() -> {
            entered.countDown();
            while (release.getCount() != 0) {
                try { release.await(); } catch (InterruptedException ignored) { }
            }
        });
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            future.cancel(true);
            assertFalse(executor.stop(Duration.ofMillis(100)));
        } finally {
            release.countDown();
            assertTrue(executor.stop(Duration.ofSeconds(5)));
        }
    }

    @Test
    void reloadCanStopItsOwnExecutorWithoutSelfInterruption() throws Exception {
        AsyncExecutor executor = new AsyncExecutor();
        executor.submit(() -> {
            assertTrue(executor.stop(Duration.ofSeconds(1)));
            assertFalse(Thread.currentThread().isInterrupted());
        }).get(5, TimeUnit.SECONDS);
        assertTrue(executor.stop(Duration.ofSeconds(5)));
    }
}
