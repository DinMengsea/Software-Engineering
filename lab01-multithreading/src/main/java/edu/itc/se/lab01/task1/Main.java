
package edu.itc.se.lab01.task1;

public class Main {
    public static void main(String[] args)
            throws InterruptedException {

        boolean sequential = args.length > 0
                && args[0].equals("--sequential");

        // Way 1: Extend Thread
        Thread apollo = new Countdown("apollo");

        // Way 2: Implement Runnable
        Thread gemini = new Thread(
                new CountdownTask(), "gemini"
        );
        

        // Way 3: Anonymous Thread class
        Thread mercury = new Thread("mercury") {
            @Override
            public void run() {
                new CountdownTask().run();
            }
        };

        // Way 4: Virtual Thread
        Thread artemis = Thread.ofVirtual()
                .name("artemis")
                .unstarted(new CountdownTask());

        Thread[] threads = {
                apollo, gemini, mercury, artemis
        };

        if (sequential) {
            // Run one thread at a time
            for (Thread t : threads) {
                t.start();
                t.join();
            }
        } else {
            // Start all four threads
            for (Thread t : threads) {
                t.start();
            }

            System.out.println(
                    "Mission control: all launched"
            );

            // Wait for all four threads
            for (Thread t : threads) {
                t.join();
            }
        }

        if (sequential) {
            System.out.println(
                    "Mission control: all launched"
            );
        }

        System.out.println(
                "Mission control: all landed"
        );

        // Print information about each thread
        for (Thread t : threads) {
            System.out.println(
                    "Name: " + t.getName()
                    + ", Virtual: " + t.isVirtual()
                    + ", State: " + t.getState()
            );
        }
    }
}

class Countdown extends Thread {

    public Countdown(String name) {
        super(name);
    }

    @Override
    public void run() {
        countdown();
    }

    private void countdown() {
        for (int i = 3; i >= 1; i--) {
            System.out.println(
                    Thread.currentThread().getName()
                    + ": " + i
            );

            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        System.out.println(
                Thread.currentThread().getName()
                + ": liftoff!"
        );
    }
}

class CountdownTask implements Runnable {

    @Override
    public void run() {
        for (int i = 3; i >= 1; i--) {
            System.out.println(
                    Thread.currentThread().getName()
                    + ": " + i
            );

            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        System.out.println(
                Thread.currentThread().getName()
                + ": liftoff!"
        );
    }
}
