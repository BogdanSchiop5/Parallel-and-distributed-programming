/**
 * MatrixWorker is the Runnable task executed by each thread.
 * It is initialized with a specific MatrixSplitStrategy and delegates
 * the work partitioning and computation to it.
 */
public class MatrixWorker implements Runnable {
    private final int[][] A, B, C;
    private final int threadId;
    private final int numThreads;
    private final StrategyInterface strategy;
    private final int R, P; // R=rows in C, P=cols in C (A: RxN, B: NxP)

    public MatrixWorker(int[][] A, int[][] B, int[][] C, int threadId, int numThreads, StrategyInterface strategy) {
        this.A = A;
        this.B = B;
        this.C = C;
        this.threadId = threadId;
        this.numThreads = numThreads;
        this.strategy = strategy;
        this.R = A.length;
        this.P = B[0].length;
    }

    @Override
    public void run() {
        strategy.execute(A, B, C, threadId, numThreads, R, P);
    }
}
