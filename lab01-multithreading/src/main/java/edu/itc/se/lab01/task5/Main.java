
package edu.itc.se.lab01.task5;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class Main {

    static final int ACCOUNT_COUNT = 4;
    static final int WORKERS = 8;
    static final int TRANSFERS_PER_WORKER = 100_000;
    static final long EXPECTED_TOTAL = 4_000;

    // Experiment 1: OrderedBank
    static void testOrderedBank() throws InterruptedException {
        Bank.Account[] accounts = new Bank.Account[ACCOUNT_COUNT];

        for (int i = 0; i < ACCOUNT_COUNT; i++) {
            accounts[i] = new Bank.Account(i, 1_000);
        }

        AtomicLong completed = new AtomicLong();
        Thread[] workers = new Thread[WORKERS];

        long start = System.nanoTime();

        for (int w = 0; w < WORKERS; w++) {
            workers[w] = new Thread(() -> {
                var random = ThreadLocalRandom.current();
                int successful = 0;
                int nextReport = 10_000;

                while (successful < TRANSFERS_PER_WORKER
                        && !Thread.currentThread().isInterrupted()) {

                    Bank.Account from =
                            accounts[random.nextInt(ACCOUNT_COUNT)];
                    Bank.Account to =
                            accounts[random.nextInt(ACCOUNT_COUNT)];

                    if (OrderedBank.transfer(
                            from, to, random.nextInt(1, 50))) {

                        successful++;
                        completed.incrementAndGet();

                        if (successful >= nextReport) {
                            System.out.println(
                                    Thread.currentThread().getName()
                                    + " completed " + successful
                                    + " transfers"
                            );
                            nextReport += 10_000;
                        }
                    }
                }
            }, "ordered-" + w);

            workers[w].start();
        }

        for (Thread worker : workers) {
            worker.join();
        }

        long total = totalBankBalance(accounts);

        long elapsedMs =
                TimeUnit.NANOSECONDS.toMillis(
                        System.nanoTime() - start
                );

        System.out.printf(
                "transfers=%d total=%d (expected %d) in %d ms%n",
                completed.get(), total, EXPECTED_TOTAL, elapsedMs
        );

        if (completed.get() != (long) WORKERS * TRANSFERS_PER_WORKER) {
            throw new AssertionError(
                    "OrderedBank did not complete all transfers"
            );
        }

        checkTotal(total);
    }

    // Experiment 2: TryLockBank
    static void testTryLockBank() throws InterruptedException {
        TryLockBank.Account[] accounts =
                new TryLockBank.Account[ACCOUNT_COUNT];

        for (int i = 0; i < ACCOUNT_COUNT; i++) {
            accounts[i] = new TryLockBank.Account(i, 1_000);
        }

        AtomicLong completed = new AtomicLong();
        TryLockBank.retries.set(0);

        Thread[] workers = new Thread[WORKERS];
        long start = System.nanoTime();

        for (int w = 0; w < WORKERS; w++) {
            workers[w] = new Thread(() -> {
                var random = ThreadLocalRandom.current();
                int successful = 0;

                while (successful < TRANSFERS_PER_WORKER
                        && !Thread.currentThread().isInterrupted()) {

                    TryLockBank.Account from =
                            accounts[random.nextInt(ACCOUNT_COUNT)];
                    TryLockBank.Account to =
                            accounts[random.nextInt(ACCOUNT_COUNT)];

                    try {
                        if (TryLockBank.transfer(
                                from, to, random.nextInt(1, 50))) {

                            successful++;
                            completed.incrementAndGet();
                        } else {
                            // Avoid excessive spinning after a failed attempt.
                            Thread.yield();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }, "trylock-" + w);

            workers[w].start();
        }

        for (Thread worker : workers) {
            worker.join();
        }

        long total = totalTryLockBalance(accounts);

        long elapsedMs =
                TimeUnit.NANOSECONDS.toMillis(
                        System.nanoTime() - start
                );

        System.out.printf(
                "transfers=%d retries=%d total=%d "
                + "(expected %d) in %d ms%n",
                completed.get(),
                TryLockBank.retries.get(),
                total,
                EXPECTED_TOTAL,
                elapsedMs
        );

        if (completed.get() != (long) WORKERS * TRANSFERS_PER_WORKER) {
            throw new AssertionError(
                    "TryLockBank did not complete all transfers"
            );
        }

        checkTotal(total);
    }

    // Experiment 3: Graceful shutdown
    static void testGracefulShutdown() throws InterruptedException {
        Bank.Account[] accounts = new Bank.Account[ACCOUNT_COUNT];

        for (int i = 0; i < ACCOUNT_COUNT; i++) {
            accounts[i] = new Bank.Account(i, 1_000);
        }

        Thread[] workers = new Thread[WORKERS];

        for (int w = 0; w < WORKERS; w++) {
            workers[w] = new Thread(() -> {
                var random = ThreadLocalRandom.current();
                long localCount = 0;

                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        Bank.Account from =
                                accounts[random.nextInt(ACCOUNT_COUNT)];
                        Bank.Account to =
                                accounts[random.nextInt(ACCOUNT_COUNT)];

                        if (OrderedBank.transfer(
                                from, to, random.nextInt(1, 50))) {
                            localCount++;
                        }
                    }
                } finally {
                    System.out.println(
                            Thread.currentThread().getName()
                            + " stopped after " + localCount
                            + " transfers; interrupted="
                            + Thread.currentThread().isInterrupted()
                    );
                }
            }, "transfer-" + w);

            workers[w].start();
        }

        // Let the workers run for two seconds.
        Thread.sleep(2_000);

        System.out.println(
                "[main] interrupting " + WORKERS
                + " workers after 2000 ms"
        );

        long shutdownStart = System.nanoTime();
        long deadline = shutdownStart
                + TimeUnit.SECONDS.toNanos(1);

        // Request shutdown of all workers.
        for (Thread worker : workers) {
            worker.interrupt();
        }

        // Give all workers the same one-second deadline.
        for (Thread worker : workers) {
            long remaining = deadline - System.nanoTime();

            if (remaining <= 0) {
                break;
            }

            long millis = TimeUnit.NANOSECONDS.toMillis(remaining);
            int nanos = (int) (
                    remaining
                    - TimeUnit.MILLISECONDS.toNanos(millis)
            );

            worker.join(millis, nanos);
        }

        boolean allStopped = true;

        for (Thread worker : workers) {
            if (worker.isAlive()) {
                allStopped = false;
                System.out.println(
                        "[main] WARNING: " + worker.getName()
                        + " did not stop within 1 second"
                );
            }
        }

        long shutdownMs = TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - shutdownStart
        );

        if (!allStopped) {
            throw new AssertionError(
                    "Some workers did not stop within one second"
            );
        }

        // All workers are stopped, so balances can now be checked.
        long total = totalBankBalance(accounts);

        System.out.printf(
                "all workers stopped=%s shutdown=%d ms "
                + "total=%d (expected %d)%n",
                allStopped, shutdownMs, total, EXPECTED_TOTAL
        );

        checkTotal(total);
    }

    // Sum balances for Bank.Account objects.
    static long totalBankBalance(Bank.Account[] accounts) {
        long total = 0;

        for (Bank.Account account : accounts) {
            total += account.balance();
        }

        return total;
    }

    // Sum balances for TryLockBank.Account objects.
    static long totalTryLockBalance(TryLockBank.Account[] accounts) {
        long total = 0;

        for (TryLockBank.Account account : accounts) {
            account.lock.lock();
            try {
                total += account.balance;
            } finally {
                account.lock.unlock();
            }
        }

        return total;
    }

    // Check the invariant.
    static void checkTotal(long total) {
        if (total != EXPECTED_TOTAL) {
            throw new AssertionError(
                    "Balance invariant violated: expected "
                    + EXPECTED_TOTAL + ", got " + total
            );
        }
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("# OrderedBank");
        testOrderedBank();

        System.out.println("\n# TryLockBank");
        testTryLockBank();

        System.out.println("\n# graceful shutdown");
        testGracefulShutdown();

        System.out.println("\nAll Task 5 experiments completed.");
    }
}
