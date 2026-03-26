---
name: java-design-driven-delivery
description: 基于 `requirements.md`、`proposal.md`、`design.md`、`tasks.md` 等设计文档执行 Java 开发编排。用于用户要求“按设计文档实现 Java 功能”“按特性拆任务给 subagent”“按 Spring MVC 分层落地”“按项目/公司规范收口”时。适用于 Spring Boot、Spring MVC 和普通 Java 模块；要求先读规格，再按特性或 `controller/service/domain/repository` 层拆分子任务，持续维护本地进度清单，并在 `references/` 中的项目级与公司级规范全部检查 OK 且进度达到 100% 前不得宣称完成。默认采用 Java 21 设计与可读性规范，但若目标模块显式锁定更低语言级别，必须保持目标版本兼容并在本地清单记录该偏差。默认不考虑测试，除非用户明确要求。
---

# Java Design Driven Delivery

## Quick Start

1. 先按顺序读取与实现直接相关的文档链：
   - 若仓库已迁移到 OpenSpec，优先读取 `openspec/changes/<change>/proposal.md` / `spec.md` -> `design.md` -> `tasks.md`
   - 只有在 feature 尚未迁移或用户明确指向旧规格时，才读取旧 SDD：`requirements.md` -> `design.md` -> `tasks.md`
   - 若用户给定的设计文档位于 `openspec/` 之外，按用户给定的真实路径读取，不要强行回写成 OpenSpec 路径
2. 再读取 [references/project-delivery-standard.md](references/project-delivery-standard.md) 和 [references/company-java21-standard.md](references/company-java21-standard.md)，建立项目级与公司级约束。
3. 在动手改代码前，先创建本地进度清单：

```bash
python3 scripts/progress_checklist.py init \
  --file .codex-progress/<feature>.md \
  --title "<feature>" \
  --task DOCS::Read specs and design \
  --task PLAN::Choose decomposition strategy \
  --task SUB01::First implementation slice \
  --task INTEG::Integrate and verify \
  --task PROJ_GATE::Project standard review \
  --task COMP_GATE::Company Java21 review
```

4. 使用 [references/task-decomposition-playbook.md](references/task-decomposition-playbook.md) 选择“按特性”或“按 Spring MVC 层级”拆分任务，并给每个 subagent 分配明确写入边界。
5. 每完成一个子任务，立刻复核结果并更新本地清单；未更新清单前，不要推进下一个子任务。
6. 仅在 [references/final-gate-checklist.md](references/final-gate-checklist.md) 全部通过且本地清单进度为 `100%` 时，才可宣称任务完成。

## Required Inputs

开始执行前至少确认以下内容：

- 目标设计文档路径
- 设计文档所在位置
  - 可能在 `openspec/changes/`
  - 也可能在 legacy `specs/`
  - 也可能是用户直接给定的任意 `*.md` 路径
- 对应实现目录或模块
- 目标模块实际 `source/target/release` 版本
- 是否更适合按特性拆分还是按层拆分
- 允许 subagent 触达的文件边界
- 可用的构建或静态校验命令
- 测试策略
  - 默认：不考虑测试
  - 只有用户明确要求时，才把测试纳入任务

若设计文档缺失、冲突或明显过期，先补规格或让用户确认边界，再继续实现。

## Workflow

### 1. Build Context Locally

先由主 agent 在本地完成这些动作，不要把关键上下文构建外包给 subagent：

- 读取完整设计链和项目约束
- 确认目标能力、边界、非目标
- 对照现有代码识别改动入口、影响范围和风险点
- 识别需要同步更新的实现、配置、文档或任务记录

### 2. Choose a Decomposition Mode

使用 [references/task-decomposition-playbook.md](references/task-decomposition-playbook.md) 中的决策规则：

- 优先“按特性拆分”，当每个能力块拥有相对独立的文件集合和业务闭环时。
- 使用“按 Spring MVC 层级拆分”，当单个能力横跨多个 HTTP 入口，且控制器、应用服务、领域对象、仓储/集成层之间边界清晰时。
- 若不是 Spring MVC 项目，则按包职责或模块职责拆分，例如 `parser`、`validation`、`generator`、`facade`。
- 默认把中等及以上切片委托给 subagent；若切片过小、不值得委托或会阻塞关键路径，可由主 agent 本地处理，但必须在清单备注原因。
- 只有当写入集合互不重叠时，才允许并行 subagent；否则串行执行。

### 3. Create the Local Checklist Before Editing

本 skill 把本地清单当成强制门禁，而不是可选记录：

- 每个子任务都必须先建一行，再开始实施。
- 每行都必须包含：
  - `ID`
  - `Task`
  - `Status`
  - `Check`
  - `Notes`
- `Status` 只允许：`TODO`、`IN_PROGRESS`、`DONE`、`BLOCKED`
- `Check` 只允许：`PENDING`、`OK`、`FAIL`

常用命令：

```bash
python3 scripts/progress_checklist.py add \
  --file .codex-progress/<feature>.md \
  --task SUB02::Repository and mapper slice

python3 scripts/progress_checklist.py update \
  --file .codex-progress/<feature>.md \
  --task-id SUB02 \
  --status DONE \
  --check OK \
  --notes "Integrated repository changes and cleaned imports."
```

### 4. Delegate the Smallest Useful Slice

给 subagent 下发任务时，必须保证：

- 任务边界单一且可交付
- 文件所有权清晰
- 禁止越权修改未授权区域
- 要求提交实际代码结果，不接受只给分析
- 默认不把测试写进任务单
- 若因为规模过小改由主 agent 自己执行，也要先在本地清单写入对应切片，并记录“未委派原因”

复用 [references/subagent-task-template.md](references/subagent-task-template.md) 组装任务单。若任务跨多个层级，只拆到“一个 subagent 一个完整可验证切片”，不要把 controller、service、repository 混成一个无边界大任务。

### 5. Integrate and Check After Every Subtask

每个 subagent 返回后，主 agent 立刻执行以下动作：

1. 阅读修改结果，确认没有越界。
2. 检查是否满足设计文档要求与本轮任务单目标。
3. 清理导入、注解、依赖注入、DTO/mapper 或配置上的收尾项。
4. 运行最小必要构建或静态检查。
   - 不要求测试，除非用户明确要求。
5. 更新本地清单，把本轮 `Status` 与 `Check` 改成真实状态。
6. 只有当该行 `Status=DONE` 且 `Check=OK` 时，才允许进入下一轮。

### 6. Run the Final Gate

结束前必须同时完成三件事：

1. 按 [references/project-delivery-standard.md](references/project-delivery-standard.md) 完成项目级复核。
2. 按 [references/company-java21-standard.md](references/company-java21-standard.md) 完成公司级 Java 21 复核。
3. 按 [references/final-gate-checklist.md](references/final-gate-checklist.md) 逐项确认并更新本地清单，直到进度 `100%`。

只要存在以下任一情况，就不得宣称完成：

- 仍有 `TODO`、`IN_PROGRESS` 或 `BLOCKED`
- 仍有 `Check=PENDING` 或 `Check=FAIL`
- 项目级规范尚未全部检查 OK
- 公司级规范尚未全部检查 OK
- 本地进度不是 `100%`

## Decomposition Rules

### Feature-First

适用于：

- 功能边界天然独立
- 同一个功能有自己完整的 DTO、service、repository 闭环
- 不同 subagent 可以拥有互不重叠的写入集合

优先把任务切成：

- API/command contract
- application flow
- persistence/integration
- mapping/serialization
- configuration or wiring

### Spring MVC Layer-First

适用于：

- 设计文档本身就是典型 Spring Boot / Spring MVC 分层
- 多个接口共用同一 service/domain/repository 结构
- 更适合按层清扫而非按 endpoint 切片

推荐层次：

- `controller`
- `application/service`
- `domain`
- `repository` / `gateway`
- `dto` / `mapper`
- `config`

层级拆分时遵守这些边界：

- `controller` 只处理 HTTP 输入输出、鉴权、校验和状态码映射
- `service` / `application` 负责事务边界与业务编排
- `domain` 承载规则和核心对象
- `repository` / `gateway` 只处理持久化或外部系统访问
- `dto` / `mapper` 只做边界对象转换，不承载业务规则

## Output Requirements

执行该 skill 时，最终输出至少应包含：

1. 使用了哪些设计文档和规范文档
2. 选择了哪种拆分策略，以及原因
3. 本地清单文件路径
4. subagent 任务列表与完成状态
5. 当前集成结果与剩余风险
6. 项目级与公司级规范检查结果
7. 本地进度百分比

若进度未到 `100%` 或规范检查未全绿，明确说明未完成原因，不得给出“已完成”结论。
