# Subagent Task Template

把下面模板发给 subagent，并替换占位符。默认这是执行型任务，不是分析型任务。

```md
你负责一个受限的 Java 实现切片，只能在授权边界内修改。
本任务要求提交实际代码结果，禁止只给建议。

## 上下文
- 任务模式: <feature | spring-layer | responsibility-slice>
- 设计文档: <DesignDocPath>
- 本轮目标: <Goal>
- 写入边界: <AllowedPaths>
- 禁止修改: <ForbiddenPaths>

## 必做事项
1. 按 Java 21 可读性规范实现本轮目标。
2. 遵守既有架构边界；若是 Spring MVC：
   - controller 只处理 HTTP 边界
   - service/application 处理业务编排
   - domain 处理规则
   - repository/gateway 处理数据访问
3. 只修改授权范围内文件；如必须越界，先明确列出阻塞点。
4. 删除本轮可确认无用的 import、字段、注入和死代码。
5. 默认不要求测试，除非任务单另行说明。
6. 返回前给出当前切片的完成结论，不得把未完成项包装成已完成。

## 输出要求
1. 修改文件列表。
2. 本轮完成的实现项。
3. 与设计文档的对应关系。
4. 尚未完成或需要主 agent 收尾的事项。
5. 建议主 agent 在本地清单中写入的状态:
   - Status: <DONE | BLOCKED>
   - Check: <OK | FAIL>
   - Notes: <OneLineSummary>
```
