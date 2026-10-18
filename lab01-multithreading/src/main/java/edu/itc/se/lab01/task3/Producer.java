package edu.itc.se.lab01.task3;
import java.util.concurrent.ThreadLocalRandom;

public class Producer implements Runnable {
    private final OrderQueue queue;
    private final int numberOfOrders;
    private final int numberOfConsumers;
    private final int producerId;

    public Producer(OrderQueue queue, int numberOfOrders, int numberOfConsumers, int producerId) {
        this.queue = queue;
        this.numberOfOrders = numberOfOrders;
        this.numberOfConsumers = numberOfConsumers;
        this.producerId = producerId;
    }

    @Override 

    public void run(){
        try{
            for(int i = 1; i<= numberOfOrders; i++){
                Order order = new Order((producerId - 1) * numberOfOrders + i, "item-" +(producerId - 1)* numberOfOrders + i, ThreadLocalRandom.current().nextInt(1, 6));
                queue.put(order);
                Thread.sleep(ThreadLocalRandom.current().nextInt(21));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
}
