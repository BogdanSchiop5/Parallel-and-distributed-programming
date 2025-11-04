public class MatrixComputationHelper {
    public static boolean DEBUG_PRINT_ENABLED = true;

    public static void computeSingleElement(int[][] A, int[][] B, int[][] C, int row, int col, int threadId) {
        int N = A[0].length;
        long sum = 0;

        for (int k = 0; k < N; k++) {
            sum += (long) A[row][k] * B[k][col];
        }

        C[row][col] = (int) sum;

        if (DEBUG_PRINT_ENABLED) {
            System.out.printf("   (%d,%d) computed by Thread [%d]\n", row, col, threadId);
        }
    }
}
