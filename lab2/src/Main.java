import Model.Consumer;
import Model.Producer;
import Model.SharedQueue;

import java.util.concurrent.atomic.AtomicLong;

public class Main{

    public static void main(String[] args) {
        int vectorSize = 100000;
        int[] queueSizes = {100000, 10000, 1000, 100, 10, 1};
        System.out.println("Vector size: " + vectorSize);
        System.out.println("Queue Size | Time (ms) | Result");
        System.out.println("----------------------------------------");

        double[] vectorA = generateVector(vectorSize, 1.0);
        double[] vectorB = generateVector(vectorSize, 2.0);
        double expectedResult = computeSequentialScalarProduct(vectorA, vectorB);

        for (int queueSize : queueSizes) {
            AtomicLong resultHolder = new AtomicLong();
            long timeTaken = runTest(vectorA, vectorB, queueSize, resultHolder);

            double actualResult = Double.longBitsToDouble(resultHolder.get());

            String result = (Math.abs(actualResult - expectedResult) < 1e-9) ?
                    String.format("%.2f (CORRECT)", actualResult) :
                    String.format("%.2f (ERROR - %.2f)", actualResult, expectedResult);

            System.out.printf("%10d | %9d | %s%n", queueSize, timeTaken, result);
        }

    }

    private static double[] generateVector(int size, double base) {
        double[] vector = new double[size];
        for (int i = 0; i < size; i++) {
            vector[i] = base + (i * 0.000001);
        }
        return vector;
    }

    private static double computeSequentialScalarProduct(double[] vectorA, double[] vectorB) {
        double sum = 0;
        for (int i = 0; i < vectorA.length; i++) {
            sum += vectorA[i] * vectorB[i];
        }
        return sum;
    }

    private static long runTest(double[] vectorA, double[] vectorB, int queueSize, AtomicLong resultHolder) {
        SharedQueue sharedQueue = new SharedQueue(queueSize);
        Producer producer = new Producer(vectorA, vectorB, sharedQueue);
        Consumer consumer = new Consumer(sharedQueue, resultHolder);

        long startTime = System.currentTimeMillis();

        producer.start();
        consumer.start();

        try {
            producer.join();
            consumer.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return System.currentTimeMillis() - startTime;
    }
}
