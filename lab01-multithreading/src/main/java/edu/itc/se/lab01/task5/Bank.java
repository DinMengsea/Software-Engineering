
package edu.itc.se.lab01.task5;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.ThreadLocalRandom;

public class Bank {

    static final class Account {
        final int id;
        private long balance;

        Account(int id, long balance) {
            this.id = id;
            this.balance = balance;
        }

        long balance() {
            return balance;
        }

        void withdraw(long amount) {
            balance -= amount;
        }

        void deposit(long amount) {
            balance += amount;
        }
    }

    // Intentionally unsafe: demonstrates deadlock
    static void transfer(
            Account from, Account to, long amount) {

        synchronized (from) {
            synchronized (to) {
                if (from.balance() >= amount) {
                    from.withdraw(amount);
                    to.deposit(amount);
                }
            }
        }
    }

    // Detect deadlocked threads
    static void startWatchdog() {
        Thread watchdog = new Thread(() -> {
            ThreadMXBean bean =
                ManagementFactory.getThreadMXBean();

            while (!Thread.currentThread().isInterrupted()) {
                long[] ids = bean.findDeadlockedThreads();

                if (ids != null) {
                    System.out.println(
                        "[watchdog] DEADLOCK detected between:"
                    );

                    for (var info :
                            bean.getThreadInfo(ids, true, true)) {
                        System.out.println(
                            "  " + info.getThreadName()
                        );
                    }

                    return;
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "watchdog");

        watchdog.setDaemon(true);
        watchdog.start();
    }

    public static void main(String[] args) {
        Account[] accounts = new Account[4];

        for (int i = 0; i < accounts.length; i++) {
            accounts[i] = new Account(i, 1000);
        }

        startWatchdog();

        Thread[] workers = new Thread[8];

        for (int w = 0; w < workers.length; w++) {
            workers[w] = new Thread(() -> {
                var random = ThreadLocalRandom.current();

                for (int i = 0; i < 100_000; i++) {
                    Account from =
                        accounts[random.nextInt(accounts.length)];

                    Account to =
                        accounts[random.nextInt(accounts.length)];

                    if (from != to) {
                        transfer(from, to, random.nextInt(1, 50));
                    }
                }
            }, "transfer-" + w);

            workers[w].start();
        }

        for (Thread worker : workers) {
            try {
                worker.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        long total = 0;

        for (Account account : accounts) {
            total += account.balance();
        }

        System.out.println("total = " + total);
    }
}
