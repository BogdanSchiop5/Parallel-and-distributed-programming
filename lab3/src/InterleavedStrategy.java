public class InterleavedStrategy implements StrategyInterface {
    @Override
    public void execute(int[][] A, int[][] B, int[][] C, int threadId, int numThreads, int R, int P) {
        final int totalElements = R * P;

        for (int i = threadId; i < totalElements; i += numThreads) {
            int row = i / P;
            int col = i % P;
            MatrixComputationHelper.computeSingleElement(A, B, C, row, col, threadId);
        }
    }
}
