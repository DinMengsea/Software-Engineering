package edu.itc.se.lab01.challenge;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Stream;

public class WordCount {

    private static final Path POISON = Path.of("__POISON__");

    private final BlockingQueue<Path> queue =
            new ArrayBlockingQueue<>(16);

    private final ConcurrentHashMap<String, LongAdder> counts =
            new ConcurrentHashMap<>();

    private final AtomicInteger filesDone = new AtomicInteger();
    private final LongAdder wordsDone = new LongAdder();
    private final AtomicBoolean stopping = new AtomicBoolean();

    private final Path dir;
    private final int workers;
    private final boolean virtual;
    private final int top;
    private final long timeoutSec;

    private volatile Thread scanner;
    private ExecutorService pool;
    private ScheduledExecutorService reporter;

    private volatile int totalFiles;
    private volatile long startTime;
    private volatile long deadlineNanos;
    private volatile boolean partial;
    private volatile boolean finished;
    private final AtomicBoolean resultPrinted = new AtomicBoolean(false);

    static List<String> tokenize(String line) {
        return List.of(
            line.toLowerCase(java.util.Locale.ROOT)
                .split("[^\\p{L}]+")
        );
    }
    WordCount(
            Path dir,
            int workers,
            boolean virtual,
            int top,
            long timeoutSec
    ) {
        if (workers < 1) {
            throw new IllegalArgumentException(
                    "Workers must be at least 1"
            );
        }

        if (top < 1) {
            throw new IllegalArgumentException(
                    "Top N must be at least 1"
            );
        }

        if (timeoutSec < 1) {
            throw new IllegalArgumentException(
                    "Timeout must be at least 1 second"
            );
        }

        this.dir = dir.toAbsolutePath().normalize();
        this.workers = workers;
        this.virtual = virtual;
        this.top = top;
        this.timeoutSec = timeoutSec;
    }

    
    private void scan() {
        try {
            List<Path> files;

            // Step 1: Find all regular files in the directory
            try (Stream<Path> paths = Files.walk(dir)) {
                files = paths
                        .filter(Files::isRegularFile)
                        .toList();
            }

            // Step 2: Record the total number of files
            totalFiles = files.size();

            // Step 3: Put each file into the bounded queue
            for (Path file : files) {
                if (stopping.get()
                        || Thread.currentThread().isInterrupted()) {
                    break;
                }

                queue.put(file);
            }

        } catch (IOException e) {
            System.err.println(
                    "Error scanning directory " + dir + ": " + e.getMessage()
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

        } finally {
            // Step 4: Send one poison pill to each worker
            // so workers can stop when the queue is drained.
            if (!stopping.get()
                    && !Thread.currentThread().isInterrupted()) {
                try {
                    for (int i = 0; i < workers; i++) {
                        queue.put(POISON);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private void countFile(Path p) {
        try (BufferedReader reader = Files.newBufferedReader(
                p, StandardCharsets.UTF_8)) {

            String line;

            while ((line = reader.readLine()) != null) {
                // Stop promptly when cancellation is requested.
                if (Thread.currentThread().isInterrupted()
                        || stopping.get()) {
                    return;
                }

                for (String word : tokenize(line)) {
                    if (Thread.currentThread().isInterrupted()
                            || stopping.get()) {
                        return;
                    }

                    if (!word.isEmpty()) {
                        counts.computeIfAbsent(
                                word, k -> new LongAdder()
                        ).increment();

                        wordsDone.increment();
                    }
                }
            }

        } catch (IOException e) {
            System.err.println(
                    "Could not read " + p + ": " + e.getMessage()
            );
        }
    }


    private void work() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Path path = queue.take();

                // Poison pill means normal worker shutdown.
                if (path.equals(POISON)) {
                    return;
                }

                if (stopping.get()) {
                    return;
                }

                countFile(path);

                // Count only files completed without cancellation.
                if (Thread.currentThread().isInterrupted()
                        || stopping.get()) {
                    return;
                }

                filesDone.incrementAndGet();
            }
        } catch (InterruptedException e) {
            // Preserve the interrupt status.
            Thread.currentThread().interrupt();
        }
    }

    
    private void report() {
        long elapsedNanos = System.nanoTime() - startTime;
        double elapsedSeconds = elapsedNanos / 1_000_000_000.0;

        long totalWords = wordsDone.sum();

        double wordsPerSecond = elapsedSeconds > 0
                ? totalWords / elapsedSeconds
                : 0.0;

        List<Map.Entry<String, Long>> topWords = counts.entrySet()
                .stream()
                .map(entry -> Map.entry(
                        entry.getKey(),
                        entry.getValue().sum()
                ))
                .sorted(
                        Map.Entry.<String, Long>comparingByValue()
                                .reversed()
                                .thenComparing(Map.Entry::getKey)
                )
                .limit(3)
                .toList();

        System.out.printf(
                "[%.1fs] files %d/%d words %,d %.0f w/s",
                elapsedSeconds,
                filesDone.get(),
                totalFiles,
                totalWords,
                wordsPerSecond
        );

        if (!topWords.isEmpty()) {
            System.out.print(" top: ");

            for (int i = 0; i < topWords.size(); i++) {
                if (i > 0) {
                    System.out.print(" ");
                }

                Map.Entry<String, Long> entry = topWords.get(i);

                System.out.printf(
                        "%s(%,d)",
                        entry.getKey(),
                        entry.getValue()
                );
            }
        }

        System.out.println();
    }

    
    private void printResult(boolean partial) {
        List<Map.Entry<String, Long>> results = counts.entrySet()
                .stream()
                .map(entry -> Map.entry(
                        entry.getKey(),
                        entry.getValue().sum()
                ))
                .sorted(
                        Map.Entry.<String, Long>comparingByValue()
                                .reversed()
                                .thenComparing(Map.Entry::getKey)
                )
                .toList();

        long totalWords = wordsDone.sum();

        System.out.println();
        System.out.println(
                partial ? "===== [PARTIAL] WORD COUNT ====="
                        : "===== WORD COUNT RESULTS ====="
        );

        System.out.printf(
                "Files completed: %d/%d%n",
                filesDone.get(),
                totalFiles
        );

        System.out.printf("Total words: %,d%n", totalWords);
        System.out.println("Top " + top + " words:");

        for (int i = 0; i < Math.min(top, results.size()); i++) {
            Map.Entry<String, Long> entry = results.get(i);

            double percentage = totalWords > 0
                    ? entry.getValue() * 100.0 / totalWords
                    : 0.0;

            System.out.printf(
                    "%d. %s: %,d (%.2f%%)%n",
                    i + 1,
                    entry.getKey(),
                    entry.getValue(),
                    percentage
            );
        }

        try (BufferedWriter writer = Files.newBufferedWriter(
                Paths.get("result.csv"),
                StandardCharsets.UTF_8)) {

            writer.write("word,count,percentage");
            writer.newLine();

            for (Map.Entry<String, Long> entry : results) {
                double percentage = totalWords > 0
                        ? entry.getValue() * 100.0 / totalWords
                        : 0.0;

                writer.write(
                        csvEscape(entry.getKey()) + ","
                        + entry.getValue() + ","
                        + String.format(
                                java.util.Locale.ROOT,
                                "%.4f",
                                percentage
                        )
                );

                writer.newLine();
            }

            System.out.println("CSV written to: "
                    + Paths.get("result.csv").toAbsolutePath());

        } catch (IOException e) {
            System.err.println(
                    "Could not write result.csv: " + e.getMessage()
            );
        }
    }

        Map<String, Long> countsSnapshot() {
        Map<String, Long> snapshot = new java.util.HashMap<>();

        counts.forEach((word, count) ->
                snapshot.put(word, count.sum())
        );

        return Map.copyOf(snapshot);
    }
     
    private String csvEscape(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }


    
    void shutdown() {
        stopping.set(true);
        partial = true;

        // Request that all background tasks stop.
        Thread currentScanner = scanner;

        if (currentScanner != null) {
            currentScanner.interrupt();
        }

        if (pool != null) {
            pool.shutdownNow();
        }

        if (reporter != null) {
            reporter.shutdownNow();
        }

        // Print partial results BEFORE waiting for workers to terminate.
        if (resultPrinted.compareAndSet(false, true)) {
            printResult(true);
        }

        // Give background threads up to 2 seconds to stop.
        long deadline = System.nanoTime()
                + TimeUnit.SECONDS.toNanos(2);

        try {
            if (currentScanner != null) {
                long remaining = deadline - System.nanoTime();

                if (remaining > 0) {
                    currentScanner.join(
                            Math.max(1, remaining / 1_000_000)
                    );
                }
            }

            if (pool != null) {
                long remaining = deadline - System.nanoTime();

                if (remaining > 0) {
                    pool.awaitTermination(
                            remaining, TimeUnit.NANOSECONDS
                    );
                }
            }

            if (reporter != null) {
                long remaining = deadline - System.nanoTime();

                if (remaining > 0) {
                    reporter.awaitTermination(
                            remaining, TimeUnit.NANOSECONDS
                    );
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    
    void run() throws InterruptedException {
        startTime = System.nanoTime();
        deadlineNanos = startTime + TimeUnit.SECONDS.toNanos(timeoutSec);

        pool = virtual
                ? Executors.newVirtualThreadPerTaskExecutor()
                : Executors.newFixedThreadPool(workers);

        reporter = Executors.newSingleThreadScheduledExecutor();

        CountDownLatch workersDone = new CountDownLatch(workers);

        reporter.scheduleAtFixedRate(
                this::report,
                500,
                500,
                TimeUnit.MILLISECONDS
        );

        for (int i = 0; i < workers; i++) {
            pool.submit(() -> {
                try {
                    work();
                } finally {
                    workersDone.countDown();
                }
            });
        }

        scanner = Thread.ofPlatform()
                .name("word-scanner")
                .start(this::scan);

        boolean completed = false;

        try {
            long remaining = deadlineNanos - System.nanoTime();

            if (remaining > 0) {
                completed = workersDone.await(
                        remaining,
                        TimeUnit.NANOSECONDS
                );
            }

            if (completed) {
                scanner.join();
                reporter.shutdownNow();
                pool.shutdown();
                pool.awaitTermination(2, TimeUnit.SECONDS);
                finished = true;
            if (resultPrinted.compareAndSet(false, true)) {
                printResult(false);
            }
            } else {
                partial = true;
                shutdown();
            }

        } catch (InterruptedException e) {
            partial = true;
            shutdown();
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Path dir = Paths.get(".");
        int workers = Runtime.getRuntime().availableProcessors();
        boolean virtual = false;
        int top = 10;
        long timeoutSec = 60;

        // Parse command-line arguments
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--dir" -> dir = Paths.get(args[++i]);
                case "--workers" -> workers = Integer.parseInt(args[++i]);
                case "--virtual" -> virtual = true;
                case "--top" -> top = Integer.parseInt(args[++i]);
                case "--timeout" -> timeoutSec = Long.parseLong(args[++i]);
                case "--help", "-h" -> {
                    System.out.println(
                        "Usage: WordCount --dir DIR --workers N " +
                        "[--virtual] --top N --timeout SECONDS"
                    );
                    return;
                }
                default -> throw new IllegalArgumentException(
                    "Unknown argument: " + args[i]
                );
            }
        }

        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException(
                "Directory does not exist: " + dir
            );
        }

        WordCount app = new WordCount(dir, workers, virtual, top, timeoutSec);

            Runtime.getRuntime().addShutdownHook(
                    Thread.ofPlatform()
                            .name("wordcount-shutdown")
                            .unstarted(() -> {
                                if (!app.finished) {
                                    System.err.println(
                                            "\nShutdown hook started. Saving partial results..."
                                    );
                                    app.shutdown();
                                }
                            })
            );

        app.run();
    }

}
