package Model;

import java.util.LinkedList;
import java.util.Queue;

public class SharedQueue {
    private final Queue<Double> queue;
    private final int capacity;
    private volatile boolean producerFinished = false;

    public SharedQueue(int capacity) {
        this.queue = new LinkedList<>();
        this.capacity = capacity;
    }

    public synchronized void put(double product) throws InterruptedException {
        while(queue.size() >= capacity){
            wait();
        }

        queue.add(product);
        notifyAll();
    }

    public  synchronized Double take() throws InterruptedException {
        while(queue.isEmpty()){
            if(producerFinished){
                return Double.NaN;
            }
            wait();
        }
        Double product = queue.poll();
        notifyAll();
        return product;
    }

    public synchronized void setProducerFinished() {
        this.producerFinished = true;
        notifyAll();
    }
}
