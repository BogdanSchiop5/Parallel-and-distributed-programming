import java.util.concurrent.*;
import java.util.Arrays;

public class Multiplication {

    public static Polynomial multiplySequentialRegular(Polynomial p1, Polynomial p2) {
        int size1 = p1.getLength();
        int size2 = p2.getLength();
        int[] result = new int[size1 + size2 - 1];

        for (int i = 0; i < size1; i++) {
            for (int j = 0; j < size2; j++) {
                result[i + j] += p1.getCoeffs()[i] * p2.getCoeffs()[j];
            }
        }
        return new Polynomial(result);
    }

    public static Polynomial multiplyParallelRegular(Polynomial p1, Polynomial p2) throws InterruptedException {
        int size1 = p1.getLength();
        int size2 = p2.getLength();
        int resultSize = size1 + size2 - 1;
        int[] result = new int[resultSize];

        int numThreads = Runtime.getRuntime().availableProcessors();
        Thread[] threads = new Thread[numThreads];

        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;

            threads[t] = new Thread(() -> {
                for (int k = threadId; k < resultSize; k += numThreads) {
                    int sum = 0;
                    for (int i = 0; i < size1; i++) {
                        int j = k - i;
                        if (j >= 0 && j < size2) {
                            sum += p1.getCoeffs()[i] * p2.getCoeffs()[j];
                        }
                    }
                    result[k] = sum;
                }
            });

            threads[t].start();
        }

        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        return new Polynomial(result);
    }

    public static Polynomial multiplySequentialKaratsuba(Polynomial p1, Polynomial p2) {
        if (p1.getLength() < 64 || p2.getLength() < 64) {
            return multiplySequentialRegular(p1, p2);
        }

        int n = Math.max(p1.getLength(), p2.getLength());
        int len = n / 2;

        Polynomial low1 = new Polynomial(Arrays.copyOfRange(p1.getCoeffs(), 0, Math.min(len, p1.getLength())));
        Polynomial high1 = new Polynomial(Arrays.copyOfRange(p1.getCoeffs(), Math.min(len, p1.getLength()), p1.getLength()));

        Polynomial low2 = new Polynomial(Arrays.copyOfRange(p2.getCoeffs(), 0, Math.min(len, p2.getLength())));
        Polynomial high2 = new Polynomial(Arrays.copyOfRange(p2.getCoeffs(), Math.min(len, p2.getLength()), p2.getLength()));

        Polynomial z0 = multiplySequentialKaratsuba(low1, low2);
        Polynomial z2 = multiplySequentialKaratsuba(high1, high2);
        Polynomial z1 = multiplySequentialKaratsuba(Polynomial.add(low1, high1), Polynomial.add(low2, high2));

        Polynomial middle = Polynomial.subtract(Polynomial.subtract(z1, z0), z2);

        Polynomial r1 = z2.shift(2 * len);
        Polynomial r2 = middle.shift(len);

        return Polynomial.add(Polynomial.add(r1, r2), z0);
    }

    public static Polynomial multiplyParallelKaratsuba(Polynomial p1, Polynomial p2, int depth) {
        if (depth > 4 || p1.getLength() < 64 || p2.getLength() < 64) {
            return multiplySequentialKaratsuba(p1, p2);
        }

        int n = Math.max(p1.getLength(), p2.getLength());
        int len = n / 2;

        Polynomial low1 = new Polynomial(Arrays.copyOfRange(p1.getCoeffs(), 0, Math.min(len, p1.getLength())));
        Polynomial high1 = new Polynomial(Arrays.copyOfRange(p1.getCoeffs(), Math.min(len, p1.getLength()), p1.getLength()));

        Polynomial low2 = new Polynomial(Arrays.copyOfRange(p2.getCoeffs(), 0, Math.min(len, p2.getLength())));
        Polynomial high2 = new Polynomial(Arrays.copyOfRange(p2.getCoeffs(), Math.min(len, p2.getLength()), p2.getLength()));

        RecursiveTask<Polynomial> taskZ2 = new RecursiveTask<>() {
            @Override
            protected Polynomial compute() {
                return multiplyParallelKaratsuba(high1, high2, depth + 1);
            }
        };
        taskZ2.fork();

        RecursiveTask<Polynomial> taskZ1 = new RecursiveTask<>() {
            @Override
            protected Polynomial compute() {
                return multiplyParallelKaratsuba(Polynomial.add(low1, high1), Polynomial.add(low2, high2), depth + 1);
            }
        };
        taskZ1.fork();

        Polynomial z0 = multiplyParallelKaratsuba(low1, low2, depth + 1);

        Polynomial z1 = taskZ1.join();
        Polynomial z2 = taskZ2.join();

        Polynomial middle = Polynomial.subtract(Polynomial.subtract(z1, z0), z2);
        Polynomial r1 = z2.shift(2 * len);
        Polynomial r2 = middle.shift(len);

        return Polynomial.add(Polynomial.add(r1, r2), z0);
    }
}