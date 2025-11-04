/*
2. Bank accounts
At a bank, we have to keep track of the balance of some accounts.

We have concurrently run transfer operations, to be executer on multiple threads. Each operation transfers a given amount of money from one account to someother account.

From time to time, as well as at the end of the program, a consistency check shall be executed. It shall verify that the total amount of money in all accounts is the same as in the beginning.

Two transaction involving distinct accounts must be able to proceed independently (without having to wait for the same mutex).
 */

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import Model.*;

public class Main {
    public static void main() {
        int numAccounts = 5;
        int initialBalance = 1000;
        int numThreads = 2;
        int operationsPerThread = 500;

        List<Account> accounts = new ArrayList<>();
        for (int i = 0; i < numAccounts; i++) {
            accounts.add(new Account(initialBalance));
        }

        int initialTotal = 0;
        for (Account account : accounts) {
            initialTotal += account.getBalance();
        }
        System.out.println("Initial balance: " + initialTotal);

        List<Thread> threads = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < numThreads; i++) {
            Thread t = new Thread(new Transfer(accounts, operationsPerThread), "Transfer- " + i);
            threads.add(t);
            t.start();
        }

        for(Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {}
        }

        long endTime = System.currentTimeMillis();
        double elapsedMs = (endTime - startTime);

        System.out.printf("Elapsed time: %.2f ms%n", elapsedMs);

        int finalBalance = 0;
        for(Account account : accounts) {
            finalBalance += account.getBalance();
        }
        System.out.println("Final balance: " + finalBalance);

        if(initialTotal == finalBalance) {
            System.out.println("Check passed!");
        }
        else{
            System.out.println("Check failed!");
        }
    }
}