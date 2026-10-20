package edu.itc.se.lab01.task3;

public class BoundedBuffer implements OrderQueue {
    private final java.util.ArrayDeque<Order> items = new java.util.ArrayDeque<>();
    private final int capacity;
    public BoundedBuffer(int capacity) { this.capacity = capacity; }

    @Override public synchronized void put(Order order) throws InterruptedException {
        while(items.size() == capacity) wait();
        items.addLast(order);
        notifyAll();
    }
    @Override public synchronized Order take() throws InterruptedException {
        while(items.isEmpty()) wait();
        Order order = items.removeFirst();
        notifyAll();
        return order;
    }

    // 7. Experiment: change notifyAll() to notify() and the while to an if. 
    // Describe in README.md what breaks and why (hint: run it 20 times in a loop).

    // @Override
    // public synchronized void put(Order order) throws InterruptedException {
    //     if (items.size() == capacity) {
    //         wait();
    //     }

    //     items.addLast(order);
    //     notify();
    // }

    // @Override
    // public synchronized Order take() throws InterruptedException {
    //     if (items.isEmpty()) {
    //         wait();
    //     }

    //     Order order = items.removeFirst();
    //     notify();
    //     return order;
    // }

}
