import java.util.Arrays;

public class GraphPathQueryDemo {

    public static void main(String[] args) {
        // Demo 只负责展示 GraphQueryService 的调用方式。
        GraphQueryService service = new GraphQueryService("Node", "REL");

        print("全图汇总查询", service.buildGraphSummaryQuery());

        GraphQueryService.PathQueryRequest request = GraphQueryService.PathQueryRequest.of(Arrays.asList(
                GraphQueryService.Branch.of(
                        "branch-1",
                        Arrays.asList(
                                GraphQueryService.NodeRef.of("a", GraphQueryService.Condition.eq("name", "alpha")),
                                GraphQueryService.NodeRef.of("b", GraphQueryService.Condition.contains("name", "beta")),
                                GraphQueryService.NodeRef.of("c", GraphQueryService.Condition.eq("status", "online"))
                        ),
                        Arrays.asList(
                                GraphQueryService.EdgeRef.of("ab", GraphQueryService.Condition.eq("type", "depends")),
                                GraphQueryService.EdgeRef.of("bc", GraphQueryService.Condition.contains("remark", "core"))
                        )
                ),
                GraphQueryService.Branch.of(
                        "branch-2",
                        Arrays.asList(
                                GraphQueryService.NodeRef.of("b", GraphQueryService.Condition.contains("name", "beta")),
                                GraphQueryService.NodeRef.of("d", GraphQueryService.Condition.eq("name", "delta"))
                        ),
                        Arrays.asList(
                                GraphQueryService.EdgeRef.of("bd", GraphQueryService.Condition.eq("status", "valid"))
                        )
                )
        ));

        print("结构化路径查询", service.buildPathQuery(request));
    }

    // 打印生成后的 Cypher 和对应参数，便于直接复制到 Neo4j 客户端验证。
    private static void print(String title, GraphQueryService.CypherQuery query) {
        System.out.println("=== " + title + " ===");
        System.out.println(query.statement());
        System.out.println("params = " + query.params());
        System.out.println();
    }
}
