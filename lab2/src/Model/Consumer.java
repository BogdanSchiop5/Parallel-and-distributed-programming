package Model;

import java.util.concurrent.atomic.AtomicLong;

public class Consumer extends Thread{
    private final SharedQueue sharedQueue;
    private final AtomicLong totalSumHolder;

    public Consumer(SharedQueue sharedQueue, AtomicLong totalSumHolder) {
        this.sharedQueue = sharedQueue;
        this.totalSumHolder = totalSumHolder;
    }

    @Override
    public void run() {
        double scalarProduct = 0;
        try {
            while (true) {
                Double product = sharedQueue.take();

                if (product.isNaN()) {
                    break;
                }

                scalarProduct += product;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            totalSumHolder.set(Double.doubleToRawLongBits(scalarProduct));
        }
    }
}
