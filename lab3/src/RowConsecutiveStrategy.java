public class RowConsecutiveStrategy implements StrategyInterface {

    @Override
    public void execute(int[][] A, int[][] B, int[][] C, int threadId, int numThreads, int R, int P) {
        final int totalElements = R * P;

        final int elementsPerThread = totalElements / numThreads;
        final int remainder = totalElements % numThreads;

        int startIndex = threadId * elementsPerThread + Math.min(threadId, remainder);
        int endIndex = startIndex + elementsPerThread + (threadId < remainder ? 1 : 0);

        for (int i = startIndex; i < endIndex; i++) {
            int row = i / P;
            int col = i % P;
            MatrixComputationHelper.computeSingleElement(A, B, C, row, col, threadId);
        }
    }

}
