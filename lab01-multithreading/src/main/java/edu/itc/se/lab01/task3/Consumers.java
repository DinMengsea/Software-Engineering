package edu.itc.se.lab01.task3;
import java.util.concurrent.atomic.AtomicInteger;

public class Consumers implements Runnable{
    private final OrderQueue queue;
    private final int consumerId;
    private final AtomicInteger processedCount;    

    public Consumers(OrderQueue queue, int consumerId, AtomicInteger processedCount){
        this.queue = queue;
        this.consumerId = consumerId;
        this.processedCount = processedCount;
    }

    @Override 
    public void run(){
        try{
            while(true){
                Order order = queue.take();

                //Stop if poison pill is received
                if(order.id() == -1){
                    System.out.println("Consumer " + consumerId + " received poison pill. Stopping.");
                    break;
                }
                Thread.sleep(
                    java.util.concurrent.ThreadLocalRandom.current().nextInt(5,31)
                );
                processedCount.incrementAndGet();

                System.out.println("Consumer " + consumerId + " processed order: " + order);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
