
package edu.itc.se.lab01.task3;

import java.util.concurrent.atomic.AtomicInteger;

public class Main2 {
    public static void main(String[] args) throws InterruptedException {
        int capacity = 5;
        int numberOfProducers = 2;
        int numberOfConsumers = 3;
        int ordersPerProducer = 50;

        // Use Java's built-in blocking queue
        OrderQueue queue = new BlockingOrderQueue(capacity);

        AtomicInteger processedCount = new AtomicInteger(0);

        long start = System.nanoTime();

        // Start consumers
        Thread[] consumers = new Thread[numberOfConsumers];

        for (int i = 0; i < numberOfConsumers; i++) {
            consumers[i] = new Thread(
                new Consumers(queue, i + 1, processedCount)
            );
            consumers[i].start();
        }

        // Start producers
        Thread[] producers = new Thread[numberOfProducers];

        for (int i = 0; i < numberOfProducers; i++) {
            producers[i] = new Thread(
                new Producer(
                    queue, ordersPerProducer, numberOfConsumers, i + 1
                )
            );
            producers[i].start();
        }

        // Wait for both producers to finish
        for (Thread producer : producers) {
            producer.join();
        }

        // Stop all consumers using poison pills
        for (int i = 0; i < numberOfConsumers; i++) {
            queue.put(new Order(-1, "POISON", 0));
        }

        // Wait for all consumers to finish
        for (Thread consumer : consumers) {
            consumer.join();
        }

        long end = System.nanoTime();
        long elapsedMs = (end - start) / 1_000_000;

        int total = processedCount.get();

        System.out.println(
            "Total orders processed: " + total
            + " in " + elapsedMs + " ms"
        );

        if (total != 100) {
            throw new AssertionError(
                "Expected 100 orders, got " + total
            );
        }

        System.out.println("Test passed!");
    }
}
