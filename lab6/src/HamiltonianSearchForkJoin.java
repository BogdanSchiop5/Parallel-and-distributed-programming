import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicBoolean;

public class HamiltonianSearchForkJoin extends RecursiveTask<List<Integer>> {
    private final DirectedGraph graph;
    private final int current;
    private final List<Integer> path;
    private final int startNode;
    private final AtomicBoolean solutionFound;

    public HamiltonianSearchForkJoin(DirectedGraph graph, int current, List<Integer> path, int startNode, AtomicBoolean solutionFound) {
        this.graph = graph;
        this.current = current;
        this.path = path;
        this.startNode = startNode;
        this.solutionFound = solutionFound;
    }

    @Override
    protected List<Integer> compute() {
        if (solutionFound.get()) return null;

        if (path.size() == graph.getNumVertices()) {
            if (graph.getNeighbors(current).contains(startNode)) {
                solutionFound.set(true);
                return new ArrayList<>(path);
            }
            return null;
        }

        List<HamiltonianSearchForkJoin> tasks = new ArrayList<>();

        for (int neighbor : graph.getNeighbors(current)) {
            if (!path.contains(neighbor)) {
                List<Integer> newPath = new ArrayList<>(path);
                newPath.add(neighbor);
                tasks.add(new HamiltonianSearchForkJoin(graph, neighbor, newPath, startNode, solutionFound));
            }
        }

        for (HamiltonianSearchForkJoin task : tasks) {
            task.fork();
        }

        for (HamiltonianSearchForkJoin task : tasks) {
            List<Integer> result = task.join();
            if (result != null) {
                return result;
            }
        }

        return null;
    }
}