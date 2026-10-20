
package edu.itc.se.lab01.task5;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public class TryLockBank {

    static final class Account {
        final int id;
        long balance;
        final ReentrantLock lock = new ReentrantLock();

        Account(int id, long balance) {
            this.id = id;
            this.balance = balance;
        }
    }

    static final AtomicLong retries = new AtomicLong();

    static boolean transfer(
            Account from,
            Account to,
            long amount) throws InterruptedException {

        if (from == to) {
            return false;
        }

        boolean gotFrom = false;
        boolean gotTo = false;

        try {
            gotFrom = from.lock.tryLock(
                    10, TimeUnit.MILLISECONDS
            );

            if (gotFrom) {
                gotTo = to.lock.tryLock(
                        10, TimeUnit.MILLISECONDS
                );
            }

            if (!gotFrom || !gotTo) {
                retries.incrementAndGet();
                return false;
            }

            if (from.balance < amount) {
                return false;
            }

            from.balance -= amount;
            to.balance += amount;

            return true;

        } finally {
            if (gotTo) {
                to.lock.unlock();
            }

            if (gotFrom) {
                from.lock.unlock();
            }
        }
    }
}
