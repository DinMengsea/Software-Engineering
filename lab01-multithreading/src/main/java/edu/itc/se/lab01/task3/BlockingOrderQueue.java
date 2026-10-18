
package edu.itc.se.lab01.task3;

import java.util.concurrent.ArrayBlockingQueue;

public class BlockingOrderQueue implements OrderQueue {
    private final ArrayBlockingQueue<Order> queue;

    public BlockingOrderQueue(int capacity) {
        this.queue = new ArrayBlockingQueue<>(capacity);
    }

    @Override
    public void put(Order order) throws InterruptedException {
        queue.put(order);
    }

    @Override
    public Order take() throws InterruptedException {
        return queue.take();
    }
}
