# Company Java 21 Coding Standard

这份文档作为本 skill 的“公司级”默认编码规范门禁。若用户提供了更具体的团队规范文档，用用户文档覆盖这里的默认条目。

## 1. Language Baseline

- 以 Java 21 为默认设计和可读性基线
- 若目标模块在 `pom.xml`、Gradle 或工具链中显式锁定更低 `source`、`target` 或 `release`，必须优先保持目标版本兼容
- 发生“规范基线 21”与“编译目标 <21”冲突时：
  - 保持编译目标兼容
  - 采用 Java 21 的设计习惯而不是强行使用不兼容语法
  - 在本地清单中记录该偏差和处理方式
- 新增代码优先采用 Java 21 清晰可读的语法，而不是为了“新”而新
- 只有在能明显提升可读性和边界表达时，才使用：
  - `record`
  - `sealed`
  - `switch` expression
  - pattern matching

## 2. Design and Package Layout

- 优先按特性组织包；若项目已固定采用 Spring MVC 分层，则遵循现有层级
- 一个类只承载一个主要责任
- 不为临时凑合引入 `Util`、`Helper`、`Manager` 这类模糊命名，除非仓库已有明确模式
- 目录和包名与现有代码风格保持一致

## 3. Spring MVC Layer Boundaries

- `controller` 只做协议适配、参数接收、校验触发、响应映射
- `service` / `application` 做业务编排、事务控制、跨组件协调
- `domain` 承载领域规则和值对象
- `repository` / `gateway` 只处理持久化或外部系统调用
- `dto` / `mapper` 只负责数据转换，不承载业务逻辑
- 不把业务规则塞进 controller，也不把 HTTP 语义泄露到 domain

## 4. Dependency Injection and State

- 优先构造器注入
- 避免字段注入
- 新增依赖尽量声明为 `final`
- 尽量保持对象不可变；能做局部变量就不要升级为成员变量

## 5. API and Method Design

- 方法名表达动作和业务意图
- 保持方法短小，优先用早返回降低嵌套
- 公共 API 保持兼容，除非设计文档或用户明确允许变更
- 入参和返回值优先使用清晰边界对象，不滥用原始 `Map` 或裸字符串

## 6. Null, Optional, and Collections

- 不返回 `null` 集合
- 仅在表达“可能缺失的单值结果”时使用 `Optional`
- 对外暴露签名优先使用接口类型，如 `List`、`Map`
- `Stream` 仅在能提升可读性时使用，避免副作用式流操作
- 优先使用 `Stream.toList()` 而不是不必要的可变集合拼装

## 7. Exceptions and Logging

- 不吞异常
- 不使用空 `catch`
- 抛出异常时保留业务上下文
- 日志使用参数化占位，不做字符串拼接式日志
- 避免记录敏感数据或无意义噪音日志

## 8. Spring Transaction and Validation

- 事务边界放在 `service` / `application` 层
- 输入校验放在控制器边界或 command 边界
- 不在 repository 层编排业务事务
- 不在 controller 里堆复杂业务判定

## 9. Style and Readability

- 新增注释只解释非显而易见的设计意图
- import、注解和成员顺序遵循仓库既有风格
- 删除无用字段、无用 import、无用注入和死代码
- 不为了“对称”而复制粘贴重复实现

## 10. Final Review Questions

收尾前逐项回答：

1. 是否符合 Java 21 设计基线，并与目标模块编译版本兼容？
2. 是否遵守特性边界或 Spring MVC 层级边界？
3. 是否避免了字段注入、过宽可见性和模糊职责？
4. 是否清理了死代码、无用 import 和无用依赖？
5. 是否保留了可维护的异常、事务和 DTO 映射边界？

任一项不能回答“是”，都不能把公司级规范检查记为 `OK`。
