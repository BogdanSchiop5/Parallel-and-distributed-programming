import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main {

    public static void main(String[] args) throws InterruptedException {
        int NUM_VERTICES = 100;
        int MANUAL_THREAD_COUNT = 8;

        DirectedGraph g = new DirectedGraph(NUM_VERTICES);

        for (int i = 0; i < NUM_VERTICES; i++) {
            g.addEdge(i, (i + 1) % NUM_VERTICES);
        }

        Random rand = new Random(123);
        for (int i = 0; i < NUM_VERTICES; i++) {
            for (int j = 0; j < 3; j++) {
                int target = rand.nextInt(NUM_VERTICES);
                if (target != i) g.addEdge(i, target);
            }
        }


        System.out.println("Basic Thread Search (Budget: " + MANUAL_THREAD_COUNT + ")...");

        long startManual = System.currentTimeMillis();

        HamiltonianSearchManual manualSolver = new HamiltonianSearchManual(g, 0);
        List<Integer> pathManual = manualSolver.solve(MANUAL_THREAD_COUNT);

        long endManual = System.currentTimeMillis();

        System.out.println("Basic Search Time: " + (endManual - startManual) + " ms");
        System.out.println("Result: " + (pathManual != null ? "Found Cycle (Length " + pathManual.size() + ")" : "No Cycle Found"));
        System.out.println("--------------------------------------------------");

        System.out.println("Starting ForkJoin Search...");

        long startFJ = System.currentTimeMillis();

        ForkJoinPool pool = new ForkJoinPool();
        List<Integer> initialPath = new ArrayList<>();
        initialPath.add(0);
        AtomicBoolean flag = new AtomicBoolean(false);

        HamiltonianSearchForkJoin task = new HamiltonianSearchForkJoin(g, 0, initialPath, 0, flag);
        List<Integer> pathFJ = pool.invoke(task);

        long endFJ = System.currentTimeMillis();

        System.out.println("ForkJoin Search Time: " + (endFJ - startFJ) + " ms");
        System.out.println("Result: " + (pathFJ != null ? "Found Cycle (Length " + pathFJ.size() + ")" : "No Cycle Found"));
    }
}