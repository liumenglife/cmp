# 消费方适配与压力测试专项设计

## 1. 文档定位

本文档是 `encrypted-document` 子模块的**消费方适配与压力测试治理设计**，承接 [`detailed-design.md`](../detailed-design.md) 第 12 节中"签章、归档、搜索、AI 各消费方的专项适配细节与压力测试参数"的专项下沉。

本文档只回答以下问题：
- 签章消费方适配如何治理：对象是谁、稳定锚点在哪里、版本如何归属、最小治理单元是什么
- 归档消费方适配如何治理：对象是谁、稳定锚点在哪里、版本如何归属、最小治理单元是什么
- 搜索消费方适配如何治理：对象是谁、稳定锚点在哪里、版本如何归属、最小治理单元是什么
- AI 消费方适配如何治理：对象是谁、稳定锚点在哪里、版本如何归属、最小治理单元是什么
- 压力测试参数的治理边界在哪里、责任如何划分
- 与前五份专项设计的引用关系
- 与 `contract-core`、`document-center` 的边界如何划分

本文档**不承担**以下内容：
- 不展开 API 路径、请求/响应结构、错误码
- 不输出 DDL、表结构、索引细节
- 不编写消费方适配代码、回调处理代码、压力测试脚本代码
- 不包含实施排期、工时估算、负责人分配
- 不写成运维手册、部署方案、故障排查指南
- 不描述具体适配实现、压力测试工具配置、性能调优实现

---

## 2. 签章消费方适配治理

### 2.1 治理对象

签章消费方适配治理的**一级对象**是 `signature-adaptation-policy`（签章适配策略），每个策略封装签章服务对加密文档的受控访问规则与安全基线。

治理对象层级：

| 层级 | 对象 | 说明 |
| --- | --- | --- |
| 一级 | `signature-adaptation-policy` | 签章适配策略，是签章消费方适配治理的最小单元 |
| 二级 | `signature-access-spec` | 签章访问规格，描述签章场景的读取模式、时效约束、回调要求 |
| 三级 | `signature-binding` | 签章绑定，将适配策略与签章服务关联 |

`signature-adaptation-policy` 不持有明文或密文内容，不持有签章结果，只持有"签章服务以什么模式、在什么时效内、通过什么回调、获得受控访问"的治理描述。

### 2.2 稳定锚点

签章适配治理的**稳定锚点**是 `signature-policy-code`（签章策略编码），其稳定性约束如下：

- `signature-policy-code` 是跨模块、跨版本的稳定引用键，签章服务、审计链路均通过 `signature-policy-code` 引用签章适配策略。
- `signature-policy-code` 一旦发布，不允许修改其核心访问模式（如必须走受控读取、必须带水印预览）；若需变更适配规则，只能创建新 `signature-policy-code`。
- `signature-policy-code` 与 `document-center` 的松耦合：文档中心只持有原始密文，不感知签章适配策略内部规则，签章访问由加密模块在受控读取后执行。
- `signature-policy-code` 与 `contract-core` 的松耦合：合同主档不持有签章适配策略状态，签章适配策略仅通过 `contract_id` 间接关联到合同上下文。
- `signature-policy-code` 与 `e-signature` 模块的松耦合：签章模块只通过 `signature-policy-code` 引用适配策略，不感知策略内部细节。

### 2.3 版本归属

签章适配策略版本归属规则：

| 版本维度 | 归属主体 | 说明 |
| --- | --- | --- |
| `policy-version` | `encrypted-document` 子模块 | 签章适配策略自身的版本演进，由加密模块独立治理 |
| `spec-version` | `encrypted-document` 子模块 | 访问规格版本，随签章要求或合规要求演进 |
| `binding-version` | `encrypted-document` 子模块 | 策略绑定版本，跟随策略版本联动 |
| `document-version` | `document-center` | 文档版本，加密模块只引用，不拥有 |
| `contract-version` | `contract-core` | 合同版本，加密模块不直接访问 |
| `signature-version` | `e-signature` 模块 | 签章版本，加密模块不直接访问 |

版本归属的核心原则：**签章适配策略版本由加密模块自治，文档版本、合同版本、签章版本由各自归属模块治理，通过 `signature-policy-code` 建立跨模块关联**。

### 2.4 最小治理单元

签章适配的最小治理单元是 **`signature-adaptation-policy` + `signature-access-spec` 的组合**，具备以下治理属性：

- 可独立启用/停用（关联签章服务授权生效/失效）
- 可独立审计（谁在何时定义了哪个签章适配策略、应用于哪个签章服务）
- 可独立评估（适配强度是否满足签章场景安全需求）
- 可独立下线（标记废弃，不影响历史签章记录的可审计性）

最小治理单元**不包含**具体签章算法实现、具体文件格式转换逻辑、具体回调处理代码——这些属于执行层，不在治理设计范围内。

---

## 3. 归档消费方适配治理

### 3.1 治理对象

归档消费方适配治理的**一级对象**是 `archive-adaptation-policy`（归档适配策略），每个策略封装归档服务对加密文档的受控访问规则与安全基线。

治理对象层级：

| 层级 | 对象 | 说明 |
| --- | --- | --- |
| 一级 | `archive-adaptation-policy` | 归档适配策略，是归档消费方适配治理的最小单元 |
| 二级 | `archive-access-spec` | 归档访问规格，描述归档场景的读取模式、存储要求、保留策略 |
| 三级 | `archive-binding` | 归档绑定，将适配策略与归档服务关联 |

`archive-adaptation-policy` 不持有明文或密文内容，不持有归档结果，只持有"归档服务以什么模式、按什么保留策略、将受控访问结果交付到哪"的治理描述。

### 3.2 稳定锚点

归档适配治理的**稳定锚点**是 `archive-policy-code`（归档策略编码），其稳定性约束如下：

- `archive-policy-code` 是跨模块、跨版本的稳定引用键，归档服务、审计链路均通过 `archive-policy-code` 引用归档适配策略。
- `archive-policy-code` 一旦发布，不允许修改其核心访问模式（如必须走受控读取、必须保留加密标识）；若需变更适配规则，只能创建新 `archive-policy-code`。
- `archive-policy-code` 与 `document-center` 的松耦合：文档中心只持有原始密文，不感知归档适配策略内部规则，归档访问由加密模块在受控读取后执行。
- `archive-policy-code` 与 `contract-core` 的松耦合：合同主档不持有归档适配策略状态，归档适配策略仅通过 `contract_id` 间接关联到合同上下文。

### 3.3 版本归属

归档适配策略版本归属规则：

| 版本维度 | 归属主体 | 说明 |
| --- | --- | --- |
| `policy-version` | `encrypted-document` 子模块 | 归档适配策略自身的版本演进，由加密模块独立治理 |
| `spec-version` | `encrypted-document` 子模块 | 访问规格版本，随归档要求或合规要求演进 |
| `binding-version` | `encrypted-document` 子模块 | 策略绑定版本，跟随策略版本联动 |
| `document-version` | `document-center` | 文档版本，加密模块只引用，不拥有 |
| `contract-version` | `contract-core` | 合同版本，加密模块不直接访问 |

版本归属的核心原则：**归档适配策略版本由加密模块自治，文档版本和合同版本由各自归属模块治理，通过 `archive-policy-code` 建立跨模块关联**。

### 3.4 最小治理单元

归档适配的最小治理单元是 **`archive-adaptation-policy` + `archive-access-spec` 的组合**，具备以下治理属性：

- 可独立启用/停用（关联归档服务授权生效/失效）
- 可独立审计（谁在何时定义了哪个归档适配策略、应用于哪个归档服务）
- 可独立评估（适配强度是否满足归档场景安全需求）
- 可独立下线（标记废弃，不影响历史归档记录的可审计性）

最小治理单元**不包含**具体归档格式实现、具体存储介质交互逻辑、具体保留策略执行代码——这些属于执行层，不在治理设计范围内。

---

## 4. 搜索消费方适配治理

### 4.1 治理对象

搜索消费方适配治理的**一级对象**是 `search-adaptation-policy`（搜索适配策略），每个策略封装搜索服务对加密文档脱敏文本的二次存储与访问规则。

治理对象层级：

| 层级 | 对象 | 说明 |
| --- | --- | --- |
| 一级 | `search-adaptation-policy` | 搜索适配策略，是搜索消费方适配治理的最小单元 |
| 二级 | `search-access-spec` | 搜索访问规格，描述搜索场景的脱敏策略、索引要求、查询边界 |
| 三级 | `search-binding` | 搜索绑定，将适配策略与搜索服务关联 |

`search-adaptation-policy` 不持有明文或密文内容，不持有搜索索引，只持有"搜索服务使用哪个脱敏策略、构建什么类型的索引、查询边界如何限定"的治理描述。

### 4.2 稳定锚点

搜索适配治理的**稳定锚点**是 `search-policy-code`（搜索策略编码），其稳定性约束如下：

- `search-policy-code` 是跨模块、跨版本的稳定引用键，搜索服务、审计链路均通过 `search-policy-code` 引用搜索适配策略。
- `search-policy-code` 一旦发布，不允许修改其核心脱敏维度（如哪些字段必须脱敏、哪些信息必须保留）；若需变更适配规则，只能创建新 `search-policy-code`。
- `search-policy-code` 与 `document-center` 的松耦合：文档中心只持有原始密文，不感知搜索适配策略内部规则，脱敏由加密模块在受控读取后执行。
- `search-policy-code` 与 `contract-core` 的松耦合：合同主档不持有搜索适配策略状态，搜索适配策略仅通过 `contract_id` 间接关联到合同上下文。

### 4.3 版本归属

搜索适配策略版本归属规则：

| 版本维度 | 归属主体 | 说明 |
| --- | --- | --- |
| `policy-version` | `encrypted-document` 子模块 | 搜索适配策略自身的版本演进，由加密模块独立治理 |
| `spec-version` | `encrypted-document` 子模块 | 访问规格版本，随搜索要求或隐私政策演进 |
| `binding-version` | `encrypted-document` 子模块 | 策略绑定版本，跟随策略版本联动 |
| `document-version` | `document-center` | 文档版本，加密模块只引用，不拥有 |
| `contract-version` | `contract-core` | 合同版本，加密模块不直接访问 |

版本归属的核心原则：**搜索适配策略版本由加密模块自治，文档版本和合同版本由各自归属模块治理，通过 `search-policy-code` 建立跨模块关联**。

### 4.4 最小治理单元

搜索适配的最小治理单元是 **`search-adaptation-policy` + `search-access-spec` 的组合**，具备以下治理属性：

- 可独立启用/停用（关联搜索服务授权生效/失效）
- 可独立审计（谁在何时定义了哪个搜索适配策略、应用于哪个搜索服务）
- 可独立评估（脱敏强度是否满足搜索场景隐私需求）
- 可独立下线（标记废弃，不影响历史搜索索引的可审计性）

最小治理单元**不包含**具体搜索引擎实现、具体索引构建逻辑、具体脱敏算法代码——这些属于执行层，不在治理设计范围内。

---

## 5. AI 消费方适配治理

### 5.1 治理对象

AI 消费方适配治理的**一级对象**是 `ai-adaptation-policy`（AI 适配策略），每个策略封装 AI 服务对加密文档脱敏文本的二次存储与推理规则。

治理对象层级：

| 层级 | 对象 | 说明 |
| --- | --- | --- |
| 一级 | `ai-adaptation-policy` | AI 适配策略，是 AI 消费方适配治理的最小单元 |
| 二级 | `ai-access-spec` | AI 访问规格，描述 AI 场景的脱敏策略、向量化要求、推理边界 |
| 三级 | `ai-binding` | AI 绑定，将适配策略与 AI 服务关联 |

`ai-adaptation-policy` 不持有明文或密文内容，不持有向量嵌入，只持有"AI 服务使用哪个脱敏策略、构建什么类型的向量存储、推理边界如何限定"的治理描述。

### 5.2 稳定锚点

AI 适配治理的**稳定锚点**是 `ai-policy-code`（AI 策略编码），其稳定性约束如下：

- `ai-policy-code` 是跨模块、跨版本的稳定引用键，AI 服务、审计链路均通过 `ai-policy-code` 引用 AI 适配策略。
- `ai-policy-code` 一旦发布，不允许修改其核心脱敏维度（如哪些字段必须脱敏、哪些信息必须保留）；若需变更适配规则，只能创建新 `ai-policy-code`。
- `ai-policy-code` 与 `document-center` 的松耦合：文档中心只持有原始密文，不感知 AI 适配策略内部规则，脱敏由加密模块在受控读取后执行。
- `ai-policy-code` 与 `contract-core` 的松耦合：合同主档不持有 AI 适配策略状态，AI 适配策略仅通过 `contract_id` 间接关联到合同上下文。
- `ai-policy-code` 与 `Agent OS` 的松耦合：AI 服务只通过 `ai-policy-code` 引用适配策略，不感知策略内部细节。

### 5.3 版本归属

AI 适配策略版本归属规则：

| 版本维度 | 归属主体 | 说明 |
| --- | --- | --- |
| `policy-version` | `encrypted-document` 子模块 | AI 适配策略自身的版本演进，由加密模块独立治理 |
| `spec-version` | `encrypted-document` 子模块 | 访问规格版本，随 AI 要求或隐私政策演进 |
| `binding-version` | `encrypted-document` 子模块 | 策略绑定版本，跟随策略版本联动 |
| `document-version` | `document-center` | 文档版本，加密模块只引用，不拥有 |
| `contract-version` | `contract-core` | 合同版本，加密模块不直接访问 |
| `agent-version` | `Agent OS` | AI Agent 版本，加密模块不直接访问 |

版本归属的核心原则：**AI 适配策略版本由加密模块自治，文档版本、合同版本、Agent 版本由各自归属模块治理，通过 `ai-policy-code` 建立跨模块关联**。

### 5.4 最小治理单元

AI 适配的最小治理单元是 **`ai-adaptation-policy` + `ai-access-spec` 的组合**，具备以下治理属性：

- 可独立启用/停用（关联 AI 服务授权生效/失效）
- 可独立审计（谁在何时定义了哪个 AI 适配策略、应用于哪个 AI 服务）
- 可独立评估（脱敏强度是否满足 AI 场景隐私需求）
- 可独立下线（标记废弃，不影响历史向量存储的可审计性）

最小治理单元**不包含**具体向量化实现、具体推理逻辑、具体脱敏算法代码——这些属于执行层，不在治理设计范围内。

---

## 6. 压力测试参数治理

### 6.1 治理对象

压力测试参数治理的**一级对象**是 `pressure-test-policy`（压力测试策略），每个策略封装针对各消费方的压力测试场景定义、参数配置与评估标准。

治理对象层级：

| 层级 | 对象 | 说明 |
| --- | --- | --- |
| 一级 | `pressure-test-policy` | 压力测试策略，是压力测试治理的最小单元 |
| 二级 | `test-scenario-spec` | 测试场景规格，描述并发量、持续时间、请求模式 |
| 三级 | `test-baseline` | 测试基线，定义通过标准、告警阈值、熔断条件 |

`pressure-test-policy` 不持有测试执行代码，不持有具体测试数据，只持有"在哪个消费方、模拟什么负载、达到什么标准、触发什么告警"的治理描述。

### 6.2 稳定锚点

压力测试治理的**稳定锚点**是 `test-policy-code`（测试策略编码），其稳定性约束如下：

- `test-policy-code` 是跨模块、跨版本的稳定引用键，测试服务、审计链路均通过 `test-policy-code` 引用压力测试策略。
- `test-policy-code` 一旦发布，不允许修改其核心测试基线（如并发下限、成功率下限）；若需变更测试参数，只能创建新 `test-policy-code`。
- `test-policy-code` 与各消费方的松耦合：消费方只通过 `test-policy-code` 引用测试策略，不感知策略内部细节。

### 6.3 版本归属

压力测试策略版本归属规则：

| 版本维度 | 归属主体 | 说明 |
| --- | --- | --- |
| `policy-version` | `encrypted-document` 子模块 | 压力测试策略自身的版本演进，由加密模块独立治理 |
| `scenario-version` | `encrypted-document` 子模块 | 测试场景版本，随性能要求或容量规划演进 |
| `baseline-version` | `encrypted-document` 子模块 | 测试基线版本，跟随性能目标联动 |
| `consumer-version` | 各消费方模块 | 消费方版本，加密模块只引用，不直接访问 |

版本归属的核心原则：**压力测试策略版本由加密模块自治，消费方版本由各自归属模块治理，通过 `test-policy-code` 建立跨模块关联**。

### 6.4 最小治理单元

压力测试的最小治理单元是 **`pressure-test-policy` + `test-scenario-spec` + `test-baseline` 的组合**，具备以下治理属性：

- 可独立启用/停用（关联测试场景生效/失效）
- 可独立审计（谁在何时定义了哪个压力测试策略、应用于哪个消费方）
- 可独立评估（测试基线是否满足性能需求）
- 可独立下线（标记废弃，不影响历史测试记录的可审计性）

最小治理单元**不包含**具体测试脚本代码、具体压测工具配置、具体监控采集代码——这些属于执行层，不在治理设计范围内。

### 6.5 各消费方压力测试参数边界

| 消费方 | 测试场景 | 核心参数 | 治理边界 |
| --- | --- | --- | --- |
| 签章 | 并发签章请求 | 并发数、签名包大小、回调超时 | 加密模块定义测试基线，签章模块只引用测试结果 |
| 归档 | 批量归档请求 | 批次大小、归档频率、存储吞吐 | 加密模块定义测试基线，归档模块只引用测试结果 |
| 搜索 | 索引刷新请求 | 刷新频率、文档量级、查询 QPS | 加密模块定义测试基线，搜索模块只引用测试结果 |
| AI | 向量化推理请求 | 并发推理数、上下文长度、推理耗时 | 加密模块定义测试基线，AI 模块只引用测试结果 |

### 6.6 责任划分

压力测试参数的责任划分：

**`encrypted-document` 负责的边界：**
- 压力测试策略的治理规则（测试基线、告警阈值、熔断条件）
- 各消费方测试场景的定义与参数配置
- 测试结果的评估标准与通过规则
- 测试策略与消费方的绑定关系
- 压力测试事件的审计要求

**`encrypted-document` 不负责的边界：**
- 测试脚本的具体实现（由测试执行层负责）
- 压测工具的选择与配置（由测试执行层负责）
- 监控采集与告警通知的具体实现（由运维层负责）

**各消费方的边界：**
- 持有各自的服务实现、性能特征、容量上限
- 只通过 `test-policy-code` 引用压力测试策略，不感知策略内部细节
- 负责提供测试所需的最小服务接口，不负责测试策略定义

---

## 7. 与前五份专项设计的引用关系

本文档与 [`crypto-algorithm-and-key-hierarchy-design.md`](crypto-algorithm-and-key-hierarchy-design.md)、[`controlled-read-handle-design.md`](controlled-read-handle-design.md)、[`plaintext-export-package-design.md`](plaintext-export-package-design.md)、[`authorization-scope-expression-design.md`](authorization-scope-expression-design.md) 和 [`desensitization-and-secondary-storage-design.md`](desensitization-and-secondary-storage-design.md) 的引用关系：

### 7.1 架构层级关系

```
加密算法与密钥层级专项设计（第一份）
  └── encryption-profile（加密方案）
        └── controlled-read-handle-design（第二份）
              └── read-handle（读取句柄）
                    └── plaintext-export-package-design（第三份）
                          └── export-package（导出包）
                                └── authorization-scope-expression-design（第四份）
                                      └── scope-expression（范围表达式）
                                            └── desensitization-and-secondary-storage-design（第五份）
                                                  └── desensitization-policy（脱敏策略）
                                                        └── consumer-adaptation-and-pressure-test-design（本文档）
                                                              └── signature-adaptation-policy（签章适配策略，本文档）
                                                              └── archive-adaptation-policy（归档适配策略，本文档）
                                                              └── search-adaptation-policy（搜索适配策略，本文档）
                                                              └── ai-adaptation-policy（AI 适配策略，本文档）
                                                              └── pressure-test-policy（压力测试策略，本文档）
```

### 7.2 治理依赖关系

| 本文档依赖前五份的内容 | 说明 |
| --- | --- |
| `encryption-profile` | 消费方适配依赖加密方案定义的原始保护边界，所有受控访问必须基于加密方案成立 |
| `read-handle` | 消费方适配依赖读取句柄获取受控访问，签章/归档/搜索/AI 均通过读取句柄获得临时访问能力 |
| `export-package` | 消费方适配与导出包共享"明文临时访问"的治理边界，但目标不同：导出包面向人工下载，消费方适配面向机器服务 |
| `scope-expression` | 消费方适配的授权（哪些签章/归档/搜索/AI 服务可以访问）依赖范围表达式定义的授权边界 |
| `desensitization-policy` | 搜索/AI 消费方适配依赖脱敏策略，搜索索引和 AI 向量化必须使用脱敏后的文本 |

### 7.3 治理边界划分

- **第一份专项设计**负责：加密算法选择、密钥层级划分、密钥轮换策略、介质保护和密钥托管
- **第二份专项设计**负责：读取句柄治理、流式分段策略治理、临时缓存介质治理
- **第三份专项设计**负责：导出包治理、水印选项治理、脱敏选项治理、文件名规范治理
- **第四份专项设计**负责：范围表达式治理、细粒度字段扩展治理、标签扩展治理、业务规则扩展治理
- **第五份专项设计**负责：脱敏策略治理、二次存储治理、搜索/AI 消费方接入模式治理
- **本文档**负责：签章/归档/搜索/AI 消费方适配治理、压力测试参数治理
- **六个文档的共同约束**：都不持有文档明文内容、都不编写实现代码、都保持与 `document-center` 和 `contract-core` 的松耦合

---

## 8. 与 `contract-core`、`document-center` 的边界说明

### 8.1 与 `document-center` 的边界

| 边界维度 | `document-center` 负责 | `encrypted-document` 负责 |
| --- | --- | --- |
| 文件真相 | 持有文档资产、版本链、存储定位 | 不持有文件真相，只引用 `document_asset_id` |
| 版本管理 | 管理文档版本的新增、切换、失效 | 只感知当前版本，不管理版本生命周期 |
| 消费方适配 | 不感知消费方适配策略 | 持有签章/归档/搜索/AI 适配策略的治理状态、规格、绑定关系 |
| 压力测试 | 不参与压力测试策略治理 | 持有压力测试策略、场景规格、测试基线 |
| 受控访问 | 提供密文读取接口 | 通过读取句柄提供受控访问，消费方适配基于读取句柄成立 |
| 测试结果 | 只引用测试结果，不感知测试策略 | 定义测试基线、评估标准、告警阈值 |

引用关系：`document-center` ← `document_asset_id` ← `encrypted-document`（消费方适配 + 压力测试）

### 8.2 与 `contract-core` 的边界

| 边界维度 | `contract-core` 负责 | `encrypted-document` 负责 |
| --- | --- | --- |
| 合同主档 | 持有合同业务状态、生命周期 | 不持有合同主档，只引用 `contract_id` |
| 业务权限 | 管理合同的业务级权限 | 只读取权限结果，不管理业务权限 |
| 组织归属 | 管理合同归属部门、负责人 | 只读取组织快照，不管理组织关系 |
| 消费方适配 | 不感知消费方适配策略 | 持有签章/归档/搜索/AI 适配策略的治理状态 |
| 压力测试 | 不参与压力测试策略治理 | 持有压力测试策略、场景规格、测试基线 |
| 测试结果 | 只引用测试结果，不感知测试策略 | 定义测试基线、评估标准、告警阈值 |

引用关系：`contract-core` ← `contract_id` ← `encrypted-document`（消费方适配 + 压力测试）

### 8.3 与各消费方模块的边界

| 消费方模块 | 消费方负责 | `encrypted-document` 负责 |
| --- | --- | --- |
| 签章模块 (`e-signature`) | 持有签章服务、签章结果、回调处理 | 持有签章适配策略，定义签章场景的受控访问规则 |
| 归档模块 | 持有归档服务、归档结果、存储介质 | 持有归档适配策略，定义归档场景的受控访问规则 |
| 搜索模块 | 持有搜索引擎、索引结构、查询接口 | 持有搜索适配策略，定义搜索场景的脱敏策略与二次存储规则 |
| AI 模块 (`Agent OS`) | 持有 AI 服务、向量嵌入、推理接口 | 持有 AI 适配策略，定义 AI 场景的脱敏策略与二次存储规则 |

跨模块引用原则：
- **自上而下引用**：合同 → 文档 → 加密方案 → 读取句柄 → 消费方适配
- **禁止反向依赖**：消费方适配不持有合同主档引用，不持有文档内容引用
- **松耦合**：`signature-policy-code`、`archive-policy-code`、`search-policy-code`、`ai-policy-code`、`test-policy-code` 是跨模块稳定键，各模块不直接依赖对方内部模型
- **治理隔离**：各消费方模块不感知适配策略的存在细节，只通过策略编码引用适配规则

---

## 9. 审计最小单元

### 9.1 签章适配审计最小单元

签章适配审计最小单元是 **`signature-adaptation-policy` + `signature-access-spec` + 消费记录**，审计必须覆盖：

| 审计要素 | 说明 |
| --- | --- |
| 签章策略编码 | `signature-policy-code` |
| 签章规格标识 | `signature-access-spec-id` |
| 关联签章服务 | 签章服务标识（非服务实现细节） |
| 变更类型 | 新增 / 启用 / 停用 / 规格调整 / 版本升级 / 绑定到签章服务 |
| 变更主体 | 操作人 / 系统 / 策略触发 |
| 变更时间 | 操作时间戳 |
| 影响范围 | 受影响的签章场景描述（不展开到具体文档列表或合同列表） |

### 9.2 归档适配审计最小单元

归档适配审计最小单元是 **`archive-adaptation-policy` + `archive-access-spec` + 消费记录**，审计必须覆盖：

| 审计要素 | 说明 |
| --- | --- |
| 归档策略编码 | `archive-policy-code` |
| 归档规格标识 | `archive-access-spec-id` |
| 关联归档服务 | 归档服务标识（非服务实现细节） |
| 变更类型 | 新增 / 启用 / 停用 / 规格调整 / 版本升级 / 绑定到归档服务 |
| 变更主体 | 操作人 / 系统 / 策略触发 |
| 变更时间 | 操作时间戳 |
| 影响范围 | 受影响的归档场景描述（不展开到具体文档列表或合同列表） |

### 9.3 搜索适配审计最小单元

搜索适配审计最小单元是 **`search-adaptation-policy` + `search-access-spec` + 消费记录**，审计必须覆盖：

| 审计要素 | 说明 |
| --- | --- |
| 搜索策略编码 | `search-policy-code` |
| 搜索规格标识 | `search-access-spec-id` |
| 关联搜索服务 | 搜索服务标识（非服务实现细节） |
| 变更类型 | 新增 / 启用 / 停用 / 规格调整 / 版本升级 / 绑定到搜索服务 |
| 变更主体 | 操作人 / 系统 / 策略触发 |
| 变更时间 | 操作时间戳 |
| 影响范围 | 受影响的搜索场景描述（不展开到具体文档列表或合同列表） |

### 9.4 AI 适配审计最小单元

AI 适配审计最小单元是 **`ai-adaptation-policy` + `ai-access-spec` + 消费记录**，审计必须覆盖：

| 审计要素 | 说明 |
| --- | --- |
| AI 策略编码 | `ai-policy-code` |
| AI 规格标识 | `ai-access-spec-id` |
| 关联 AI 服务 | AI 服务标识（非服务实现细节） |
| 变更类型 | 新增 / 启用 / 停用 / 规格调整 / 版本升级 / 绑定到 AI 服务 |
| 变更主体 | 操作人 / 系统 / 策略触发 |
| 变更时间 | 操作时间戳 |
| 影响范围 | 受影响的 AI 场景描述（不展开到具体文档列表或合同列表） |

### 9.5 压力测试审计最小单元

压力测试审计最小单元是 **`pressure-test-policy` + `test-scenario-spec` + `test-baseline` + 测试记录**，审计必须覆盖：

| 审计要素 | 说明 |
| --- | --- |
| 测试策略编码 | `test-policy-code` |
| 测试场景标识 | `test-scenario-spec-id` |
| 测试基线标识 | `test-baseline-id` |
| 关联消费方 | 签章 / 归档 / 搜索 / AI（非消费方内部细节） |
| 变更类型 | 新增 / 启用 / 停用 / 场景调整 / 基线升级 / 绑定到消费方 / 测试执行 |
| 变更主体 | 操作人 / 系统 / 定时触发 |
| 变更时间 | 操作时间戳 |
| 测试结果 | 通过 / 失败 / 熔断（不展开到具体测试数据或监控细节） |

### 9.6 审计关联约束

- 审计事件只记录治理动作，不记录适配执行细节（执行细节由 `ed_encryption_audit_event` 记录）
- 审计事件中的策略标识不包含策略内容本身，只保留策略编码和版本号
- 审计事件与 `contract-core`、`document-center` 的关联通过策略编码间接建立，不直接引用合同或文档内部状态
- 审计事件保留期限按合规要求设定，与适配策略生命周期解耦
- 压力测试结果不在审计事件中记录详细性能数据，只记录测试结果与基线对比结论

---

## 10. 与总平台架构的对应关系

本文档的治理设计基于以下总平台架构约束：

| 架构约束来源 | 对应章节 | 本文档遵循方式 |
| --- | --- | --- |
| `architecture-design.md` 5.4 总平台与子模块边界 | §5.4 | 加密模块不拥有文件真相源和合同主档，只持有安全治理状态 |
| `architecture-design.md` 8.2 自研子模块边界 | §8.2 | 加密模块挂在文档中心读写路径上，是平台内正式子模块 |
| `../architecture-design.md` 5. 关键组件划分 | §5 | 消费方适配由 `Capability Consumption Adapter` 组件治理 |
| `../architecture-design.md` 6. 与文档中心的关系 | §6 | 消费方适配通过文档中心与加密边界协同读取 |
| `detailed-design.md` 2.2 约束落点 | §2.2 | 加密子模块不拥有文件真相源和合同主档，只持有安全治理状态与审计事实 |
| `detailed-design.md` 3.6 `capability-consumer-adapter` | §3.6 | 消费方适配器统一挂接签章、归档、搜索、AI，本文档定义其治理规则 |
| `detailed-design.md` 8.3 签章、归档、搜索、AI 挂接 | §8.3 | 本文档定义各消费方的适配策略与压力测试参数治理 |
| `crypto-algorithm-and-key-hierarchy-design.md` | 第一份专项 | 消费方适配依赖加密方案定义的原始保护边界 |
| `controlled-read-handle-design.md` | 第二份专项 | 消费方适配依赖读取句柄获取受控访问能力 |
| `plaintext-export-package-design.md` | 第三份专项 | 消费方适配与导出包共享"明文临时访问"的治理边界 |
| `authorization-scope-expression-design.md` | 第四份专项 | 消费方适配的授权依赖范围表达式定义的授权边界 |
| `desensitization-and-secondary-storage-design.md` | 第五份专项 | 搜索/AI 消费方适配依赖脱敏策略与二次存储治理 |

---

## 11. 本文档边界

### 11.1 本文保留的内容

作为消费方适配与压力测试的治理设计，本文保留以下内容：
- 签章/归档/搜索/AI 消费方适配的治理对象、稳定锚点、版本归属、最小治理单元
- 压力测试参数的治理对象、稳定锚点、版本归属、最小治理单元
- 各消费方压力测试参数的治理边界与责任划分
- 与前五份专项设计的引用关系
- 与 `contract-core`、`document-center` 的边界说明
- 签章/归档/搜索/AI 适配与压力测试的审计最小单元

### 11.2 下沉到实现层的内容

以下内容在后续实现层展开，不在本文设计：
- 消费方适配的具体实现细节（适配器代码、回调处理、服务挂接）
- 签章/归档/搜索/AI 服务的具体接口协议与调用时序
- 压力测试的具体脚本实现、工具配置、监控采集
- 测试数据准备、测试环境搭建、测试结果分析与报告
- 与各消费方模块的具体挂接接口与调用时序
- 消费方适配的执行、重试、降级、熔断的实现细节

### 11.3 排除内容

本文不包含以下内容：
- API 路径设计与请求/响应结构（已在 `api-design.md` 定义）
- DDL 与表结构详细设计（已在 `detailed-design.md` 定义）
- 代码实现与算法细节
- 实施排期与工时估算
- 运维手册与故障排查指南
- 具体适配代码、测试脚本代码、监控配置代码
