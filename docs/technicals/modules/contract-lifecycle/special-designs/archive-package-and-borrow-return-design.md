# 归档封包与借阅归还专项设计

## 1. 文档说明

本文档是 `contract-lifecycle` 模块关于归档封包与借阅归还的治理与实现基线设计，承接
[`detailed-design.md`](../detailed-design.md) 第 11 节中"归档输入集完整性规则、封包格式、目录结构、借阅/归还子域表设计"的下沉要求。

本文同时回答治理层问题和最小实现落点，封包目录、借阅归还状态机、子域表结构草案、封包生成伪代码、幂等、锁与补偿任务以第 6 节为准。

### 1.1 输入

- 本模块详细设计：[`detailed-design.md`](../detailed-design.md)
- 第一份专项设计：[`履约规则与风险评分专项设计`](fulfillment-rules-and-risk-scoring-design.md)
- 第二份专项设计：[`变更影响与回写专项设计`](change-impact-and-write-back-design.md)
- 第三份专项设计：[`终止善后与访问控制专项设计`](termination-settlement-and-access-control-design.md)
- 总平台架构：[`Architecture Design`](../../architecture-design.md)
- 总平台接口规范：[`API Design`](../../api-design.md)
- 总平台共享内部边界：[`Detailed Design`](../../detailed-design.md)
- 合同管理本体详细设计：[`contract-core Detailed Design`](../contract-core/detailed-design.md)
- 项目规范：[`PRINCIPLE.md`](../../../PRINCIPLE.md)

### 1.2 文档定位

本文档定位为**归档封包与借阅归还的治理设计 + 实现基线**，前半部分聚焦"归档封包如何被治理、借阅归还子域如何被约束"，第 6 节聚焦"封包目录如何组织、借阅归还状态如何落库、封包任务如何幂等执行和补偿恢复"。

### 1.3 明确排除

本文档不包含以下内容：

- API 路径、请求响应字段、接口契约
- 可直接执行的数据库 DDL 脚本、索引创建语句
- 生产代码、具体压缩库调用代码、文件系统操作代码
- 实施排期、里程碑、负责人
- 运维手册、部署配置、监控告警

表级对象草案、索引方向、状态机、封包目录样例、伪代码、幂等键、锁粒度和补偿任务属于本文承接范围，不再排除。

### 1.4 阅读边界

本文只回答以下问题：

- 归档封包的治理对象是什么，不治理什么
- 归档封包的稳定锚点在哪里，为什么选择这些锚点
- 归档封包的版本归属如何划分，谁来定义、谁来消费、谁来变更
- 归档封包的最小治理单元是什么，如何避免封包规则膨胀
- 借阅/归还子域的治理边界在哪里，与业务状态、流程、文档的边界如何划分
- 借阅/归还子域的责任如何划分，定义权、执行权、解释权归属谁
- 与前三份专项设计的引用关系
- 与 `contract-core`、`document-center` 的归档封包与借阅归还边界如何划分
- 归档封包、借阅、归还、逾期、撤销在实现层如何落点

## 2. 归档封包治理设计

### 2.1 治理对象

归档封包治理的**直接对象**是：

| 治理对象 | 说明 | 不治理内容 |
| --- | --- | --- |
| 归档输入集完整性规则 | 归档输入集的组成、必选/可选条件、完整性校验基准 | 具体归档记录的输入集计算结果 |
| 归档封包格式定义 | 封包结构、元数据块、清单文件格式、编码约束 | 封包生成算法、压缩实现 |
| 归档目录结构规则 | 目录层级、命名规范、分类维度、排序规则 | 目录生成代码、文件系统操作 |
| 归档产物引用规则 | 封包文件引用、清单文件引用、存储定位引用的治理约束 | 文件存储实现、对象存储操作 |
| 归档批次管理规则 | 批次编号规则、批次状态、批次与合同关系 | 批次生成算法、批次调度逻辑 |

归档封包治理的**间接约束对象**是：

- `cl_archive_record` 的 `archive_scope_json`、`package_document_id`、`manifest_document_id`、`archive_batch_no` 字段含义与约束
- `cl_lifecycle_document_ref` 中 `ARCHIVE_PACKAGE`、`ARCHIVE_MANIFEST` 角色的引用约束
- `cl_lifecycle_summary` 中归档摘要块的结构约束
- `cl_lifecycle_timeline_event` 中归档里程碑事件的生成约束

### 2.2 稳定锚点

归档封包的**稳定锚点**选择如下：

| 稳定锚点 | 说明 | 选择原因 |
| --- | --- | --- |
| `contract_id` | 合同主档主键，归档封包作用的根本业务实体 | 合同主档是业务真相源，归档必须围绕同一 `contract_id` 运行 |
| `archive_record_id` | 归档记录主键，归档封包的作用对象 | 一次归档对应一条 `ArchiveRecord`，封包依附于归档记录 |
| `archive_status` | 归档状态，封包生成与完整性校验的状态基准 | 状态是封包生成、完整性校验的稳定输入维度 |
| `archive_batch_no` | 归档批次编号，封包批次归属的稳定维度 | 批次编号是封包管理、批次查询的稳定分类键 |
| `package_document_id` | 封包文件引用，封包产物的稳定标识 | 封包文件是文档中心对象，引用是稳定锚点 |
| `manifest_document_id` | 清单文件引用，封包清单的稳定标识 | 清单文件是文档中心对象，引用是稳定锚点 |

**稳定锚点原则**：归档封包只能引用上述稳定锚点作为输入，不得引用以下不稳定锚点：

- 流程引擎的节点 ID、任务 ID（流程实例是外部对象）
- 文档中心的具体文件版本内容（文件内容不是治理对象）
- 临时计算字段、缓存字段、非持久化投影字段
- 履约、变更、终止模块的私有过程状态（除非已沉淀为稳定摘要）

### 2.3 版本归属

归档封包的版本归属按以下原则划分：

| 规则类型 | 定义权归属 | 消费方 | 变更权限 | 版本化方式 |
| --- | --- | --- | --- | --- |
| 归档输入集完整性规则 | `contract-lifecycle` 模块 | 归档记录、封包生成、摘要、时间线、AI 完整性检查 | 模块级变更，需同步更新摘要与时间线消费面 | 随模块版本演进，不单独版本化 |
| 归档封包格式定义 | `contract-lifecycle` 模块 | 封包生成、文档中心引用、归档记录、摘要 | 模块级变更，需评估对文档中心引用、摘要的连锁影响 | 随模块版本演进，不单独版本化 |
| 归档目录结构规则 | `contract-lifecycle` 模块 | 封包生成、归档浏览、摘要、时间线 | 模块级变更，需评估对归档浏览、摘要的连锁影响 | 随模块版本演进，不单独版本化 |
| 归档产物引用规则 | `contract-lifecycle` 模块 | 归档记录、文档中心、借阅归还、摘要 | 模块级变更，需评估对借阅归还、摘要的连锁影响 | 随模块版本演进，不单独版本化 |
| 归档批次管理规则 | `contract-lifecycle` 模块 | 归档记录、批次查询、摘要、报表 | 模块级变更，需评估对查询、摘要、报表的连锁影响 | 随模块版本演进，不单独版本化 |

**版本归属原则**：

- 归档封包属于 `contract-lifecycle` 模块的内部治理资产，不属于 `contract-core` 的合同主档规则，也不属于 `document-center` 的文件格式规则。
- 归档封包变更不需要变更合同主档、文档中心、流程引擎的版本，但可能需要刷新 `cl_archive_record`、`cl_lifecycle_summary`、`cl_lifecycle_timeline_event` 的消费面。
- 归档封包不对外暴露为独立 API 资源，只通过归档记录、摘要、时间线间接消费。

### 2.4 最小治理单元

归档封包的**最小治理单元**是：

- **归档输入集完整性条件**：按"输入来源 + 必选/可选 + 校验条件"组合为最小治理单元，例如"合同主档摘要、必选、非空校验"的规则。
- **封包格式要素**：按"格式要素类型 + 编码规范"组合为最小治理单元，例如"元数据块、JSON 格式、UTF-8 编码"的规则。
- **目录结构层级**：按"目录层级 + 分类维度 + 命名规范"组合为最小治理单元，例如"一级目录、按文档角色分类、大写英文命名"的规则。
- **归档产物类型**：按"产物类型 + 引用方式 + 消费场景"组合为最小治理单元，例如"封包文件、文档中心引用、归档浏览"的规则。

**最小治理单元原则**：

- 不允许将多个输入来源合并为一个"通用输入集"，每个输入来源必须有独立的治理条目。
- 不允许将封包格式与目录结构合并为同一个治理单元，封包格式是文件规范，目录结构是组织规范。
- 不允许将归档产物引用与借阅归还合并为同一个治理单元，归档产物引用是封包治理，借阅归还是子域治理。
- 不允许将归档封包与履约节点类型混为同一个治理单元，参见[第一份专项设计](fulfillment-rules-and-risk-scoring-design.md)。
- 不允许将归档封包与变更影响评估混为同一个治理单元，参见[第二份专项设计](change-impact-and-write-back-design.md)。
- 不允许将归档封包与终止善后清单混为同一个治理单元，参见[第三份专项设计](termination-settlement-and-access-control-design.md)。

## 3. 借阅/归还子域治理设计

### 3.1 治理边界

借阅/归还子域的治理边界如下：

| 治理边界内（属于借阅/归还子域治理） | 治理边界外（不属于借阅/归还子域治理） |
| --- | --- |
| 借阅申请规则（借阅条件、审批流程、借阅期限） | 具体借阅记录的审批执行结果 |
| 借阅权限定义（谁可以借阅、借阅范围、使用限制） | 权限检查算法的代码实现 |
| 归还规则（归还期限、逾期判定、续借条件） | 归还操作的调度逻辑 |
| 借阅记录状态机（申请中、已借出、已归还、逾期） | 借阅状态持久化实现 |
| 借阅与归档记录的映射关系 | 借阅记录的数据库存储实现 |
| 借阅记录在摘要、时间线中的呈现约束 | 借阅的 API 接口设计 |

**治理边界原则**：

- 借阅/归还子域治理的是"借阅规则、权限定义、归还规则、状态机、映射关系、呈现约束"，不是"怎么借阅、怎么归还"。
- 借阅/归还子域不替代归档状态，借阅/归还是对归档后档案的访问控制，不是归档状态本身。
- 借阅/归还子域不拥有合同主档，借阅记录只通过 `cl_lifecycle_summary` 和 `cl_lifecycle_timeline_event` 间接消费。
- 借阅/归还子域不拥有文档中心文件对象，借阅的是归档封包引用，不是文件内容本身。

### 3.2 责任划分

借阅/归还子域的责任划分如下：

| 责任类型 | 归属方 | 说明 |
| --- | --- | --- |
| 借阅规则定义权 | `contract-lifecycle` 模块 | 定义借阅条件、借阅期限、审批流程、使用限制 |
| 借阅权限定义权 | `contract-lifecycle` 模块 + `identity-access` 底座 | 定义谁可以借阅、借阅范围、权限继承规则 |
| 归还规则定义权 | `contract-lifecycle` 模块 | 定义归还期限、逾期判定、续借条件、逾期处理 |
| 借阅/归还执行权 | `contract-lifecycle` 模块内部服务 | 按治理定义的规则执行借阅/归还，但不拥有借阅算法实现细节的治理权 |
| 借阅记录消费权 | 摘要域、时间线域、通知、审计 | 消费稳定后的借阅状态，不重新计算借阅期限 |
| 借阅记录解释权 | `contract-lifecycle` 模块 | 对借阅规则的业务含义、变更影响、消费影响拥有解释权 |
| 借阅权限执行权 | `contract-core` 模块 + 平台权限底座 | 按治理定义的权限规则执行借阅权限检查 |
| 借阅审计权 | 平台审计中心 | 对借阅申请、审批、借出、归还、逾期进行审计留痕 |

**责任划分原则**：

- `contract-lifecycle` 模块拥有借阅/归还规则的定义权和解释权，但借阅权限的执行权部分归属 `contract-core` 模块和平台权限底座，因为借阅权限最终体现在合同访问能力的约束上。
- 借阅/归还子域不拥有流程引擎的审批结论，流程审批结果是借阅的前置条件，不是借阅/归还的直接治理对象。
- 借阅/归还子域不拥有文档中心的文件对象，借阅的是归档封包引用，文件访问的执行权归属文档中心和权限底座。
- 借阅记录状态机变更可能触发通知、审计、摘要更新，但这些属于消费面，不属于借阅/归还子域治理本身。

### 3.3 与前三份专项设计的关系

借阅/归还子域与前三份专项设计的治理关系：

- 履约规则与风险评分是借阅/归还的**间接输入治理约束之一**：履约状态、风险等级可以作为借阅权限的补充输入维度，例如高风险合同可能限制借阅范围。
- 变更影响评估是借阅/归还的**间接输入治理约束之一**：变更结果可能影响借阅权限，例如重大变更后的合同可能有特殊的借阅限制。
- 终止善后与访问控制是借阅/归还的**直接治理关联对象**：终止后的权限限制直接影响借阅权限，借阅规则必须继承终止后权限限制的规则。
- 借阅/归还子域不修改履约规则与风险评分：借阅/归还只能读取履约和风险评分的稳定输出，不能反向修改履约节点类型、逾期判定规则、风险评分维度。
- 借阅/归还子域不修改变更影响评估：借阅/归还只能读取变更影响评估的稳定输出，不能反向修改变更影响范围、影响等级。
- 借阅/归还子域不修改终止善后与访问控制：借阅规则继承终止后权限限制，但不能反向修改善后事项类型、结算规则、权限限制项目。

## 4. 与周边模块的边界说明

### 4.1 与 `contract-core` 的边界

| 边界维度 | `contract-core` 负责 | `contract-lifecycle` 归档封包与借阅归还负责 |
| --- | --- | --- |
| 合同主档 | 持有 `contract_id`、合同身份、生命周期主状态、主状态流转规则 | 不持有合同主档，只通过 `contract_id` 引用合同 |
| 归档状态回写 | 提供受控回写接口，接收归档摘要、归档状态、归档时间 | 通过 `LifecycleIntegrationFacade` 调用回写接口，回写归档结果、归档状态 |
| 借阅权限执行 | 持有合同访问权限、数据权限规则，执行借阅权限检查 | 定义借阅权限规则、借阅条件，但不执行权限检查本身 |
| 生命周期映射 | 持有生命周期主状态与周边状态的映射规则 | 归档状态不与生命周期主状态映射规则混淆，归档是周边状态 |
| 归档封包格式 | 不持有归档封包格式、目录结构、输入集完整性规则 | 归档封包格式、目录结构、输入集完整性规则是 `contract-lifecycle` 的内部治理资产 |

**边界原则**：归档封包与借阅归还不拥有合同主档，不修改合同主档的身份、分类、主状态。`contract-core` 不拥有归档封包格式、目录结构、借阅规则，这些属于 `contract-lifecycle` 的治理资产。

### 4.2 与 `document-center` 的边界

| 边界维度 | `document-center` 负责 | `contract-lifecycle` 归档封包与借阅归还负责 |
| --- | --- | --- |
| 文件对象 | 持有文件对象主记录、版本链、存储定位、访问权限 | 不持有文件对象，只通过 `cl_lifecycle_document_ref` 引用文件 |
| 归档封包文件 | 文件对象可以表达归档封包、清单文件 | 归档封包格式不包含文件内容规则，文件只作为封包的产物引用 |
| 文件访问限制 | 文档中心持有文件访问权限，执行文件访问限制 | 借阅规则可以包含文件访问限制规则，但执行权归属文档中心 |
| 归档浏览 | 文档中心提供归档封包文件浏览能力 | 归档目录结构规则不包含文件浏览实现，只定义目录组织规范 |
| 借阅文件交付 | 文档中心负责借阅后的文件解密下载、明文导出 | 借阅规则定义借阅条件，但文件交付执行权归属文档中心 |

**边界原则**：归档封包与借阅归还不拥有文件对象，不定义文件格式、文件内容、文件版本规则。文件引用只作为归档封包的业务语义附件，不作为归档封包格式或借阅规则的直接治理对象。借阅的是归档封包引用，文件访问的执行权归属文档中心和权限底座。

### 4.3 与 `workflow-engine` 的边界

| 边界维度 | `workflow-engine` 负责 | `contract-lifecycle` 归档封包与借阅归还负责 |
| --- | --- | --- |
| 流程定义 | 持有流程定义、节点定义、审批结论 | 不持有流程定义，只通过 `cl_lifecycle_process_ref` 引用流程实例 |
| 归档审批 | 归档审批的流程实例 | 归档封包不包含"审批节点类型"，审批流程是外部对象，不是封包格式的直接输入 |
| 借阅审批 | 借阅审批的流程实例 | 借阅规则可以引用审批结论，但不把流程节点 ID 作为稳定锚点 |
| 封包生成 | 流程审批结论可以触发封包生成 | 封包生成不引用流程节点状态、流程任务状态，只引用审批完成后的稳定结果 |
| 借阅归还 | 流程审批结论是借阅的前置条件 | 借阅/归还不引用流程节点状态、流程任务状态，只引用审批完成后的稳定结果 |

**边界原则**：归档封包与借阅归还不拥有流程引擎，不引用流程节点 ID、任务 ID、流程定义 ID 作为治理输入。流程审批结果是归档、借阅的前置条件，但不是归档封包格式或借阅规则的稳定锚点。

## 5. 治理真理与稳定性原则

### 5.1 治理真理

归档封包与借阅归还的治理真理：

1. **归档封包是 `contract-lifecycle` 的内部治理资产**，不属于合同主档、不属于流程引擎、不属于文档中心。
2. **借阅/归还子域是对归档后档案的访问控制**，不替代归档状态，不拥有合同主档。
3. **归档封包的稳定锚点只引用 `contract_id`、归档记录 ID、归档状态、批次编号、封包引用**，不引用流程实例或文件内容。
4. **借阅/归还的稳定锚点只引用 `contract_id`、归档记录 ID、借阅记录状态**，不引用流程节点或文件版本。
5. **最小治理单元必须独立、不可合并**，输入集条件、封包格式、目录结构、借阅规则必须分治。
6. **归档封包与借阅归还变更必须评估消费面影响**，摘要、时间线、通知、AI、合同主档回写的连锁影响必须同步评估。
7. **归档封包与借阅归还不拥有合同主档、不拥有流程引擎、不拥有文件对象**，只通过稳定引用和受控接口与周边模块协作。

### 5.2 稳定性原则

归档封包与借阅归还的稳定性原则：

- 归档输入集来源编码一旦发布，不得随意删除或重用，只能新增或标记为废弃。
- 封包格式要素编码一旦发布，不得随意删除或重用，只能新增或标记为废弃。
- 借阅权限编码一旦发布，不得随意删除或重用，只能新增或标记为废弃。
- 输入集完整性规则的校验条件变更，必须评估对已存在归档记录的影响。
- 封包格式变更，必须评估对已存在归档封包、文档中心引用、归档浏览的连锁影响。
- 目录结构变更，必须评估对已存在归档封包、归档浏览、借阅归还的连锁影响。
- 借阅规则变更，必须评估对已存在借阅记录、权限检查、通知、审计的连锁影响。
- 归档封包与借阅归还的变更，必须同步更新 `cl_archive_record`、`cl_lifecycle_summary`、`cl_lifecycle_timeline_event` 的消费面约束，必要时触发合同主档的补偿回写。

## 6. 实现落点附录

本节承接父文档第 11 节下沉的实现基线。治理规则以第 2 至第 5 节为准，开发落库、任务编排、测试用例和恢复演练以本节作为最小输入。

### 6.1 借阅、归还、逾期、撤销状态机

借阅记录主状态使用 `borrow_status` 表达，首批状态如下：

| 状态 | 含义 | 可进入来源 | 可转出目标 |
| --- | --- | --- | --- |
| `DRAFT` | 借阅申请草稿，尚未提交审批或规则校验 | 新建 | `SUBMITTED`、`CANCELLED` |
| `SUBMITTED` | 已提交借阅申请，等待权限校验或审批 | `DRAFT` | `APPROVED`、`REJECTED`、`CANCELLED` |
| `APPROVED` | 借阅申请已通过，尚未实际借出 | `SUBMITTED` | `BORROWED`、`CANCELLED` |
| `BORROWED` | 归档封包已借出或访问能力已发放 | `APPROVED` | `RETURNING`、`OVERDUE`、`REVOKED` |
| `RETURNING` | 已发起归还，等待归还确认或文件访问能力回收 | `BORROWED`、`OVERDUE` | `RETURNED`、`RETURN_FAILED` |
| `RETURNED` | 已完成归还，借阅闭环结束 | `RETURNING` | 无 |
| `OVERDUE` | 超过 `due_at` 仍未归还 | `BORROWED` | `RETURNING`、`REVOKED` |
| `REVOKED` | 借阅资格被管理员或规则撤销 | `BORROWED`、`OVERDUE` | `RETURNING`、`RETURNED` |
| `REJECTED` | 借阅申请被拒绝 | `SUBMITTED` | 无 |
| `CANCELLED` | 申请人在借出前撤销申请 | `DRAFT`、`SUBMITTED`、`APPROVED` | 无 |
| `RETURN_FAILED` | 归还确认失败，需要补偿或人工处理 | `RETURNING` | `RETURNING`、`RETURNED` |

状态推进规则：

| 动作 | 前置状态 | 目标状态 | 关键校验 | 结果动作 |
| --- | --- | --- | --- | --- |
| 提交借阅 | `DRAFT` | `SUBMITTED` | 合同与归档记录存在、归档状态允许借阅、申请人具备基础查看权限 | 写入申请时间线事件 |
| 审批通过 | `SUBMITTED` | `APPROVED` | 流程审批结论为通过、借阅期限合法 | 冻结审批快照 |
| 借出 | `APPROVED` | `BORROWED` | 封包引用有效、无互斥借阅限制、文档中心访问能力可发放 | 生成借阅交付记录 |
| 发起归还 | `BORROWED`、`OVERDUE`、`REVOKED` | `RETURNING` | 借阅记录未终态、归还人匹配或具备代还权限 | 写入归还流水 |
| 归还确认 | `RETURNING` | `RETURNED` | 文档中心访问能力已回收或交付介质已确认 | 刷新摘要与时间线 |
| 逾期扫描 | `BORROWED` | `OVERDUE` | 当前时间大于 `due_at` 且无归还确认 | 生成逾期通知与审计事件 |
| 借阅撤销 | `BORROWED`、`OVERDUE` | `REVOKED` | 管理员撤销、权限规则失效或合同状态限制 | 触发访问能力回收 |
| 借出前取消 | `DRAFT`、`SUBMITTED`、`APPROVED` | `CANCELLED` | 尚未进入 `BORROWED` | 关闭流程引用 |

### 6.2 借阅与归还子域表结构草案

#### 6.2.1 `cl_archive_borrow_record`

用途：记录一次归档封包借阅申请、审批、借出、逾期、撤销和归还闭环，是借阅子域主表。

| 字段 | 含义 |
| --- | --- |
| `borrow_record_id` | 借阅记录主键 |
| `contract_id` | 合同主档引用 |
| `archive_record_id` | 归档记录引用 |
| `archive_batch_no` | 归档批次编号快照 |
| `package_document_id` | 封包文件引用快照 |
| `manifest_document_id` | 清单文件引用快照 |
| `borrow_status` | 借阅主状态 |
| `borrow_purpose` | 借阅用途 |
| `borrow_scope` | 借阅范围：`PACKAGE_ONLY`、`MANIFEST_ONLY`、`PACKAGE_AND_MANIFEST` |
| `requested_by` | 申请人 |
| `requested_org_unit_id` | 申请人组织 |
| `requested_at` | 申请提交时间 |
| `approved_by` | 审批人 |
| `approved_at` | 审批通过时间 |
| `borrowed_at` | 实际借出时间 |
| `due_at` | 应归还时间 |
| `returned_at` | 实际归还时间 |
| `revoked_at` | 撤销时间 |
| `revoke_reason_code` | 撤销原因编码 |
| `workflow_instance_id` | 借阅审批流程引用 |
| `access_grant_ref` | 文档中心或权限底座发放的访问能力引用 |
| `borrow_snapshot_ref` | 借阅审批、权限、封包引用冻结快照 |
| `idempotency_key` | 申请幂等键 |
| `version_no` | 乐观锁版本 |
| `created_at`、`created_by`、`updated_at`、`updated_by`、`is_deleted` | 基础审计字段 |

关键约束与索引方向：

| 类型 | 字段 |
| --- | --- |
| 唯一约束 | `uk_borrow_idempotency(idempotency_key)` |
| 唯一约束 | `uk_active_borrow(archive_record_id, requested_by, borrow_status, is_deleted)`，仅允许业务侧前置校验一个申请人同一归档记录存在一个活动借阅 |
| 索引 | `idx_borrow_contract(contract_id, borrow_status, requested_at)` |
| 索引 | `idx_borrow_archive(archive_record_id, borrow_status, due_at)` |
| 索引 | `idx_borrow_requester(requested_by, borrow_status, due_at)` |
| 索引 | `idx_borrow_overdue(borrow_status, due_at)` |

#### 6.2.2 `cl_archive_return_record`

用途：记录一次归还动作及访问能力回收结果，支持归还失败补偿和人工确认。

| 字段 | 含义 |
| --- | --- |
| `return_record_id` | 归还记录主键 |
| `borrow_record_id` | 借阅记录引用 |
| `contract_id` | 合同主档引用 |
| `archive_record_id` | 归档记录引用 |
| `return_status` | `REQUESTED`、`RECLAIMING_ACCESS`、`CONFIRMED`、`FAILED`、`MANUAL_CONFIRMED` |
| `returned_by` | 发起归还人 |
| `returned_at` | 发起归还时间 |
| `confirmed_by` | 确认人或系统主体 |
| `confirmed_at` | 归还确认时间 |
| `access_reclaim_result` | 访问能力回收结果：`NOT_REQUIRED`、`SUCCEEDED`、`FAILED` |
| `return_evidence_ref` | 归还凭证引用，适用于线下介质或人工确认 |
| `failure_code`、`failure_message` | 失败原因 |
| `idempotency_key` | 归还动作幂等键 |
| `version_no` | 乐观锁版本 |
| `created_at`、`created_by`、`updated_at`、`updated_by`、`is_deleted` | 基础审计字段 |

关键约束与索引方向：

| 类型 | 字段 |
| --- | --- |
| 唯一约束 | `uk_return_idempotency(idempotency_key)` |
| 唯一约束 | `uk_return_borrow(borrow_record_id, return_status, is_deleted)`，避免同一借阅出现多个活动归还单 |
| 索引 | `idx_return_borrow(borrow_record_id, return_status)` |
| 索引 | `idx_return_contract(contract_id, return_status, returned_at)` |
| 索引 | `idx_return_failed(return_status, updated_at)` |

#### 6.2.3 `cl_archive_package_build_task`

用途：记录归档封包生成或重建任务的业务上下文，平台任务中心负责执行状态，本表保留归档子域可恢复输入。

| 字段 | 含义 |
| --- | --- |
| `package_build_task_id` | 封包构建任务主键 |
| `contract_id` | 合同主档引用 |
| `archive_record_id` | 归档记录引用 |
| `archive_batch_no` | 归档批次编号 |
| `build_type` | `INITIAL_BUILD`、`REBUILD`、`MANIFEST_ONLY_REBUILD` |
| `build_status` | `PENDING`、`RUNNING`、`SUCCEEDED`、`FAILED_RETRYABLE`、`FAILED_TERMINAL`、`COMPENSATING` |
| `input_snapshot_ref` | 输入集完整性校验快照 |
| `package_document_id` | 生成后的封包文件引用 |
| `manifest_document_id` | 生成后的清单文件引用 |
| `content_fingerprint` | 输入集指纹，用于重复构建判断 |
| `idempotency_key` | 构建任务幂等键 |
| `platform_job_id` | 平台任务中心作业引用 |
| `attempt_count` | 已尝试次数 |
| `failure_code`、`failure_message` | 最近失败原因 |
| `locked_until` | 执行租约过期时间 |
| `version_no` | 乐观锁版本 |
| `created_at`、`created_by`、`updated_at`、`updated_by`、`is_deleted` | 基础审计字段 |

关键约束与索引方向：

| 类型 | 字段 |
| --- | --- |
| 唯一约束 | `uk_package_build_idempotency(idempotency_key)` |
| 唯一约束 | `uk_package_build_fingerprint(archive_record_id, build_type, content_fingerprint, is_deleted)` |
| 索引 | `idx_package_build_archive(archive_record_id, build_status, created_at)` |
| 索引 | `idx_package_build_job(platform_job_id)` |
| 索引 | `idx_package_build_retry(build_status, locked_until)` |

### 6.3 归档封包目录结构样例

归档封包以 `archive_batch_no` 和 `contract_id` 组成根目录。目录名使用稳定英文编码，展示名称由前端或浏览器侧映射，不把中文文案作为机器解析依据。

```text
ARCHIVE_{archive_batch_no}_{contract_id}/
├── manifest.json
├── metadata/
│   ├── contract-summary.json
│   ├── archive-record.json
│   ├── lifecycle-summary.json
│   └── integrity-check-result.json
├── documents/
│   ├── main-body/
│   │   └── {display_order}_{document_id}_{version_ref}.{ext}
│   ├── attachments/
│   │   └── {display_order}_{document_id}_{version_ref}.{ext}
│   ├── change-agreements/
│   │   └── {display_order}_{document_id}_{version_ref}.{ext}
│   ├── termination-materials/
│   │   └── {display_order}_{document_id}_{version_ref}.{ext}
│   └── performance-evidence/
│       └── {display_order}_{document_id}_{version_ref}.{ext}
├── audit/
│   ├── timeline-events.json
│   └── package-build-log.json
└── checksums/
    ├── files.sha256
    └── manifest.sha256
```

`manifest.json` 最小结构如下：

```json
{
  "manifest_version": "1.0",
  "contract_id": "{contract_id}",
  "archive_record_id": "{archive_record_id}",
  "archive_batch_no": "{archive_batch_no}",
  "generated_at": "{generated_at}",
  "content_fingerprint": "{content_fingerprint}",
  "entries": [
    {
      "entry_path": "documents/main-body/001_{document_id}_{version_ref}.pdf",
      "document_id": "{document_id}",
      "document_version_ref": "{version_ref}",
      "document_role": "MAIN_BODY",
      "sha256": "{sha256}",
      "required": true
    }
  ]
}
```

目录生成规则：

| 规则 | 要求 |
| --- | --- |
| 根目录 | `ARCHIVE_{archive_batch_no}_{contract_id}`，同一封包内唯一 |
| 清单文件 | 固定为 `manifest.json`，作为封包可验证入口 |
| 元数据目录 | 固定为 `metadata/`，只保存结构化摘要和校验结果 |
| 文档目录 | 固定为 `documents/{document_role_group}/`，按文档角色分组 |
| 审计目录 | 固定为 `audit/`，保存时间线和封包构建摘要 |
| 校验目录 | 固定为 `checksums/`，保存文件摘要和清单摘要 |
| 文件排序 | 同组内按 `display_order`、`document_id`、`version_ref` 稳定排序 |

### 6.4 封包生成伪代码

```text
function buildArchivePackage(command):
  idempotencyKey = "archive-package:" + command.archive_record_id + ":" + command.build_type + ":" + command.request_seq
  if packageBuildTaskRepository.existsSucceeded(idempotencyKey):
    return existing package_document_id and manifest_document_id

  acquire lock "cl:archive-package:" + command.archive_record_id
  try:
    archiveRecord = archiveRepository.requireForUpdate(command.archive_record_id)
    assert archiveRecord.archive_status allows package build

    inputSet = archiveInputAssembler.loadStableInputs(archiveRecord.contract_id, archiveRecord.archive_record_id)
    integrityResult = archiveIntegrityChecker.check(inputSet)
    if integrityResult.hasBlockingError:
      mark archiveRecord.archive_integrity_status = FAILED
      create timeline event ARCHIVE_INTEGRITY_FAILED
      return failed result

    contentFingerprint = fingerprint(inputSet.requiredDocumentRefs, inputSet.metadataSummary)
    task = packageBuildTaskRepository.createOrReuse(idempotencyKey, contentFingerprint)
    if task.build_status == SUCCEEDED:
      return task result

    manifest = manifestBuilder.build(inputSet, integrityResult, contentFingerprint)
    directoryPlan = directoryPlanner.plan(manifest)
    packageFile = packageWriter.write(directoryPlan, inputSet.documentStreams)
    checksumResult = checksumWriter.write(packageFile, manifest)

    packageDocument = documentCenter.registerArchivePackage(packageFile, checksumResult)
    manifestDocument = documentCenter.registerArchiveManifest(manifest)

    archiveRepository.attachPackage(
      archive_record_id = archiveRecord.archive_record_id,
      package_document_id = packageDocument.document_id,
      manifest_document_id = manifestDocument.document_id,
      content_fingerprint = contentFingerprint
    )
    lifecycleDocumentRefRepository.upsertArchiveRefs(archiveRecord, packageDocument, manifestDocument)
    timelineRepository.insertDedupe(archiveRecord.contract_id, "ARCHIVE_PACKAGE_BUILT", contentFingerprint)
    packageBuildTaskRepository.markSucceeded(task, packageDocument, manifestDocument)
    return packageDocument and manifestDocument
  catch retryableError:
    packageBuildTaskRepository.markFailedRetryable(idempotencyKey, retryableError)
    schedule compensation task ARCHIVE_PACKAGE_RETRY
    throw retryableError
  catch terminalError:
    packageBuildTaskRepository.markFailedTerminal(idempotencyKey, terminalError)
    mark archiveRecord.archive_status = ARCHIVE_BUILD_FAILED
    create timeline event ARCHIVE_PACKAGE_BUILD_FAILED
    throw terminalError
  finally:
    release lock "cl:archive-package:" + command.archive_record_id
```

### 6.5 幂等键与锁粒度

| 场景 | 幂等键 | 锁粒度 | 数据库兜底 |
| --- | --- | --- | --- |
| 归档封包首次生成 | `archive-package:{archive_record_id}:INITIAL_BUILD:{request_seq}` | `cl:archive-package:{archive_record_id}` | `uk_package_build_idempotency`、`uk_package_build_fingerprint` |
| 归档封包重建 | `archive-package:{archive_record_id}:REBUILD:{content_fingerprint}` | `cl:archive-package:{archive_record_id}` | `uk_package_build_fingerprint` |
| 清单重建 | `archive-package:{archive_record_id}:MANIFEST_ONLY_REBUILD:{content_fingerprint}` | `cl:archive-manifest:{archive_record_id}` | `uk_package_build_fingerprint` |
| 借阅申请 | `archive-borrow:{archive_record_id}:{requested_by}:{request_seq}` | `cl:archive-borrow:{archive_record_id}:{requested_by}` | `uk_borrow_idempotency` |
| 借出确认 | `archive-borrow-deliver:{borrow_record_id}:{action_seq}` | `cl:archive-borrow-record:{borrow_record_id}` | 条件更新 `borrow_status=APPROVED` |
| 逾期扫描 | `archive-borrow-overdue:{borrow_record_id}:{due_at}` | `cl:archive-borrow-record:{borrow_record_id}` | 条件更新 `borrow_status=BORROWED and due_at < now` |
| 借阅撤销 | `archive-borrow-revoke:{borrow_record_id}:{revoke_seq}` | `cl:archive-borrow-record:{borrow_record_id}` | 条件更新活动状态 |
| 归还确认 | `archive-return:{borrow_record_id}:{return_seq}` | `cl:archive-return:{borrow_record_id}` | `uk_return_idempotency` |

锁使用原则：

- 封包构建锁只覆盖同一 `archive_record_id`，不扩大到 `contract_id`，避免阻塞同合同其他履约、变更、终止动作。
- 借阅锁以 `archive_record_id + requested_by` 或 `borrow_record_id` 为粒度，不阻塞其他用户对同一归档记录的只读查询。
- 锁只做短期互斥，最终一致性由唯一约束、条件更新和 `version_no` 乐观锁兜底。

### 6.6 补偿任务清单

| 补偿任务 | 触发条件 | 扫描条件 | 补偿动作 | 终止条件 |
| --- | --- | --- | --- | --- |
| `ARCHIVE_PACKAGE_RETRY` | 封包构建出现可重试失败 | `cl_archive_package_build_task.build_status=FAILED_RETRYABLE` 且租约过期 | 重新装载输入快照并重试封包构建 | 成功、达到最大重试、转人工 |
| `ARCHIVE_PACKAGE_REF_REPAIR` | 封包文件已登记但归档记录引用未回写 | 任务成功但 `cl_archive_record.package_document_id` 为空 | 回写归档记录与 `cl_lifecycle_document_ref` | 引用补齐或人工确认文件不存在 |
| `ARCHIVE_MANIFEST_REPAIR` | 清单文件缺失或校验摘要不一致 | `manifest_document_id` 为空或 `manifest.sha256` 不匹配 | 重建 `manifest.json` 并重新登记清单引用 | 清单校验通过 |
| `ARCHIVE_TIMELINE_BACKFILL` | 归档封包成功但时间线缺口 | 成功任务无 `ARCHIVE_PACKAGE_BUILT` 去重事件 | 补写时间线与摘要块 | 时间线去重键存在 |
| `BORROW_OVERDUE_SCAN` | 借阅到期未归还 | `borrow_status=BORROWED` 且 `due_at < now` | 状态改为 `OVERDUE`，发通知和审计 | 已归还、已撤销或已标记逾期 |
| `BORROW_ACCESS_RECLAIM_RETRY` | 撤销或归还后访问能力回收失败 | `return_status=FAILED` 或 `borrow_status=REVOKED` 且访问能力仍有效 | 重试调用文档中心或权限底座回收能力 | 回收成功、转人工 |
| `BORROW_SUMMARY_REPAIR` | 借阅状态变化后摘要未刷新 | 借阅记录更新时间晚于摘要归档块更新时间 | 重算归档借阅摘要和待办 | 摘要版本覆盖最新借阅状态 |

## 7. 本文结论

归档封包与借阅归还的治理设计与实现基线已明确为：

- 归档封包治理对象是输入集完整性规则、封包格式定义、目录结构规则、归档产物引用规则、批次管理规则，最小治理单元按输入来源、格式要素、目录层级、产物类型分治。
- 归档封包的稳定锚点是 `contract_id`、归档记录 ID、归档状态、批次编号、封包引用，不引用流程实例或文件内容。
- 归档封包版本归属 `contract-lifecycle` 模块，随模块版本演进，不单独版本化，不对外暴露为独立 API 资源。
- 借阅/归还子域治理边界是借阅规则、权限定义、归还规则、状态机、映射关系、呈现约束；实现基线补齐 `cl_archive_borrow_record`、`cl_archive_return_record`、`cl_archive_package_build_task` 三类表级对象草案。
- 借阅/归还子域责任划分明确：定义权和解释权归 `contract-lifecycle` 模块，借阅权限执行权部分归属 `contract-core` 模块和平台权限底座，消费权归摘要、时间线、通知、审计，借阅审计权归平台审计中心。
- 与前三份专项设计的关系：履约规则、变更影响、终止善后是借阅/归还的间接或直接治理约束，但借阅/归还不修改前三份专项设计的治理对象。
- 与 `contract-core` 的边界：归档封包与借阅归还不拥有合同主档，只通过 `LifecycleIntegrationFacade` 回写归档结果。
- 与 `document-center` 的边界：归档封包与借阅归还不拥有文件对象，文件只作为归档封包的产物引用，借阅的文件交付执行权归属文档中心。
- 与 `workflow-engine` 的边界：归档封包与借阅归还不引用流程实例，审批结果是归档、借阅的前置条件但不是稳定锚点。
- 封包目录结构、`manifest.json` 样例、封包生成伪代码、幂等键、锁粒度和补偿任务清单已作为本文实现落点，后续开发不需要另建专项承接同一内容。
