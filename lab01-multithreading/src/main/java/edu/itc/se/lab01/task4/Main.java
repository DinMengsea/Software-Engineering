
package edu.itc.se.lab01.task4;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class Main {

    private static final long PRIME_LIMIT = 4_000_000L;
    private static final int PRIME_CHUNKS = 16;
    private static final int DOWNLOAD_COUNT = 500;
    private static final int PLATFORM_THREADS = 20;

    // Task 1: Count primes in [from, to).
    static long countPrimes(long from, long to) {
        long count = 0;

        for (long n = Math.max(from, 2); n < to; n++) {
            boolean prime = true;

            for (long d = 2; d * d <= n; d++) {
                if (n % d == 0) {
                    prime = false;
                    break;
                }
            }

            if (prime) {
                count++;
            }
        }

        return count;
    }

    // Task 1: Callable for counting primes in one chunk.
    static class PrimeCounter implements Callable<Long> {
        private final long from;
        private final long to;

        PrimeCounter(long from, long to) {
            this.from = from;
            this.to = to;
        }

        @Override
        public Long call() {
            return countPrimes(from, to);
        }
    }

    // Tasks 2-4: Simulated download task.
    static class FakeDownload implements Callable<String> {
        private final String url;
        private final int taskNumber;
        private final boolean slow;

        FakeDownload(String url, int taskNumber, boolean slow) {
            this.url = url;
            this.taskNumber = taskNumber;
            this.slow = slow;
        }

        @Override
        public String call() throws Exception {
            // The first download takes five seconds.
            // Other downloads take 200 milliseconds.
            Thread.sleep(slow ? 5_000 : 200);

            // Downloads 50, 100, ..., 500 fail.
            if (taskNumber % 50 == 0) {
                throw new IOException("Simulated download failure");
            }

            return url + " -> " + url.length();
        }
    }

    private static String downloadUrl(int taskNumber) {
        return "https://example.com/" + taskNumber;
    }

    // Collect Future results and enforce a one-second timeout
    // for the first download.
    static void runDownloads(
            ExecutorService executor, String label)
            throws InterruptedException {

        List<Future<String>> futures = new ArrayList<>();
        long start = System.nanoTime();

        for (int i = 1; i <= DOWNLOAD_COUNT; i++) {
            futures.add(executor.submit(
                    new FakeDownload(downloadUrl(i), i, i == 1)));
        }

        int ok = 0;
        int failed = 0;
        int timedOut = 0;

        for (int i = 0; i < futures.size(); i++) {
            Future<String> future = futures.get(i);

            try {
                if (i == 0) {
                    future.get(1, TimeUnit.SECONDS);
                } else {
                    future.get();
                }

                ok++;

            } catch (TimeoutException e) {
                future.cancel(true);
                timedOut++;

                System.out.println(
                        label + ": download " + (i + 1) + " timed out");

            } catch (ExecutionException e) {
                failed++;

                Throwable cause = e.getCause();
                String message = cause == null
                        ? "Unknown error"
                        : cause.getMessage();

                System.out.println(
                        label + ": download " + (i + 1)
                                + " failed: " + message);
            }
        }

        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - start);

        System.out.printf(
                "%s: ok=%d failed=%d timedOut=%d in %d ms%n",
                label, ok, failed, timedOut, elapsedMs);
    }

    // Shut down an executor gracefully, then force shutdown if necessary.
    static void shutdownExecutor(ExecutorService executor)
            throws InterruptedException {

        executor.shutdown();

        if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
            executor.shutdownNow();

            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                System.err.println(
                        "Executor did not terminate within the second timeout.");
            }
        }
    }

    // Bonus: Result type for CompletableFuture download processing.
    private record DownloadResult(
            int taskNumber, String status, String message) {
    }

    // Unwrap exceptions produced by CompletableFuture.
    private static Throwable unwrap(Throwable error) {
        Throwable current = error;

        while ((current instanceof CompletionException
                || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }

        // The simulated failure wraps IOException in RuntimeException.
        if (current instanceof RuntimeException
                && current.getCause() != null) {
            current = current.getCause();
        }

        return current;
    }

    // Bonus: CompletableFuture with virtual threads and timeouts.
    static void runDownloadsCompletableFuture() {
        ExecutorService executor =
                Executors.newVirtualThreadPerTaskExecutor();

        try {
            long start = System.nanoTime();

            List<CompletableFuture<DownloadResult>> futures =
                    new ArrayList<>();

            for (int i = 1; i <= DOWNLOAD_COUNT; i++) {
                final int taskNumber = i;
                final String url = downloadUrl(i);

                CompletableFuture<DownloadResult> future =
                        CompletableFuture.supplyAsync(() -> {
                            try {
                                Thread.sleep(
                                        taskNumber == 1 ? 5_000 : 200);

                                if (taskNumber % 50 == 0) {
                                    throw new RuntimeException(
                                            new IOException(
                                                    "Simulated download failure"));
                                }

                                return url + " -> " + url.length();

                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException(e);
                            }
                        }, executor)
                        .orTimeout(
                                taskNumber == 1 ? 1 : 10,
                                TimeUnit.SECONDS)
                        .handle((result, error) -> {
                            if (error == null) {
                                return new DownloadResult(
                                        taskNumber, "OK", result);
                            }

                            Throwable cause = unwrap(error);

                            String status =
                                    cause instanceof TimeoutException
                                            ? "TIMEOUT"
                                            : "FAILED";

                            String message = cause.getMessage() == null
                                    ? cause.getClass().getSimpleName()
                                    : cause.getMessage();

                            return new DownloadResult(
                                    taskNumber, status, message);
                        });

                futures.add(future);
            }

            // Wait until every future has completed.
            CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture<?>[0])).join();

            int ok = 0;
            int failed = 0;
            int timedOut = 0;

            for (CompletableFuture<DownloadResult> future : futures) {
                DownloadResult result = future.join();

                switch (result.status()) {
                    case "OK" -> ok++;

                    case "TIMEOUT" -> {
                        timedOut++;
                        System.out.println(
                                "CompletableFuture: download "
                                        + result.taskNumber() + " timed out");
                    }

                    default -> {
                        failed++;
                        System.out.println(
                                "CompletableFuture: download "
                                        + result.taskNumber()
                                        + " failed: " + result.message());
                    }
                }
            }

            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(
                    System.nanoTime() - start);

            System.out.printf(
                    "CompletableFuture downloads: "
                            + "ok=%d failed=%d timedOut=%d in %d ms%n",
                    ok, failed, timedOut, elapsedMs);

        } finally {
            try {
                shutdownExecutor(executor);
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    public static void main(String[] args) throws Exception {

        // Task 1A: Single-threaded prime counting.
        long start = System.nanoTime();

        long singleCount = countPrimes(0, PRIME_LIMIT);

        long singleMs = TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - start);

        System.out.printf(
                "single-threaded: %d primes in %d ms%n",
                singleCount, singleMs);

        // Task 1B: Parallel prime counting using a fixed thread pool.
        int processors = Runtime.getRuntime().availableProcessors();

        ExecutorService primePool =
                Executors.newFixedThreadPool(processors);

        try {
            start = System.nanoTime();

            List<Future<Long>> futures = new ArrayList<>();

            long chunkSize =
                    (PRIME_LIMIT + PRIME_CHUNKS - 1) / PRIME_CHUNKS;

            for (int i = 0; i < PRIME_CHUNKS; i++) {
                long from = i * chunkSize;
                long to = Math.min(from + chunkSize, PRIME_LIMIT);

                futures.add(primePool.submit(
                        new PrimeCounter(from, to)));
            }

            long parallelCount = 0;

            for (Future<Long> future : futures) {
                parallelCount += future.get();
            }

            long parallelMs = TimeUnit.NANOSECONDS.toMillis(
                    System.nanoTime() - start);

            System.out.printf(
                    "pool (%d threads): %d primes in %d ms%n",
                    processors, parallelCount, parallelMs);

            if (parallelCount != singleCount) {
                throw new AssertionError(
                        "Single-threaded and parallel counts do not match!");
            }

            if (singleCount != 283_146L) {
                throw new AssertionError(
                        "Unexpected prime count: " + singleCount);
            }

        } finally {
            shutdownExecutor(primePool);
        }

        // Tasks 2-4: Run 500 downloads using 20 platform threads.
        ExecutorService platformPool =
                Executors.newFixedThreadPool(PLATFORM_THREADS);

        try {
            runDownloads(
                    platformPool,
                    "Downloads on fixed(20) platform threads");
        } finally {
            shutdownExecutor(platformPool);
        }

        // Tasks 2-4: Run the same downloads using virtual threads.
        ExecutorService virtualPool =
                Executors.newVirtualThreadPerTaskExecutor();

        try {
            runDownloads(
                    virtualPool,
                    "Downloads on virtual threads");
        } finally {
            shutdownExecutor(virtualPool);
        }

        // Bonus: CompletableFuture with virtual threads.
        runDownloadsCompletableFuture();

        System.out.println("All tasks completed.");
    }
}
