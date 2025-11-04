public class CollumnConsecutiveStrategy implements StrategyInterface{

    @Override
    public void execute(int[][] A, int[][] B, int[][] C, int threadId, int numThreads, int R, int P) {

    final int totalElements = R * P;

    // Calculate block size for this thread, ensuring the remainder is handled.
    final int elementsPerThread = totalElements / numThreads;
    final int remainder = totalElements % numThreads;

    // Calculate the start and end indices for this thread
    int startIndex = threadId * elementsPerThread + Math.min(threadId, remainder);
    int endIndex = startIndex + elementsPerThread + (threadId < remainder ? 1 : 0);

        for (int i = startIndex; i < endIndex; i++) {
        // Column-major mapping:
        int row = i % R; // The remainder after dividing by R (total rows) gives the row index
        int col = i / R; // The quotient gives the column index
        MatrixComputationHelper.computeSingleElement(A, B, C, row, col, threadId);
        }
    }
}
