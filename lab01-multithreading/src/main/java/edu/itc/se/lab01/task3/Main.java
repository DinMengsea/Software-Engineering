
package edu.itc.se.lab01.task3;

import java.util.concurrent.atomic.AtomicInteger;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        int capacity = 5;
        int numberOfProducers = 2;
        int numberOfConsumers = 3;
        int ordersPerProducer = 50;

        // Create a bounded buffer with capacity 5
        OrderQueue queue = new BoundedBuffer(capacity);

        // Shared counter for processed orders
        AtomicInteger processedCount = new AtomicInteger(0);

        long start = System.nanoTime();

        // Start 3 consumers
        Thread[] consumers = new Thread[numberOfConsumers];

        for (int i = 0; i < numberOfConsumers; i++) {
            consumers[i] = new Thread(
                new Consumers(queue, i + 1, processedCount)
            );
            consumers[i].start();
        }

        // Start 2 producers, each producing 50 orders
        Thread[] producers = new Thread[numberOfProducers];

        for (int i = 0; i < numberOfProducers; i++) {
            producers[i] = new Thread(
                new Producer(queue, ordersPerProducer, numberOfConsumers, i + 1)
            );
            producers[i].start();
        }

        // Wait until both producers finish
        for (Thread producer : producers) {
            producer.join();
        }

        // Send one poison pill to each consumer
        for (int i = 0; i < numberOfConsumers; i++) {
            queue.put(new Order(-1, "POISON", 0));
        }

        // Wait until all consumers finish
        for (Thread consumer : consumers) {
            consumer.join();
        }

        long end = System.nanoTime();
        long elapsedMs = (end - start) / 1_000_000;

        // Check the final result
        int total = processedCount.get();

        System.out.println("Total orders processed: "
                + total + " in " + elapsedMs + " ms");

        if (total != 100) {
            throw new AssertionError(
                "Expected 100 orders, got " + total
            );
        }

        System.out.println("Test passed!");
    }
}
