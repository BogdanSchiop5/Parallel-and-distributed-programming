package Model;

public class Producer extends Thread{
    private final double[] vectorA;
    private final double[] vectorB;
    private final SharedQueue sharedQueue;

    public Producer(double[] vectorA, double[] vectorB, SharedQueue sharedQueue) {
        this.vectorA = vectorA;
        this.vectorB = vectorB;
        this.sharedQueue = sharedQueue;
    }

    @Override
    public void run() {
        if (vectorA.length != vectorB.length) {
            System.err.println("Vectors must have the same length.");
            sharedQueue.setProducerFinished();
            return;
        }

        try {
            for (int i = 0; i < vectorA.length; i++) {
                double product = vectorA[i] * vectorB[i];
                sharedQueue.put(product);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            sharedQueue.setProducerFinished();
        }
    }
}