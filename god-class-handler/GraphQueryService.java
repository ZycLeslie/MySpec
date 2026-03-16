import java.util.ArrayList;
import java.util.Arrays;
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

    // 只支持 A-B-C 这种单条路径表达式。
    public CypherQuery buildDirectPathQuery(String expression) {
        PathSpec path = parseDirectPath(expression);
        return buildPathQuery(path, "p", false, "path");
    }

    // 支持 A-B-C,B-D 这种多分支表达式，每个分支会生成一段 MATCH，再用 UNION ALL 拼接。
    public CypherQuery buildBranchPathQuery(String expression) {
        List<PathSpec> branches = parseBranches(expression);
        if (branches.size() == 1) {
            return buildPathQuery(branches.get(0), "p", false, "path");
        }

        StringBuilder statement = new StringBuilder();
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        for (int i = 0; i < branches.size(); i++) {
            if (i > 0) {
                statement.append("\nUNION ALL\n");
            }

            String pathAlias = "p" + i;
            String paramPrefix = "branch" + i;
            CypherQuery branchQuery = buildPathQuery(branches.get(i), pathAlias, true, paramPrefix);
            statement.append(branchQuery.statement());
            params.putAll(branchQuery.params());
        }
        return new CypherQuery(statement.toString(), params);
    }

    // 把一条标准化后的路径定义转换成一段完整的 Cypher 查询。
    private CypherQuery buildPathQuery(PathSpec path, String pathAlias, boolean includeBranchName, String paramPrefix) {
        StringBuilder statement = new StringBuilder();
        Map<String, Object> params = new LinkedHashMap<String, Object>();

        statement.append("MATCH ").append(pathAlias).append(" = ");
        appendPathPattern(statement, params, path.nodes(), paramPrefix);
        statement.append("\nRETURN ");
        if (includeBranchName) {
            statement.append("'").append(path.expression()).append("' AS branch,\n       ");
        }
        statement.append(pathAlias).append(" AS path,\n")
                .append("       [node IN nodes(").append(pathAlias).append(") | node.id] AS nodeIds,\n")
                .append("       [rel IN relationships(").append(pathAlias).append(") | properties(rel)] AS edgeSummaries");
        return new CypherQuery(statement.toString(), params);
    }

    // A-B-C 会被拼成 (n0)-[:REL]->(n1)-[:REL]->(n2)，节点 id 通过参数绑定传入。
    private void appendPathPattern(StringBuilder statement, Map<String, Object> params,
                                   List<String> nodes, String paramPrefix) {
        for (int i = 0; i < nodes.size(); i++) {
            String paramName = paramPrefix + "Node" + i;
            params.put(paramName, nodes.get(i));
            statement.append("(n").append(i).append(":").append(nodeLabel)
                    .append(" {id: $").append(paramName).append("})");
            if (i < nodes.size() - 1) {
                statement.append("-[:").append(relationType).append("]->");
            }
        }
    }

    // 解析分支路径，兼容中文逗号和空格。
    private List<PathSpec> parseBranches(String expression) {
        String normalized = normalize(expression);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("query expression must not be empty");
        }

        List<PathSpec> branches = new ArrayList<PathSpec>();
        for (String segment : normalized.split(",")) {
            if (!segment.isEmpty()) {
                branches.add(parsePath(segment));
            }
        }
        if (branches.isEmpty()) {
            throw new IllegalArgumentException("query expression must not be empty");
        }
        return branches;
    }

    // 单路径查询不允许带逗号，否则说明调用方传进来的是分支查询格式。
    private PathSpec parseDirectPath(String expression) {
        String normalized = normalize(expression);
        if (normalized.contains(",")) {
            throw new IllegalArgumentException("direct path query does not support branches: " + expression);
        }
        return parsePath(normalized);
    }

    // 把 A-B-C 解析成节点列表 ["A", "B", "C"]。
    private PathSpec parsePath(String expression) {
        List<String> rawNodes = Arrays.asList(normalize(expression).split("-"));
        if (rawNodes.size() < 2) {
            throw new IllegalArgumentException("path requires at least two nodes: " + expression);
        }

        List<String> nodes = new ArrayList<String>();
        for (String rawNode : rawNodes) {
            nodes.add(requireText(rawNode, "nodeId"));
        }
        return new PathSpec(String.join("-", nodes), nodes);
    }

    // 统一做输入清洗，减少上层对中文标点和空白字符的处理负担。
    private String normalize(String expression) {
        if (expression == null) {
            return "";
        }
        return expression.replace("，", ",").replace("；", ",").replaceAll("\\s+", "");
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be empty");
        }
        return value.trim();
    }

    // label/type 会直接进入 Cypher 结构，必须限制成合法标识符，不能信任外部输入。
    private String validateIdentifier(String value, String fieldName) {
        String text = requireText(value, fieldName);
        if (!text.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException(fieldName + " is invalid: " + value);
        }
        return text;
    }

    private static final class PathSpec {
        private final String expression;
        private final List<String> nodes;

        // expression 是标准化后的原始路径，nodes 是拆分后的节点序列。
        private PathSpec(String expression, List<String> nodes) {
            this.expression = expression;
            this.nodes = Collections.unmodifiableList(new ArrayList<String>(nodes));
        }

        private String expression() {
            return expression;
        }

        private List<String> nodes() {
            return nodes;
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
}
