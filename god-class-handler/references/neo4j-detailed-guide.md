# Neo4j Detailed Guide

这份文档面向当前仓库中的图查询代码，目标是帮助你快速理解 Neo4j、Cypher，以及它和 `GraphQueryService.java` / `GraphPathQueryDemo.java` 的对应关系。

适用场景：

- 你第一次接触 Neo4j，希望先建立完整概念。
- 你要看懂当前仓库生成的 Cypher 查询。
- 你准备把这里的查询拼装逻辑接到真实 Neo4j 数据库。
- 你需要本地验证图数据、路径查询和汇总查询是否符合预期。

---

## 1. Neo4j 是什么

Neo4j 是一个图数据库。

和传统关系型数据库相比，它更适合处理下面这些问题：

- 多跳关联
- 依赖关系
- 路径搜索
- 知识图谱
- 组织结构
- 推荐系统
- 调用链或拓扑关系

在关系型数据库里，复杂关联通常靠多表 join；在图数据库里，核心对象就是节点和关系，路径查询通常更自然。

---

## 2. 图数据库核心概念

### 2.1 Node

节点表示一个实体，例如：

- 服务
- 用户
- 订单
- 设备
- 系统模块

示例：

```cypher
(n:Node {id: 1, name: 'alpha', status: 'online'})
```

这里：

- `n` 是查询里的变量别名
- `Node` 是标签
- `{id: 1, name: 'alpha', status: 'online'}` 是属性

### 2.2 Relationship

关系表示两个节点之间的连接。

示例：

```cypher
(a:Node)-[:REL {type: 'depends'}]->(b:Node)
```

这里表示：

- `a` 和 `b` 都是 `Node` 节点
- 两者之间存在一种 `REL` 类型的有向关系
- 关系本身也可以带属性，例如 `type`

### 2.3 Label

标签类似“节点分类”。

例如：

- `:User`
- `:Service`
- `:Order`
- `:Node`

当前仓库默认把节点标签作为构造参数传给 `GraphQueryService`：

```java
new GraphQueryService("Node", "REL")
```

所以示例里默认使用：

- 节点标签：`Node`
- 关系类型：`REL`

### 2.4 Relationship Type

关系类型类似“边的种类”，例如：

- `DEPENDS_ON`
- `CALLS`
- `OWNS`
- `CONNECTED_TO`

当前仓库里示例统一使用 `REL` 作为关系类型，再通过关系属性补充更细的业务语义，例如：

- `type = 'depends'`
- `status = 'valid'`
- `remark = 'core-service'`

### 2.5 Property

属性就是键值对，可挂在节点或关系上。

节点属性示例：

```cypher
{id: 1, name: 'alpha', status: 'online'}
```

关系属性示例：

```cypher
{type: 'depends', remark: 'core-service', status: 'valid'}
```

### 2.6 Path

路径就是从一个节点出发，沿着一个或多个关系走到另一个节点的结果。

示例：

```cypher
(a:Node)-[:REL]->(b:Node)-[:REL]->(c:Node)
```

这就是一条长度为 2 的路径。

当前仓库的 `buildPathQuery(...)` 正是在构造这种路径查询。

---

## 3. Cypher 是什么

Cypher 是 Neo4j 的查询语言，类似：

- SQL 之于 MySQL / PostgreSQL
- Cypher 之于 Neo4j

你可以把它理解成“图数据库版 SQL”。

几个最常见的关键字：

- `CREATE`：创建节点和关系
- `MATCH`：匹配图模式
- `WHERE`：加过滤条件
- `RETURN`：返回结果
- `WITH`：中间结果传递、分组、排序
- `OPTIONAL MATCH`：允许匹配不到
- `CALL {}`：子查询
- `ORDER BY`：排序
- `UNION ALL`：合并多个查询结果

---

## 4. 本地启动 Neo4j

最简单的方式通常是 Docker。

### 4.1 用 Docker 启动

```bash
docker run \
  --name neo4j-demo \
  -p 7474:7474 \
  -p 7687:7687 \
  -e NEO4J_AUTH=neo4j/password123 \
  neo4j:5
```

说明：

- `7474` 是 Neo4j Browser 的 Web 端口
- `7687` 是 Bolt 协议端口，应用程序通常连这个
- `NEO4J_AUTH=neo4j/password123` 设置默认用户名和密码

如果你不想用 Docker，也可以使用 Neo4j Desktop 或本地安装包，核心思路都一样：

- 启动数据库实例
- 打开 Browser
- 执行 Cypher

### 4.2 登录 Browser

启动后打开：

```text
http://localhost:7474
```

常见连接参数：

- URL：`bolt://localhost:7687`
- 用户名：`neo4j`
- 密码：`password123`

---

## 5. 先准备一份和仓库示例对应的数据

为了能直接验证 `GraphPathQueryDemo.java` 输出的查询，我们先造一份最小数据集。

### 5.1 清理旧数据

```cypher
MATCH (n:Node)
DETACH DELETE n;
```

### 5.2 创建节点和关系

```cypher
CREATE (a:Node {id: 1, name: 'alpha', status: 'online'})
CREATE (b:Node {id: 2, name: 'beta-service', status: 'online'})
CREATE (c:Node {id: 3, name: 'gamma', status: 'online'})
CREATE (d:Node {id: 4, name: 'delta', status: 'offline'})

CREATE (a)-[:REL {type: 'depends', remark: 'core-service', status: 'valid'}]->(b)
CREATE (b)-[:REL {type: 'flows', remark: 'core-chain', status: 'valid'}]->(c)
CREATE (b)-[:REL {type: 'sync', remark: 'edge-to-delta', status: 'valid'}]->(d);
```

这组数据满足当前 demo 的两个分支：

- `a -> b -> c`
- `b -> d`

并且满足这些过滤条件：

- `a.name = 'alpha'`
- `b.name CONTAINS 'beta'`
- `c.status = 'online'`
- `ab.type = 'depends'`
- `bc.remark CONTAINS 'core'`
- `d.name = 'delta'`
- `bd.status = 'valid'`

---

## 6. 先掌握最基础的 Cypher 写法

### 6.1 查所有节点

```cypher
MATCH (n:Node)
RETURN n;
```

### 6.2 按属性过滤节点

```cypher
MATCH (n:Node)
WHERE n.status = 'online'
RETURN n;
```

### 6.3 查关系

```cypher
MATCH (a:Node)-[r:REL]->(b:Node)
RETURN a, r, b;
```

### 6.4 查一条两跳路径

```cypher
MATCH p = (a:Node)-[:REL]->(b:Node)-[:REL]->(c:Node)
RETURN p;
```

### 6.5 用关系和节点属性一起过滤

```cypher
MATCH p = (a:Node)-[ab:REL]->(b:Node)-[bc:REL]->(c:Node)
WHERE a.name = 'alpha'
  AND b.name CONTAINS 'beta'
  AND c.status = 'online'
  AND ab.type = 'depends'
  AND bc.remark CONTAINS 'core'
RETURN p;
```

这条语句和当前仓库生成的第一段路径查询本质一致。

---

## 7. 当前仓库里的两个核心查询

### 7.1 全图汇总查询

`GraphQueryService.buildGraphSummaryQuery()` 会生成一条“全图节点汇总 + 全图边汇总”查询。

它的核心价值是：

- 一次请求拿到节点摘要
- 同时拿到边摘要
- 上层不需要再发第二次查询

生成的 Cypher 结构如下：

```cypher
CALL {
  MATCH (n:Node)
  OPTIONAL MATCH (n)-[out:REL]->()
  WITH n, count(out) AS outEdgeCount
  OPTIONAL MATCH ()-[in:REL]->(n)
  WITH n, outEdgeCount, count(in) AS inEdgeCount
  ORDER BY n.id
  RETURN collect({
    nodeId: n.id,
    nodeProperties: properties(n),
    outEdgeCount: outEdgeCount,
    inEdgeCount: inEdgeCount,
    totalEdgeCount: outEdgeCount + inEdgeCount
  }) AS nodes
}
CALL {
  MATCH (from:Node)-[r:REL]->(to:Node)
  WITH from, r, to
  ORDER BY from.id, to.id
  RETURN collect({
    fromNodeId: from.id,
    toNodeId: to.id,
    relationType: type(r),
    edgeProperties: properties(r)
  }) AS edges
}
RETURN nodes, edges
```

#### 7.1.1 这条查询做了什么

第一段子查询：

- 匹配所有 `Node`
- 统计每个节点的出边数
- 统计每个节点的入边数
- 返回节点属性和边数量摘要

第二段子查询：

- 匹配所有 `Node -[REL]-> Node` 关系
- 提取起点、终点、关系类型和关系属性
- 汇总成 `edges`

最终返回：

- `nodes`
- `edges`

#### 7.1.2 为什么用了 `CALL {}`

因为这样可以把节点汇总和边汇总拆开计算，最后再拼成一个返回结果。

好处：

- 逻辑更清晰
- 不容易因为 join 风格的组合造成重复计数
- 更适合把不同维度的统计结果装到一条响应里

#### 7.1.3 为什么用了 `properties(n)` 和 `properties(r)`

`properties(x)` 可以把节点或关系的属性整体提取为 map。

例如：

```cypher
RETURN properties(n)
```

可能返回：

```json
{
  "id": 1,
  "name": "alpha",
  "status": "online"
}
```

这对上层展示“原始属性快照”很方便。

### 7.2 结构化路径查询

`GraphQueryService.buildPathQuery(...)` 会根据结构化输入生成多分支路径查询。

当前 demo 生成的 Cypher 如下：

```cypher
MATCH p0 = (a:Node)-[ab:REL]->(b:Node)-[bc:REL]->(c:Node)
WHERE a.name = $branch0_node0_cond0_value
  AND b.name CONTAINS $branch0_node1_cond0_value
  AND c.status = $branch0_node2_cond0_value
  AND ab.type = $branch0_edge0_cond0_value
  AND bc.remark CONTAINS $branch0_edge1_cond0_value
RETURN 'branch-1' AS branch,
       p0 AS path,
       [node IN nodes(p0) | properties(node)] AS nodeSummaries,
       [rel IN relationships(p0) | properties(rel)] AS edgeSummaries
UNION ALL
MATCH p1 = (b:Node)-[bd:REL]->(d:Node)
WHERE b.name CONTAINS $branch1_node0_cond0_value
  AND d.name = $branch1_node1_cond0_value
  AND bd.status = $branch1_edge0_cond0_value
RETURN 'branch-2' AS branch,
       p1 AS path,
       [node IN nodes(p1) | properties(node)] AS nodeSummaries,
       [rel IN relationships(p1) | properties(rel)] AS edgeSummaries
```

对应参数：

```text
branch0_node0_cond0_value = alpha
branch0_node1_cond0_value = beta
branch0_node2_cond0_value = online
branch0_edge0_cond0_value = depends
branch0_edge1_cond0_value = core
branch1_node0_cond0_value = beta
branch1_node1_cond0_value = delta
branch1_edge0_cond0_value = valid
```

#### 7.2.1 这条查询的意义

它允许你用结构化对象描述路径，而不是手写一大段字符串。

这样做的好处：

- 上层可以用 Java 对象组织查询条件
- 节点条件和边条件都能表达
- 多分支查询可以统一拼成一条语句
- 参数和值分离，避免把值直接拼进 Cypher

#### 7.2.2 `UNION ALL` 在这里的作用

每个分支都会生成一段：

- `MATCH`
- `WHERE`
- `RETURN`

多个分支之间通过 `UNION ALL` 拼接。

适合场景：

- 需要一次查多条不同形状的路径
- 每条路径有自己独立的过滤条件
- 结果里还要保留“这条路径属于哪个分支”

#### 7.2.3 `nodes(path)` 和 `relationships(path)` 是什么

`nodes(p0)` 会返回路径上的节点列表。

`relationships(p0)` 会返回路径上的关系列表。

配合列表推导式：

```cypher
[node IN nodes(p0) | properties(node)]
```

可以把路径上的每个节点都映射成属性 map。

同理：

```cypher
[rel IN relationships(p0) | properties(rel)]
```

可以把路径上的每条关系都映射成属性 map。

这很适合上层直接展示。

---

## 8. Java 结构和 Cypher 的映射关系

下面是当前代码里的主要对象和它们的职责。

| Java 对象 | 作用 | 对应 Cypher 概念 |
| --- | --- | --- |
| `GraphQueryService` | 查询构造器 | 一组 Cypher 模板 |
| `PathQueryRequest` | 整个路径查询请求 | 多个分支组合 |
| `Branch` | 单个路径分支 | 一段 `MATCH ... WHERE ... RETURN` |
| `NodeRef` | 路径中的一个节点 | `(a:Node)` 这样的节点模式 |
| `EdgeRef` | 路径中的一条边 | `[ab:REL]` 这样的关系模式 |
| `Condition` | 节点或边上的过滤条件 | `a.name = $x` / `ab.type = $y` |
| `CypherQuery` | 最终输出 | `statement + params` |

### 8.1 `NodeRef`

示例：

```java
GraphQueryService.NodeRef.of("a", GraphQueryService.Condition.eq("name", "alpha"))
```

含义：

- 别名是 `a`
- 对应 Cypher 节点变量 `a`
- 有一条过滤条件：`a.name = $...`

### 8.2 `EdgeRef`

示例：

```java
GraphQueryService.EdgeRef.of("ab", GraphQueryService.Condition.eq("type", "depends"))
```

含义：

- 别名是 `ab`
- 对应 Cypher 关系变量 `ab`
- 有一条过滤条件：`ab.type = $...`

### 8.3 `Condition`

当前只支持两个操作符：

- `EQ("=")`
- `CONTAINS("CONTAINS")`

也就是：

- 精确匹配
- 字符串包含

如果你后续想扩展更多能力，通常可以继续加：

- `GT`
- `GTE`
- `LT`
- `LTE`
- `IN`
- `STARTS WITH`
- `ENDS WITH`

### 8.4 `validateIdentifier(...)` 为什么重要

当前代码里，下面这些“结构性元素”会直接进入 Cypher：

- label
- relationship type
- alias
- property name

这些内容不能像普通值一样用参数占位。

例如：

- 可以参数化：`a.name = $value`
- 不能参数化：`MATCH (a:$label)`

所以代码必须对这类内容做白名单校验。

当前实现用正则限制为：

```text
[A-Za-z_][A-Za-z0-9_]*
```

这是一个很重要的安全点，可以避免结构性注入风险。

---

## 9. 为什么值要走参数，不直接拼字符串

推荐做法：

```cypher
WHERE a.name = $name
```

不推荐做法：

```cypher
WHERE a.name = 'alpha'
```

在代码里动态拼接时，参数化的优势非常明显：

- 更安全
- 更容易复用执行计划
- 避免引号转义问题
- 更便于调试和日志记录

当前仓库的 `CypherQuery` 就是把查询拆成：

- `statement`
- `params`

这是一种非常标准的封装方式。

---

## 10. 如何把当前 Java 输出拿去 Neo4j 验证

### 10.1 直接运行 demo

如果已经有 `.class` 文件：

```bash
java GraphPathQueryDemo
```

如果你需要重新编译：

```bash
javac GraphQueryService.java GraphPathQueryDemo.java
java GraphPathQueryDemo
```

### 10.2 拿到输出结果

demo 会打印两部分内容：

- 生成的 Cypher
- 对应的参数

然后你可以：

1. 打开 Neo4j Browser。
2. 先执行文档里的初始化数据。
3. 复制 demo 打印出来的查询。
4. 手动把参数代入，或者在应用程序里通过驱动执行。

### 10.3 在应用程序里用参数执行

真正接数据库时，通常不会手工把参数写回 Cypher，而是通过 Java 驱动传入参数 map。

伪代码示例：

```java
CypherQuery query = service.buildPathQuery(request);
session.run(query.statement(), query.params());
```

关键原则：

- 查询结构由代码生成
- 查询值通过参数绑定

---

## 11. 数据建模建议

当前仓库示例用的是统一标签 `Node` 和统一关系类型 `REL`，这种建模方式简单、灵活，适合：

- 原型阶段
- 演示
- 动态图结构
- 上层需要快速验证路径能力

但如果进入正式业务，通常要进一步思考下面几个问题。

### 11.1 是否要区分不同标签

例如：

- `:Service`
- `:Application`
- `:Team`
- `:Database`

优点：

- 语义更清晰
- 查询更可控
- 更容易做约束和索引

代价：

- 查询构造会更复杂
- 通用路径模板要考虑多标签模式

### 11.2 是否要区分不同关系类型

例如：

- `:DEPENDS_ON`
- `:CALLS`
- `:OWNS`
- `:CONNECTS_TO`

如果关系类型本身已经有强业务语义，通常比全部塞进 `REL.type` 更清晰。

但当前仓库的实现假设一个统一 `relationType`，因此如果你要支持多种关系类型，可能需要扩展：

- 每条边允许独立的关系类型
- `EdgeRef` 不再只负责 alias 和条件
- 路径构造逻辑要支持混合边类型

### 11.3 节点是否需要唯一约束

一般建议关键标识加唯一约束，例如：

```cypher
CREATE CONSTRAINT node_id_unique IF NOT EXISTS
FOR (n:Node)
REQUIRE n.id IS UNIQUE;
```

好处：

- 保证 `id` 不重复
- 查找更稳定
- 防止重复导入

### 11.4 是否需要索引

如果你经常按这些字段过滤，就值得建索引：

- `id`
- `name`
- `status`

示例：

```cypher
CREATE INDEX node_name_idx IF NOT EXISTS
FOR (n:Node)
ON (n.name);

CREATE INDEX node_status_idx IF NOT EXISTS
FOR (n:Node)
ON (n.status);
```

---

## 12. Cypher 里几个容易忽略但很重要的点

### 12.1 `OPTIONAL MATCH`

含义类似“左连接”。

示例：

```cypher
MATCH (n:Node)
OPTIONAL MATCH (n)-[r:REL]->()
RETURN n, count(r);
```

如果某个节点没有出边，它依然会出现在结果里。

这就是为什么全图汇总查询里使用了 `OPTIONAL MATCH`，否则没有边的节点可能被漏掉。

### 12.2 `WITH`

`WITH` 用于把前一步结果传给下一步，并且可以顺便：

- 重命名
- 聚合
- 排序
- 限定作用域

示例：

```cypher
MATCH (n:Node)-[r:REL]->()
WITH n, count(r) AS outEdgeCount
RETURN n, outEdgeCount;
```

### 12.3 `collect(...)`

`collect` 用于把多行结果聚合成列表。

示例：

```cypher
MATCH (n:Node)
RETURN collect(n);
```

在全图汇总查询里，`collect({...})` 把每个节点摘要聚合成一个 `nodes` 列表。

### 12.4 `type(r)`

`type(r)` 返回关系类型名。

示例：

```cypher
MATCH ()-[r:REL]->()
RETURN type(r);
```

### 12.5 `properties(x)`

`properties(x)` 返回完整属性 map。

适合：

- 快速调试
- 通用接口输出
- 不想一个字段一个字段列举时

但如果进入正式接口设计，通常还是要考虑：

- 是否只暴露必要字段
- 是否需要字段脱敏
- 是否需要稳定返回结构

---

## 13. 结果应该长什么样

### 13.1 全图汇总查询预期

返回结构是两列：

- `nodes`
- `edges`

其中：

- `nodes` 是节点摘要数组
- `edges` 是边摘要数组

节点摘要示意：

```json
{
  "nodeId": 1,
  "nodeProperties": {
    "id": 1,
    "name": "alpha",
    "status": "online"
  },
  "outEdgeCount": 1,
  "inEdgeCount": 0,
  "totalEdgeCount": 1
}
```

边摘要示意：

```json
{
  "fromNodeId": 1,
  "toNodeId": 2,
  "relationType": "REL",
  "edgeProperties": {
    "type": "depends",
    "remark": "core-service",
    "status": "valid"
  }
}
```

### 13.2 路径查询预期

每个分支都会返回一行，主要字段包括：

- `branch`
- `path`
- `nodeSummaries`
- `edgeSummaries`

第一条分支的 `branch` 应为：

```text
branch-1
```

第二条分支的 `branch` 应为：

```text
branch-2
```

这样上层就能知道每一行结果对应哪个分支模板。

---

## 14. 常见排错方法

### 14.1 查询没有结果

先检查：

- 节点标签是否写对，例如 `Node`
- 关系类型是否写对，例如 `REL`
- 属性名是否写对，例如 `name`、`status`
- 条件是否过严
- 数据里是否真的存在对应路径

最常见的问题不是 Cypher 语法，而是图里没有满足条件的数据。

### 14.2 `CONTAINS` 没匹配到

例如：

```cypher
b.name CONTAINS 'beta'
```

这要求 `b.name` 必须是字符串，并且包含 `beta`。

如果你的数据是：

- `name = 'BETA'`
- `name = 'Beta-Service'`

那就要考虑大小写问题。

你可以改成：

```cypher
WHERE toLower(b.name) CONTAINS toLower($keyword)
```

如果要支持这种能力，Java 侧也需要扩展操作符表达。

### 14.3 结果重复

图查询里出现重复结果，通常是因为：

- 路径模式本身可匹配多条边
- 某个节点之间存在多条同类型关系
- 聚合前做了额外扩展匹配

这时要考虑：

- 是否需要 `DISTINCT`
- 是否要收紧路径模式
- 是否要先分段聚合再继续匹配

### 14.4 统计不准确

尤其是“计数 + 再次匹配”这类查询，容易在同一条语句里被放大。

当前全图汇总查询之所以拆成两个 `CALL {}`，就是为了降低这类问题。

### 14.5 查询慢

先做这几步：

1. 用 `EXPLAIN` 看执行计划。
2. 用 `PROFILE` 看实际行数和算子开销。
3. 给常用过滤字段加索引。
4. 避免无边界的大范围路径展开。
5. 尽量减少不必要的笛卡尔积。

示例：

```cypher
EXPLAIN
MATCH (n:Node)
WHERE n.name = 'alpha'
RETURN n;
```

---

## 15. 如果你要把它接进真实业务，建议注意这些点

### 15.1 查询构造和执行分层

当前仓库已经做了一层很好的分离：

- `GraphQueryService` 负责构造 Cypher
- `CypherQuery` 负责承载 `statement + params`
- 真正的数据库驱动调用可以放在别的类里

建议继续保持这种分层，不要把：

- 业务对象解析
- Cypher 拼接
- 数据库访问
- 返回结果映射

全部揉进一个类里。

### 15.2 明确哪些内容可以参数化，哪些不可以

可以参数化：

- 节点属性值
- 关系属性值

不建议直接参数化，且需要白名单控制：

- label
- relationship type
- alias
- property key

### 15.3 操作符扩展要注意类型语义

如果未来加：

- 数值比较
- 时间比较
- 列表包含
- 范围筛选

就要明确每种属性的数据类型，否则很容易把字符串比较误当成数值比较。

### 15.4 多跳可变长度路径要谨慎

例如：

```cypher
MATCH p = (a:Node)-[:REL*1..5]->(b:Node)
RETURN p;
```

这种查询很强大，但也更容易：

- 爆结果量
- 出现循环路径
- 导致性能问题

如果你后面要扩展到可变长度路径，建议先明确：

- 是否允许重复节点
- 是否允许重复关系
- 最大深度是多少
- 结果排序规则是什么

---

## 16. 一个完整的验证流程

如果你想从零到一把这个仓库里的图查询逻辑跑通，可以按下面流程来。

### 步骤 1

启动 Neo4j。

### 步骤 2

打开 Browser，执行本文第 5 节的数据初始化脚本。

### 步骤 3

在仓库目录执行：

```bash
java GraphPathQueryDemo
```

### 步骤 4

观察打印出来的两段查询：

- 全图汇总查询
- 结构化路径查询

### 步骤 5

把查询复制到 Neo4j Browser 中逐段执行，确认：

- 汇总查询能返回 `nodes` 和 `edges`
- 路径查询能返回 `branch-1` 和 `branch-2`

### 步骤 6

如果结果不符合预期，按第 14 节的排错方法逐项定位：

- 数据是否存在
- 标签和关系类型是否一致
- 条件是否正确
- 索引是否需要补充

---

## 17. 一句话总结当前仓库的设计思路

这套代码的核心不是“执行 Neo4j 查询”，而是“用类型化 Java 对象安全地构造 Cypher 查询”。

你可以把它理解为：

- 上层输入结构化路径需求
- `GraphQueryService` 负责转成 Cypher
- 查询值通过参数绑定
- 最终由应用层把 `statement + params` 发给 Neo4j

这种设计非常适合作为后续图查询能力的基础骨架。

---

## 18. 后续可以继续扩展的方向

如果你准备继续演进这套代码，可以优先考虑：

1. 增加更多操作符，例如 `IN`、`GT`、`STARTS WITH`。
2. 支持边类型按分支或按边单独指定。
3. 支持可变长度路径。
4. 增加排序、分页和限制返回条数。
5. 增加大小写不敏感匹配能力。
6. 增加结果映射对象，而不是直接返回原始属性 map。
7. 增加对约束、索引和执行计划的运维说明。

---

## 19. 附：最小可复制命令清单

### 启动数据库

```bash
docker run \
  --name neo4j-demo \
  -p 7474:7474 \
  -p 7687:7687 \
  -e NEO4J_AUTH=neo4j/password123 \
  neo4j:5
```

### 打开页面

```text
http://localhost:7474
```

### 运行 demo

```bash
java GraphPathQueryDemo
```

### 重新编译后运行

```bash
javac GraphQueryService.java GraphPathQueryDemo.java
java GraphPathQueryDemo
```

### 清理并重建示例数据

```cypher
MATCH (n:Node)
DETACH DELETE n;

CREATE (a:Node {id: 1, name: 'alpha', status: 'online'})
CREATE (b:Node {id: 2, name: 'beta-service', status: 'online'})
CREATE (c:Node {id: 3, name: 'gamma', status: 'online'})
CREATE (d:Node {id: 4, name: 'delta', status: 'offline'})

CREATE (a)-[:REL {type: 'depends', remark: 'core-service', status: 'valid'}]->(b)
CREATE (b)-[:REL {type: 'flows', remark: 'core-chain', status: 'valid'}]->(c)
CREATE (b)-[:REL {type: 'sync', remark: 'edge-to-delta', status: 'valid'}]->(d);
```

---

这份指南写完后，你应该能完成三件事：

- 看懂当前仓库生成的 Cypher
- 在本地 Neo4j 中复现示例结果
- 基于现有 Java 结构继续扩展图查询能力
