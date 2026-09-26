package ru.dvdishka.backuper.backend;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;

/** Unbounded virtual tasks; termination is tracked independently of future cancellation. */
public final class AsyncExecutor {
    private final ExecutorService executor = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("Backuper-", 0).factory());
    private final Set<Thread> running = new HashSet<>();
    private int submitted;
    private boolean stopping;

    public synchronized CompletableFuture<Void> submit(Runnable task) {
        if (stopping) throw new RejectedExecutionException("Backuper is stopping");
        CompletableFuture<Void> result = new CompletableFuture<>();
        submitted++;
        try {
            executor.execute(() -> {
                Thread thread = Thread.currentThread();
                try {
                    synchronized (this) {
                        running.add(thread);
                        if (stopping) thread.interrupt();
                    }
                    task.run();
                    result.complete(null);
                } catch (Throwable e) {
                    result.completeExceptionally(e);
                } finally {
                    synchronized (this) {
                        running.remove(thread);
                        submitted--;
                        notifyAll();
                    }
                }
            });
        } catch (RuntimeException e) {
            submitted--;
            throw e;
        }
        return result;
    }

    public synchronized boolean isStopping() {
        return stopping;
    }

    /** A reload command may itself be running here; it must not wait for or interrupt itself. */
    public synchronized boolean stop(Duration timeout) {
        stopping = true;
        executor.shutdown();
        Thread caller = Thread.currentThread();
        running.stream().filter(thread -> thread != caller).forEach(Thread::interrupt);
        int remainingCaller = running.contains(caller) ? 1 : 0;
        long deadline = System.nanoTime() + timeout.toNanos();
        boolean interrupted = Thread.interrupted();
        try {
            while (submitted > remainingCaller) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) return false;
                try {
                    TimeUnit.NANOSECONDS.timedWait(this, remaining);
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            return true;
        } finally {
            if (interrupted) caller.interrupt();
        }
    }
}
