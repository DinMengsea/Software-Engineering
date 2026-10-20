
package edu.itc.se.lab01.task5;

public class OrderedBank {

    static boolean transfer(
            Bank.Account from,
            Bank.Account to,
            long amount) {

        // Do not transfer to the same account
        if (from == to) {
            return false;
        }

        // Always acquire locks in ascending account ID order
        Bank.Account first =
            from.id < to.id ? from : to;

        Bank.Account second =
            from.id < to.id ? to : from;

        synchronized (first) {
            synchronized (second) {
                if (from.balance() >= amount) {
                    from.withdraw(amount);
                    to.deposit(amount);
                    return true;
                }

                return false;
            }
        }
    }
}
