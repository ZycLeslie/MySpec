import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GraphQueryService {
    private final String nodeLabel;
    private final String relationType;

    // 约定图里的节点标签和边类型，后续所有查询都基于这两个元信息生成。
    public GraphQueryService(String nodeLabel, String relationType) {
        this.nodeLabel = validateIdentifier(nodeLabel, "nodeLabel");
        this.relationType = validateIdentifier(relationType, "relationType");
    }

    // 查询单个节点的入边数量、出边数量和总边数量。
    public CypherQuery buildNodeSummaryQuery(String nodeId) {
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        params.put("nodeId", requireText(nodeId, "nodeId"));

        String statement = "MATCH (n:" + nodeLabel + " {id: $nodeId})\n"
                + "OPTIONAL MATCH (n)-[out:" + relationType + "]->()\n"
                + "WITH n, count(out) AS outEdgeCount\n"
                + "OPTIONAL MATCH ()-[in:" + relationType + "]->(n)\n"
                + "RETURN n.id AS nodeId,\n"
                + "       outEdgeCount,\n"
                + "       count(in) AS inEdgeCount,\n"
                + "       outEdgeCount + count(in) AS totalEdgeCount";
        return new CypherQuery(statement, params);
    }

    // 查询两个节点之间直接边的属性汇总。
    public CypherQuery buildEdgeSummaryQuery(String fromNodeId, String toNodeId) {
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        params.put("fromNodeId", requireText(fromNodeId, "fromNodeId"));
        params.put("toNodeId", requireText(toNodeId, "toNodeId"));

        String statement = "MATCH (from:" + nodeLabel + " {id: $fromNodeId})"
                + "-[r:" + relationType + "]->"
                + "(to:" + nodeLabel + " {id: $toNodeId})\n"
                + "RETURN from.id AS fromNodeId,\n"
                + "       to.id AS toNodeId,\n"
                + "       type(r) AS relationType,\n"
                + "       properties(r) AS edgeProperties";
        return new CypherQuery(statement, params);
    }

    // 路径查询改成结构化入参，节点和边都可以携带条件。
    public CypherQuery buildPathQuery(PathQueryRequest request) {
        requireNonNull(request, "request");
        if (request.branches().isEmpty()) {
            throw new IllegalArgumentException("request.branches must not be empty");
        }

        StringBuilder statement = new StringBuilder();
        Map<String, Object> params = new LinkedHashMap<String, Object>();

        for (int i = 0; i < request.branches().size(); i++) {
            if (i > 0) {
                statement.append("\nUNION ALL\n");
            }

            Branch branch = request.branches().get(i);
            appendBranchQuery(statement, params, branch, i);
        }

        return new CypherQuery(statement.toString(), params);
    }

    // 每个分支对应一段 MATCH/WHERE/RETURN。
    private void appendBranchQuery(StringBuilder statement, Map<String, Object> params, Branch branch, int branchIndex) {
        validateBranch(branch);
        String pathAlias = "p" + branchIndex;

        statement.append("MATCH ").append(pathAlias).append(" = ");
        appendBranchPattern(statement, branch);

        List<String> predicates = new ArrayList<String>();
        appendNodePredicates(predicates, params, branch.nodes(), branchIndex);
        appendEdgePredicates(predicates, params, branch.edges(), branchIndex);
        if (!predicates.isEmpty()) {
            statement.append("\nWHERE ").append(String.join("\n  AND ", predicates));
        }

        statement.append("\nRETURN '").append(branch.name()).append("' AS branch,\n")
                .append("       ").append(pathAlias).append(" AS path,\n")
                .append("       [node IN nodes(").append(pathAlias).append(") | properties(node)] AS nodeSummaries,\n")
                .append("       [rel IN relationships(").append(pathAlias).append(") | properties(rel)] AS edgeSummaries");
    }

    // 例如 (a:Node)-[ab:REL]->(b:Node)-[bc:REL]->(c:Node)。
    private void appendBranchPattern(StringBuilder statement, Branch branch) {
        for (int i = 0; i < branch.nodes().size(); i++) {
            NodeRef node = branch.nodes().get(i);
            statement.append("(").append(node.alias()).append(":").append(nodeLabel).append(")");
            if (i < branch.edges().size()) {
                EdgeRef edge = branch.edges().get(i);
                statement.append("-[").append(edge.alias()).append(":").append(relationType).append("]->");
            }
        }
    }

    private void appendNodePredicates(List<String> predicates, Map<String, Object> params,
                                      List<NodeRef> nodes, int branchIndex) {
        for (int nodeIndex = 0; nodeIndex < nodes.size(); nodeIndex++) {
            NodeRef node = nodes.get(nodeIndex);
            for (int conditionIndex = 0; conditionIndex < node.conditions().size(); conditionIndex++) {
                Condition condition = node.conditions().get(conditionIndex);
                appendCondition(predicates, params, node.alias(), condition,
                        "branch" + branchIndex + "_node" + nodeIndex + "_cond" + conditionIndex);
            }
        }
    }

    private void appendEdgePredicates(List<String> predicates, Map<String, Object> params,
                                      List<EdgeRef> edges, int branchIndex) {
        for (int edgeIndex = 0; edgeIndex < edges.size(); edgeIndex++) {
            EdgeRef edge = edges.get(edgeIndex);
            for (int conditionIndex = 0; conditionIndex < edge.conditions().size(); conditionIndex++) {
                Condition condition = edge.conditions().get(conditionIndex);
                appendCondition(predicates, params, edge.alias(), condition,
                        "branch" + branchIndex + "_edge" + edgeIndex + "_cond" + conditionIndex);
            }
        }
    }

    private void appendCondition(List<String> predicates, Map<String, Object> params,
                                 String targetAlias, Condition condition, String paramPrefix) {
        String property = validateIdentifier(condition.property(), "property");
        String paramName = paramPrefix + "_value";
        params.put(paramName, condition.value());

        predicates.add(targetAlias + "." + property + " " + condition.operator().cypherOperator() + " $" + paramName);
    }

    private void validateBranch(Branch branch) {
        requireNonNull(branch, "branch");
        requireText(branch.name(), "branch.name");
        if (branch.nodes().size() < 2) {
            throw new IllegalArgumentException("branch.nodes must contain at least two nodes");
        }
        if (branch.edges().size() != branch.nodes().size() - 1) {
            throw new IllegalArgumentException("branch.edges size must equal branch.nodes size - 1");
        }

        for (NodeRef node : branch.nodes()) {
            validateAlias(node.alias(), "node.alias");
            validateConditions(node.conditions());
        }
        for (EdgeRef edge : branch.edges()) {
            validateAlias(edge.alias(), "edge.alias");
            validateConditions(edge.conditions());
        }
    }

    private void validateConditions(List<Condition> conditions) {
        requireNonNull(conditions, "conditions");
        for (Condition condition : conditions) {
            requireNonNull(condition, "condition");
            validateIdentifier(condition.property(), "condition.property");
            requireNonNull(condition.operator(), "condition.operator");
            requireNonNull(condition.value(), "condition.value");
        }
    }

    private void validateAlias(String alias, String fieldName) {
        validateIdentifier(alias, fieldName);
    }

    private <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value;
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be empty");
        }
        return value.trim();
    }

    // label/type/alias/property 会直接进入 Cypher 结构，必须限制成合法标识符。
    private String validateIdentifier(String value, String fieldName) {
        String text = requireText(value, fieldName);
        if (!text.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException(fieldName + " is invalid: " + value);
        }
        return text;
    }

    public enum Operator {
        EQ("="),
        CONTAINS("CONTAINS");

        private final String cypherOperator;

        Operator(String cypherOperator) {
            this.cypherOperator = cypherOperator;
        }

        public String cypherOperator() {
            return cypherOperator;
        }
    }

    public static final class Condition {
        private final String property;
        private final Operator operator;
        private final Object value;

        private Condition(String property, Operator operator, Object value) {
            this.property = property;
            this.operator = operator;
            this.value = value;
        }

        public static Condition eq(String property, Object value) {
            return new Condition(property, Operator.EQ, value);
        }

        public static Condition contains(String property, String value) {
            return new Condition(property, Operator.CONTAINS, value);
        }

        public String property() {
            return property;
        }

        public Operator operator() {
            return operator;
        }

        public Object value() {
            return value;
        }
    }

    public static final class NodeRef {
        private final String alias;
        private final List<Condition> conditions;

        private NodeRef(String alias, List<Condition> conditions) {
            this.alias = alias;
            this.conditions = Collections.unmodifiableList(new ArrayList<Condition>(conditions));
        }

        public static NodeRef of(String alias, Condition... conditions) {
            return new NodeRef(alias, asList(conditions));
        }

        public String alias() {
            return alias;
        }

        public List<Condition> conditions() {
            return conditions;
        }
    }

    public static final class EdgeRef {
        private final String alias;
        private final List<Condition> conditions;

        private EdgeRef(String alias, List<Condition> conditions) {
            this.alias = alias;
            this.conditions = Collections.unmodifiableList(new ArrayList<Condition>(conditions));
        }

        public static EdgeRef of(String alias, Condition... conditions) {
            return new EdgeRef(alias, asList(conditions));
        }

        public String alias() {
            return alias;
        }

        public List<Condition> conditions() {
            return conditions;
        }
    }

    public static final class Branch {
        private final String name;
        private final List<NodeRef> nodes;
        private final List<EdgeRef> edges;

        private Branch(String name, List<NodeRef> nodes, List<EdgeRef> edges) {
            this.name = name;
            this.nodes = Collections.unmodifiableList(new ArrayList<NodeRef>(nodes));
            this.edges = Collections.unmodifiableList(new ArrayList<EdgeRef>(edges));
        }

        public static Branch of(String name, List<NodeRef> nodes, List<EdgeRef> edges) {
            return new Branch(name, nodes, edges);
        }

        public String name() {
            return name;
        }

        public List<NodeRef> nodes() {
            return nodes;
        }

        public List<EdgeRef> edges() {
            return edges;
        }
    }

    public static final class PathQueryRequest {
        private final List<Branch> branches;

        private PathQueryRequest(List<Branch> branches) {
            this.branches = Collections.unmodifiableList(new ArrayList<Branch>(branches));
        }

        public static PathQueryRequest of(List<Branch> branches) {
            return new PathQueryRequest(branches);
        }

        public List<Branch> branches() {
            return branches;
        }
    }

    public static final class CypherQuery {
        private final String statement;
        private final Map<String, Object> params;

        // statement 负责执行，params 负责绑定参数，避免直接拼接节点值。
        public CypherQuery(String statement, Map<String, Object> params) {
            this.statement = statement;
            this.params = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(params));
        }

        public String statement() {
            return statement;
        }

        public Map<String, Object> params() {
            return params;
        }
    }

    private static <T> List<T> asList(T[] items) {
        List<T> list = new ArrayList<T>();
        if (items == null) {
            return list;
        }
        Collections.addAll(list, items);
        return list;
    }
}
