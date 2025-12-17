import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DirectedGraph {
    private final int numVertices;
    private final List<List<Integer>> adjList;

    public DirectedGraph(int numVertices) {
        this.numVertices = numVertices;
        this.adjList = new ArrayList<>(numVertices);
        for (int i = 0; i < numVertices; i++) {
            this.adjList.add(new ArrayList<>());
        }
    }

    public void addEdge(int src, int dest) {
        if (!adjList.get(src).contains(dest)) {
            adjList.get(src).add(dest);
        }
    }

    public List<Integer> getNeighbors(int vertex) {
        return Collections.unmodifiableList(adjList.get(vertex));
    }

    public int getNumVertices() {
        return numVertices;
    }
}