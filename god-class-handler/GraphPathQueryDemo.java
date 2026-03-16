import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class GraphPathQueryDemo {

    public static void main(String[] args) {
        Graph graph = buildDemoGraph();
        GraphQueryEngine engine = new GraphQueryEngine(graph);

        printGraphSummaries(engine);

        if (args.length > 0) {
            for (String query : args) {
                runQuery(engine, query);
            }
            return;
        }

        runQuery(engine, "A-B-C");
        runQuery(engine, "A-B-C,B-D");
    }

    private static Graph buildDemoGraph() {
        Graph graph = new Graph();

        graph.addNode("A", mapOf("name", "Node-A", "type", "Start"));
        graph.addNode("B", mapOf("name", "Node-B", "type", "Hub"));
        graph.addNode("C", mapOf("name", "Node-C", "type", "Service"));
        graph.addNode("D", mapOf("name", "Node-D", "type", "Service"));
        graph.addNode("E", mapOf("name", "Node-E", "type", "Terminal"));

        graph.addEdge("e1", "A", "B", "depends_on", 1.0);
        graph.addEdge("e2", "B", "C", "calls", 2.5);
        graph.addEdge("e3", "B", "D", "calls", 1.5);
        graph.addEdge("e4", "C", "E", "writes_to", 3.0);
        graph.addEdge("e5", "D", "E", "writes_to", 2.0);

        return graph;
    }

    private static void printGraphSummaries(GraphQueryEngine engine) {
        System.out.println("=== 节点汇总（全图）===");
        for (NodeAggregate nodeAggregate : engine.getAllNodeAggregates()) {
            System.out.println(nodeAggregate);
        }

        System.out.println("\n=== 边汇总（全图）===");
        for (EdgeAggregate edgeAggregate : engine.getAllEdgeAggregates()) {
            System.out.println(edgeAggregate);
        }
    }

    private static void runQuery(GraphQueryEngine engine, String queryExpression) {
        System.out.println("\n=== 路径查询: " + queryExpression + " ===");
        QueryResult result = engine.query(queryExpression);

        System.out.println("分支数: " + result.branches.size());
        for (PathBranch branch : result.branches) {
            System.out.println("  分支: " + branch);
        }
        System.out.println("总边权重: " + format(result.totalWeight));

        System.out.println("命中节点汇总:");
        for (NodeAggregate aggregate : result.hitNodeAggregates.values()) {
            System.out.println("  " + aggregate);
        }

        System.out.println("命中边汇总:");
        for (EdgeAggregate aggregate : result.hitEdgeAggregates.values()) {
            System.out.println("  " + aggregate);
        }
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static Map<String, String> mapOf(String... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("keyValues length must be even");
        }
        Map<String, String> result = new LinkedHashMap<String, String>();
        for (int i = 0; i < keyValues.length; i += 2) {
            result.put(keyValues[i], keyValues[i + 1]);
        }
        return result;
    }

    static final class Graph {
        private final Map<String, Node> nodes = new LinkedHashMap<String, Node>();
        private final Map<String, List<Edge>> outgoing = new LinkedHashMap<String, List<Edge>>();

        void addNode(String id, Map<String, String> properties) {
            if (nodes.containsKey(id)) {
                throw new IllegalArgumentException("Node already exists: " + id);
            }
            nodes.put(id, new Node(id, properties));
            outgoing.put(id, new ArrayList<Edge>());
        }

        void addEdge(String edgeId, String from, String to, String type, double weight) {
            if (!nodes.containsKey(from) || !nodes.containsKey(to)) {
                throw new IllegalArgumentException("Both edge endpoints must exist. from=" + from + ", to=" + to);
            }
            Edge edge = new Edge(edgeId, from, to, type, weight);
            outgoing.get(from).add(edge);
        }

        Map<String, Node> nodes() {
            return Collections.unmodifiableMap(nodes);
        }

        List<Edge> edges() {
            List<Edge> allEdges = new ArrayList<Edge>();
            for (List<Edge> list : outgoing.values()) {
                allEdges.addAll(list);
            }
            return Collections.unmodifiableList(allEdges);
        }

        List<Edge> outgoing(String nodeId) {
            List<Edge> edges = outgoing.get(nodeId);
            return edges == null ? Collections.<Edge>emptyList() : Collections.unmodifiableList(edges);
        }

        int outDegree(String nodeId) {
            return outgoing(nodeId).size();
        }

        int inDegree(String nodeId) {
            int count = 0;
            for (Edge edge : edges()) {
                if (edge.to.equals(nodeId)) {
                    count++;
                }
            }
            return count;
        }

        double outWeightSum(String nodeId) {
            double sum = 0.0;
            for (Edge edge : outgoing(nodeId)) {
                sum += edge.weight;
            }
            return sum;
        }

        double inWeightSum(String nodeId) {
            double sum = 0.0;
            for (Edge edge : edges()) {
                if (edge.to.equals(nodeId)) {
                    sum += edge.weight;
                }
            }
            return sum;
        }

        Optional<Edge> findEdge(String from, String to) {
            List<Edge> fromEdges = outgoing.get(from);
            if (fromEdges == null) {
                return Optional.empty();
            }
            for (Edge edge : fromEdges) {
                if (edge.to.equals(to)) {
                    return Optional.of(edge);
                }
            }
            return Optional.empty();
        }
    }

    static final class GraphQueryEngine {
        private final Graph graph;

        GraphQueryEngine(Graph graph) {
            this.graph = graph;
        }

        List<NodeAggregate> getAllNodeAggregates() {
            List<NodeAggregate> list = new ArrayList<NodeAggregate>();
            for (Node node : graph.nodes().values()) {
                list.add(toNodeAggregate(node.id, 0));
            }
            return list;
        }

        List<EdgeAggregate> getAllEdgeAggregates() {
            List<EdgeAggregate> list = new ArrayList<EdgeAggregate>();
            for (Edge edge : graph.edges()) {
                list.add(toEdgeAggregate(edge, 0));
            }
            return list;
        }

        QueryResult query(String expression) {
            String normalized = normalizeExpression(expression);
            if (normalized.isEmpty()) {
                throw new IllegalArgumentException("查询表达式不能为空");
            }

            String[] branchExpressions = normalized.split(",");
            List<PathBranch> branches = new ArrayList<PathBranch>();
            Map<String, Integer> nodeHitCount = new LinkedHashMap<String, Integer>();
            Map<String, Integer> edgeHitCount = new LinkedHashMap<String, Integer>();
            double totalWeight = 0.0;

            for (String branchExpression : branchExpressions) {
                if (branchExpression.isEmpty()) {
                    continue;
                }

                String[] nodesInBranch = branchExpression.split("-");
                if (nodesInBranch.length < 2) {
                    throw new IllegalArgumentException("非法分支表达式: " + branchExpression + "，至少需要两个节点");
                }

                List<String> nodePath = new ArrayList<String>();
                List<Edge> edgePath = new ArrayList<Edge>();

                for (int i = 0; i < nodesInBranch.length; i++) {
                    String nodeId = nodesInBranch[i];
                    if (!graph.nodes().containsKey(nodeId)) {
                        throw new IllegalArgumentException("节点不存在: " + nodeId);
                    }
                    nodePath.add(nodeId);
                    nodeHitCount.put(nodeId, nodeHitCount.getOrDefault(nodeId, 0) + 1);
                }

                for (int i = 0; i < nodePath.size() - 1; i++) {
                    String from = nodePath.get(i);
                    String to = nodePath.get(i + 1);
                    Edge edge = graph.findEdge(from, to).orElseThrow(
                            () -> new IllegalArgumentException("边不存在: " + from + " -> " + to));
                    edgePath.add(edge);
                    edgeHitCount.put(edge.id, edgeHitCount.getOrDefault(edge.id, 0) + 1);
                    totalWeight += edge.weight;
                }

                branches.add(new PathBranch(nodePath, edgePath));
            }

            Map<String, NodeAggregate> hitNodeAggregates = new LinkedHashMap<String, NodeAggregate>();
            for (Map.Entry<String, Integer> entry : nodeHitCount.entrySet()) {
                hitNodeAggregates.put(entry.getKey(), toNodeAggregate(entry.getKey(), entry.getValue()));
            }

            Map<String, EdgeAggregate> hitEdgeAggregates = new LinkedHashMap<String, EdgeAggregate>();
            for (Map.Entry<String, Integer> entry : edgeHitCount.entrySet()) {
                Edge edge = findEdgeById(entry.getKey());
                hitEdgeAggregates.put(entry.getKey(), toEdgeAggregate(edge, entry.getValue()));
            }

            return new QueryResult(normalized, branches, hitNodeAggregates, hitEdgeAggregates, totalWeight);
        }

        private NodeAggregate toNodeAggregate(String nodeId, int hitCount) {
            Node node = graph.nodes().get(nodeId);
            int inDegree = graph.inDegree(nodeId);
            int outDegree = graph.outDegree(nodeId);
            return new NodeAggregate(
                    nodeId,
                    node.properties,
                    inDegree,
                    outDegree,
                    inDegree + outDegree,
                    graph.inWeightSum(nodeId),
                    graph.outWeightSum(nodeId),
                    hitCount);
        }

        private EdgeAggregate toEdgeAggregate(Edge edge, int hitCount) {
            return new EdgeAggregate(edge.id, edge.from, edge.to, edge.type, edge.weight, hitCount);
        }

        private Edge findEdgeById(String edgeId) {
            for (Edge edge : graph.edges()) {
                if (edge.id.equals(edgeId)) {
                    return edge;
                }
            }
            throw new IllegalArgumentException("未知边ID: " + edgeId);
        }

        private String normalizeExpression(String expression) {
            if (expression == null) {
                return "";
            }
            return expression
                    .replace("，", ",")
                    .replace("；", ",")
                    .replaceAll("\\s+", "");
        }
    }

    static final class Node {
        final String id;
        final Map<String, String> properties;

        Node(String id, Map<String, String> properties) {
            this.id = id;
            this.properties = Collections.unmodifiableMap(new LinkedHashMap<String, String>(properties));
        }
    }

    static final class Edge {
        final String id;
        final String from;
        final String to;
        final String type;
        final double weight;

        Edge(String id, String from, String to, String type, double weight) {
            this.id = id;
            this.from = from;
            this.to = to;
            this.type = type;
            this.weight = weight;
        }
    }

    static final class NodeAggregate {
        final String nodeId;
        final Map<String, String> properties;
        final int inDegree;
        final int outDegree;
        final int degree;
        final double inWeightSum;
        final double outWeightSum;
        final int hitCount;

        NodeAggregate(String nodeId, Map<String, String> properties, int inDegree, int outDegree, int degree,
                      double inWeightSum, double outWeightSum, int hitCount) {
            this.nodeId = nodeId;
            this.properties = properties;
            this.inDegree = inDegree;
            this.outDegree = outDegree;
            this.degree = degree;
            this.inWeightSum = inWeightSum;
            this.outWeightSum = outWeightSum;
            this.hitCount = hitCount;
        }

        @Override
        public String toString() {
            return "NodeAggregate{" +
                    "nodeId='" + nodeId + '\'' +
                    ", properties=" + properties +
                    ", inDegree=" + inDegree +
                    ", outDegree=" + outDegree +
                    ", degree=" + degree +
                    ", inWeightSum=" + format(inWeightSum) +
                    ", outWeightSum=" + format(outWeightSum) +
                    ", hitCount=" + hitCount +
                    '}';
        }
    }

    static final class EdgeAggregate {
        final String edgeId;
        final String from;
        final String to;
        final String type;
        final double weight;
        final int hitCount;

        EdgeAggregate(String edgeId, String from, String to, String type, double weight, int hitCount) {
            this.edgeId = edgeId;
            this.from = from;
            this.to = to;
            this.type = type;
            this.weight = weight;
            this.hitCount = hitCount;
        }

        @Override
        public String toString() {
            return "EdgeAggregate{" +
                    "edgeId='" + edgeId + '\'' +
                    ", from='" + from + '\'' +
                    ", to='" + to + '\'' +
                    ", type='" + type + '\'' +
                    ", weight=" + format(weight) +
                    ", hitCount=" + hitCount +
                    '}';
        }
    }

    static final class PathBranch {
        final List<String> nodes;
        final List<Edge> edges;
        final double weightSum;

        PathBranch(List<String> nodes, List<Edge> edges) {
            this.nodes = Collections.unmodifiableList(new ArrayList<String>(nodes));
            this.edges = Collections.unmodifiableList(new ArrayList<Edge>(edges));
            double sum = 0.0;
            for (Edge edge : edges) {
                sum += edge.weight;
            }
            this.weightSum = sum;
        }

        @Override
        public String toString() {
            return String.join("-", nodes) + " (edgeCount=" + edges.size() + ", weightSum=" + format(weightSum) + ")";
        }
    }

    static final class QueryResult {
        final String expression;
        final List<PathBranch> branches;
        final Map<String, NodeAggregate> hitNodeAggregates;
        final Map<String, EdgeAggregate> hitEdgeAggregates;
        final double totalWeight;

        QueryResult(String expression, List<PathBranch> branches, Map<String, NodeAggregate> hitNodeAggregates,
                    Map<String, EdgeAggregate> hitEdgeAggregates, double totalWeight) {
            this.expression = expression;
            this.branches = Collections.unmodifiableList(new ArrayList<PathBranch>(branches));
            this.hitNodeAggregates = Collections.unmodifiableMap(new LinkedHashMap<String, NodeAggregate>(hitNodeAggregates));
            this.hitEdgeAggregates = Collections.unmodifiableMap(new LinkedHashMap<String, EdgeAggregate>(hitEdgeAggregates));
            this.totalWeight = totalWeight;
        }
    }
}
