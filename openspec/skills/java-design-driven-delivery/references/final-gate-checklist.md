# Final Gate Checklist

只有以下全部满足，才允许宣布任务完成：

## 1. Local Checklist Gate

- 本地清单存在并可读取
- 所有行 `Status = DONE`
- 所有行 `Check = OK`
- 清单显示 `Progress = 100%`
- `python3 scripts/progress_checklist.py verify --file <checklist>` 返回成功

## 2. Project Standard Gate

按 [project-delivery-standard.md](project-delivery-standard.md) 逐项确认：

- 已阅读并使用相关设计文档
- 规格与实现无明显冲突
- 实现和文档边界清晰
- 任务记录已更新
- 已完成最小必要自检或构建检查

## 3. Company Java 21 Gate

按 [company-java21-standard.md](company-java21-standard.md) 逐项确认：

- 符合 Java 21 可读性和类型设计要求
- 若目标模块编译版本低于 21，已记录并处理兼容性偏差
- 符合特性边界或 Spring MVC 层级边界
- 构造器注入、异常、事务、DTO/mapper 边界处理合理
- 无死代码、无无用 import、无无用依赖

## 4. Completion Rule

只要以下任一条件存在，就必须判定为“未完成”：

- 本地清单未到 `100%`
- 任一门禁项未检查
- 任一门禁项检查结果不是 `OK`
- 仍有待主 agent 收尾但未处理的关键问题

## 5. Final Output

结束时至少汇报：

1. 本地清单路径
2. 本地清单进度
3. 项目级规范检查结果
4. 公司级规范检查结果
5. 若未完成，明确剩余阻塞项
