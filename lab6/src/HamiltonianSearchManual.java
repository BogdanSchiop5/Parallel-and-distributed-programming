import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class HamiltonianSearchManual {
    private final DirectedGraph graph;
    private final int startNode;
    private final AtomicReference<List<Integer>> resultPath = new AtomicReference<>(null);

    public HamiltonianSearchManual(DirectedGraph graph, int startNode) {
        this.graph = graph;
        this.startNode = startNode;
    }

    public List<Integer> solve(int threadCount) throws InterruptedException {
        List<Integer> initialPath = new ArrayList<>();
        initialPath.add(startNode);

        Thread root = new Thread(() -> search(startNode, initialPath, threadCount));
        root.start();
        root.join();

        return resultPath.get();
    }

    private void search(int current, List<Integer> path, int threadsAvailable) {
        if (resultPath.get() != null) return;

        if (path.size() == graph.getNumVertices()) {
            if (graph.getNeighbors(current).contains(startNode)) {
                resultPath.compareAndSet(null, new ArrayList<>(path));
            }
            return;
        }

        List<Integer> validNeighbors = new ArrayList<>();
        for (int neighbor : graph.getNeighbors(current)) {
            if (!path.contains(neighbor)) {
                validNeighbors.add(neighbor);
            }
        }

        if (validNeighbors.isEmpty()) return;

        if (threadsAvailable <= 1) {
            for (int neighbor : validNeighbors) {
                if (resultPath.get() != null) return;
                path.add(neighbor);
                search(neighbor, path, 1);
                path.remove(path.size() - 1);
            }
            return;
        }

        int baseThreads = threadsAvailable / validNeighbors.size();
        int remainder = threadsAvailable % validNeighbors.size();

        List<Thread> childThreads = new ArrayList<>();

        for (int i = 0; i < validNeighbors.size(); i++) {
            int neighbor = validNeighbors.get(i);

            int allocatedThreads = baseThreads + (i < remainder ? 1 : 0);

                List<Integer> newPath = new ArrayList<>(path);
                newPath.add(neighbor);

                Thread t = new Thread(() -> search(neighbor, newPath, allocatedThreads));
                childThreads.add(t);
                t.start();
        }

        for (Thread t : childThreads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}