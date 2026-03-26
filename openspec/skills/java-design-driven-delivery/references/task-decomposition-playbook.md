# Task Decomposition Playbook

## 1. Choose the Decomposition Mode

### Use Feature-First When

- 设计文档按业务能力拆块
- 每个能力块有相对独立的写入文件集合
- subagent 可以在不互相踩文件的前提下并行推进

典型切法：

- 创建接口契约与 DTO
- 落应用服务编排
- 落持久化或外部集成
- 落映射和配置收尾

### Use Spring MVC Layer-First When

- 同一能力横跨多个接口，但层级边界很清晰
- controller、service、domain、repository 之间责任稳定
- 更容易按层给出独立写入边界

典型切法：

- `controller`
- `application/service`
- `domain`
- `repository/gateway`
- `dto/mapper`
- `config`

### Use Responsibility Slices for Non-Spring Java Modules

若目标不是 Spring MVC 项目，仍要按职责切分。常见切法：

- `parser`
- `model`
- `validation`
- `generator`
- `facade`
- `adapter`

对于 `YAML -> Cypher` 这类模块，优先按职责包切，而不是硬套 controller/service/repository。

## 2. Parallelism Rules

- 只有在写入集合完全不重叠时，才允许并行 subagent
- 若多个切片都要改同一个 `service`、同一个 `config` 或同一个公共 DTO，改为串行
- 先把阻塞性上下文构建留给主 agent，本地完成后再下发子任务

## 3. Required Task Card Fields

每个 subagent 任务单至少包含：

1. 任务模式：`feature` / `spring-layer` / `responsibility-slice`
2. 目标设计文档
3. 允许修改的文件或目录
4. 禁止触碰的文件或模块
5. 必须完成的实现项
6. 必须输出的交付物
7. 验收口径
8. 测试策略
   - 默认写明“不要求测试”

## 4. Recommended Execution Loop

1. 主 agent 建立清单并拆任务
2. 下发一个或一组无冲突子任务
3. subagent 提交实际代码结果
4. 主 agent 审核并整合
5. 更新本地清单
6. 对当前切片执行最小构建或静态检查
7. 当前切片 `DONE + OK` 后再推进下一轮

## 5. Acceptance Rules Per Slice

每个切片至少确认：

- 满足当前设计文档中的职责边界
- 没有越权修改未授权模块
- 无死代码、无遗漏注入、无明显调用残缺
- 当前切片对应的本地清单行已更新

## 6. Example Slice Mapping

### Spring MVC Example

- `SUB01`: controller + request/response DTO
- `SUB02`: application service and transaction flow
- `SUB03`: domain rule and value object
- `SUB04`: repository/gateway implementation
- `SUB05`: mapper and config cleanup

### Parser/Compiler Module Example

- `SUB01`: parser tree and DTO mapping
- `SUB02`: validation and error model
- `SUB03`: compiled model and facade orchestration
- `SUB04`: generator and parameter naming

保持每个切片“能被明确验收”，不要把整个 feature 一次性丢给一个 subagent。
