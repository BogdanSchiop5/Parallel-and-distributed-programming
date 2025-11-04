public interface StrategyInterface {
    void execute(int[][] A, int[][] B, int[][] C, int threadId, int numThreads, int R, int P);
}
