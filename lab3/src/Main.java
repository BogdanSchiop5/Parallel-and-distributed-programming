import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
public class Main {

    private static final boolean DEBUG_PRINT_ENABLED = true;


    public static long multiply(int[][] A, int[][] B, int[][] C, int numThreads, StrategyInterface strategy) {
        if (numThreads <= 0) numThreads = 1;

        // Array to hold references to the worker threads
        Thread[] threads = new Thread[numThreads];

        long startTime = System.nanoTime();

        try {
            for (int i = 0; i < numThreads; i++) {
                Runnable worker = new MatrixWorker(A, B, C, i, numThreads, strategy);
                threads[i] = new Thread(worker, "Worker-" + i);
                threads[i].start();
            }

            for (int i = 0; i < numThreads; i++) {
                threads[i].join();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Thread execution interrupted: " + e.getMessage());
        }

        long endTime = System.nanoTime();
        return TimeUnit.NANOSECONDS.toMillis(endTime - startTime);
    }

    private static int[][] generateRandomMatrix(int rows, int cols) {
        int[][] matrix = new int[rows][cols];
        Random rand = new Random();
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                matrix[i][j] = rand.nextInt(10); // Values between 0 and 9
            }
        }
        return matrix;
    }

    private static void printMatrix(int[][] matrix, String name) {
        System.out.println("--- " + name + " (" + matrix.length + "x" + matrix[0].length + ") ---");
        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix[0].length; j++) {
                System.out.printf("%4d", matrix[i][j]);
            }
            System.out.println();
        }
    }

    private static void clearMatrix(int[][] matrix) {
        for (int[] row : matrix) {
            Arrays.fill(row, 0);
        }
    }

    public static void main(String[] args) {
        MatrixComputationHelper.DEBUG_PRINT_ENABLED = DEBUG_PRINT_ENABLED;

        final int R_SMALL = 9;
        final int N_SMALL = 3;
        final int P_SMALL = 9;
        final int K_SMALL = 4;

        if (DEBUG_PRINT_ENABLED) {


            int[][] A_small = generateRandomMatrix(R_SMALL, N_SMALL);
            int[][] B_small = generateRandomMatrix(N_SMALL, P_SMALL);
            int[][] C_small = new int[R_SMALL][P_SMALL];

            printMatrix(A_small, "Matrix A");
            printMatrix(B_small, "Matrix B");

            // Strategy 1: ROW_CONSECUTIVE
            System.out.println("\n-- Strategy: ROW_CONSECUTIVE--");
            multiply(A_small, B_small, C_small, K_SMALL, new RowConsecutiveStrategy());
            printMatrix(C_small, "Result C (ROW_CONSECUTIVE)");

            clearMatrix(C_small);

            //Strategy 2: COL_CONSECUTIVE
            System.out.println("\n-- Strategy: COL_CONSECUTIVE--");
            multiply(A_small, B_small, C_small, K_SMALL, new CollumnConsecutiveStrategy());
            printMatrix(C_small, "Result C (COL_CONSECUTIVE)");

            clearMatrix(C_small);

            // Strategy 3: INTERLEAVED
            System.out.println("\n-- Strategy: INTERLEAVED--");
            multiply(A_small, B_small, C_small, K_SMALL, new InterleavedStrategy());
            printMatrix(C_small, "Result C (INTERLEAVED)");
        }

        MatrixComputationHelper.DEBUG_PRINT_ENABLED = false;

        final int R_BIG = 1000;
        final int N_BIG = 1000;
        final int P_BIG = 1000;
        final int[] THREAD_COUNTS = {1, 2, 4, 8, 30, 100, 10000};

        int[][] A_big = generateRandomMatrix(R_BIG, N_BIG);
        int[][] B_big = generateRandomMatrix(N_BIG, P_BIG);
        int[][] C_big = new int[R_BIG][P_BIG];

        System.out.printf("Matrix Size: %dx%dx%d\n", R_BIG, N_BIG, P_BIG);
        System.out.println("-------------------------------------------------------------------------");
        System.out.println("| Threads | ROW_CONSECUTIVE (ms) | COL_CONSECUTIVE (ms) | INTERLEAVED (ms) |\n");
        System.out.println("-------------------------------------------------------------------------");

        StrategyInterface rowStrategy = new RowConsecutiveStrategy();
        StrategyInterface colStrategy = new CollumnConsecutiveStrategy();
        StrategyInterface intStrategy = new InterleavedStrategy();

        for (int K : THREAD_COUNTS) {
            long timeRow, timeCol, timeInt;

            // Strategy 1: ROW_CONSECUTIVE
            clearMatrix(C_big);
            timeRow = multiply(A_big, B_big, C_big, K, rowStrategy);

            // Strategy 2: COL_CONSECUTIVE
            clearMatrix(C_big);
            timeCol = multiply(A_big, B_big, C_big, K, colStrategy);

            // Strategy 3: INTERLEAVED
            clearMatrix(C_big);
            timeInt = multiply(A_big, B_big, C_big, K, intStrategy);

            System.out.printf("| %7d | %20d | %20d | %16d |\n", K, timeRow, timeCol, timeInt);
        }
        System.out.println("-------------------------------------------------------------------------");

    }
}
