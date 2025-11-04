package Model;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class Transfer implements Runnable {
    private final List<Account> accounts;
    private final int numOperations;
    private final Random rand = new Random();

    public Transfer(List<Account> accounts, int numOperations) {
        this.accounts = accounts;
        this.numOperations = numOperations;
    }

    @Override
    public void run() {
        for (int i = 0; i < numOperations; i++) {
            Account from = accounts.get(rand.nextInt(accounts.size()));
            Account to = accounts.get(rand.nextInt(accounts.size()));
            int amount = rand.nextInt(200);
            if (from == to) continue;

            try {
                while (true) {
                    if (from.getLock().tryLock(10, TimeUnit.MILLISECONDS)) {
                        try {
                            if (to.getLock().tryLock(10, TimeUnit.MILLISECONDS)) {
                                try {
                                    if (from.getBalance() >= amount) {
                                        from.withdraw(amount);
                                        to.deposit(amount);
                                    }
                                    break;
                                } finally {
                                    to.getLock().unlock();
                                }
                            }
                        } finally {
                            from.getLock().unlock();
                        }
                    }
                    Thread.sleep(1);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            if (i % 100 == 0 && i > 0) {
                checkConsistency();
            }
        }
    }

    private void checkConsistency() {
        int total = 0;
        accounts.forEach(acc -> acc.getLock().lock());
        try {
            for (Account acc : accounts) {
                total += acc.getBalance();
            }
        } finally {
            accounts.forEach(acc -> acc.getLock().unlock());
        }
    }
}
