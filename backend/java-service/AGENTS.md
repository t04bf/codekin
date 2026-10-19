# 后端工程开发要求

本文件适用于 `backend/java-service/` 及其所有子模块。开始改动前，先查看相关模块的 `pom.xml`、现有包结构和调用链；以下路径是新代码的放置约定，不代表这些业务组件目前已经实现。

## 1. 模块与代码位置

| 模块 | 放置内容 |
| --- | --- |
| `codekin_module/codekin_file` | 文件用途限制、直传凭证、文件记录、实际图片校验、对象存储适配及文件 Dubbo 提供方。前端不能指定对象 key，业务关联前由文件服务核验归属、用途及 READY 状态。 |
| `codekin_module/codekin_user` | 用户业务的接口、应用服务、领域模型、持久化代码和该业务专用配置。其他业务模块按同样方式独立组织。 |
| `codekin_module/codekin_resume` | 简历模板、当前文档、版本快照和 PDF 导出；只使用已验签的用户上下文，文件归属核验及签名通过文件 RPC，不直接读取用户认证表。 |
| `codekin_common/codekin_model` | 确实需要跨模块共享的 DTO、VO、枚举和稳定的数据契约；按业务域分包。不要把某个业务模块的数据库实体搬到这里。 |
| `codekin_common/codekin_dubbo` | Dubbo 公共运行时、SPI 过滤器与 RPC 全局异常转换；业务模块统一引用，契约模块不承载基础设施实现。 |
| `codekin_common/codekin_dubbo_api` | 跨服务调用的 Dubbo 接口定义。接口使用的共享数据类型放在 `codekin_model`，避免在接口模块复制模型。 |
| `codekin_common/codekin_redis` | 可复用的 Redis 配置、序列化和基础设施组件。业务专属的键规则、缓存策略及服务放在所属业务模块。 |
| `codekin_common/codekin_security` | JWT 与 Redis 会话、网关签名身份头、共享公开路径规则和用户请求上下文；网关与业务服务共同引用。 |
| `codekin_common/codekin_sms` | 阿里云短信发送适配器和厂商基础配置；业务场景、限流、验证码生成与核验留在所属业务模块。 |
| `codekin_common/codekin_email` | 邮件 SMTP 投递适配器与发件人配置；邮箱验证码生成、限流和核验留在所属业务模块。 |
| `codekin_common/codekin_captcha` | 可复用的服务端滑块生成、轨迹校验、Redis 缓存适配和一次性凭据消费；HTTP 接口放在所属业务模块，公共组件不定义或自动注册 Controller。业务模块在受保护操作之前消费凭据。 |
| `codekin_common/codekin_utils` | 与具体业务无关、已有库不能合理覆盖的通用工具。不要把业务逻辑放入工具模块。 |
| `codekin_common/codekin_webmvc` | 供 Spring MVC 业务服务复用的 HTTP 异常处理和网关身份拦截器；不要放入共享数据模型或响应式网关。 |
| `codekin_gateway` | Spring Cloud Gateway WebFlux 路由与响应式过滤器；同步 Redis 会话校验须切换到 `boundedElastic`。不要在网关实现用户等业务逻辑或直接访问业务数据库。 |
| `deploy/nacos-config` | Nacos 配置的仓库副本；按 Data ID 管理公共配置与服务配置。地址、密码和密钥只保留环境变量占位符；应用本地文件仅保留连接 Nacos 所需的引导配置。 |
| 父工程 `pom.xml` | 统一管理依赖和插件版本；子模块按需声明依赖。 |

`codekin_common` 与 `codekin_module` 均为 `packaging=pom` 的聚合父模块，分别登记各自的直接子模块；根工程只登记这两个聚合模块及网关。新增公共模块继承 `codekin_common`，业务模块继承 `codekin_module`，两者继续继承根 POM 的版本管理，避免根工程重复列出孙级模块。

Java 包名以 `org.ninestick.codekin` 为根。以用户模块为例：

| 组件 | 建议包位置 |
| --- | --- |
| HTTP 接口 | `org.ninestick.codekin.user.controller` |
| 业务服务接口与实现 | `org.ninestick.codekin.user.service`、`org.ninestick.codekin.user.service.impl` |
| MyBatis-Plus Mapper | `org.ninestick.codekin.user.mapper` |
| 数据库实体 | `org.ninestick.codekin.user.domain.entity` |
| 模块内 DTO、VO、枚举 | `org.ninestick.codekin.user.domain.dto`、`domain.vo`、`domain.enums` |
| 模块专用配置、Dubbo 实现 | `org.ninestick.codekin.user.config`、`org.ninestick.codekin.user.rpc` |

在上述类型包内再按业务细分，例如 `org.ninestick.codekin.user.domain.entity.info`、`org.ninestick.codekin.user.domain.entity.resume`、`org.ninestick.codekin.user.domain.vo.resume`。同一模块内保持这一层级一致，避免所有类堆在同一个包。

## 2. 模型边界

- `entity` 表示持久化实体，只在所属业务模块定义，不直接作为 HTTP 或 Dubbo 的入参、出参。
- `dto` 用于接口请求、服务间传输或应用层数据传递；`vo` 用于面向调用方的展示/响应。不要用一个类兼任数据库实体、请求体和响应体。
- 只有在其他模块确实需要共享、并且语义稳定时，才将 DTO、VO 或枚举放入 `codekin_model`。按业务分类，例如 `org.ninestick.codekin.model.user.info.dto`、`org.ninestick.codekin.model.user.resume.vo`、`org.ninestick.codekin.model.user.enums`。避免把业务模块的全部模型提前放入公共模块。
- Dubbo 接口放在 `codekin_dubbo_api`；接口引用的共享模型放在 `codekin_model`。所有业务 RPC 方法统一返回 `RpcResult<T>`，无业务数据时使用 `RpcResult<Void>`，禁止直接返回 DTO、集合、基础类型或 `void`。消费方必须先检查 `isSuccess()`，再使用 `data`；失败结果不能当作空查询结果。修改共享契约时检查所有调用方的兼容性，不兼容返回结构需升级 Dubbo 契约版本并同步提供方与消费方。

## 3. 数据库访问

- 严禁数据库 N+1：不要在循环、流操作或逐条转换中按记录访问数据库。优先一次查询所需数据，按 ID 批量查询后在内存中关联；必要时使用合适的联表查询。提交前检查列表接口及其嵌套数据的查询次数。
- 如果确有无法消除 N+1 的场景，先说明具体场景、数据规模、查询次数和替代方案，并与用户讨论；讨论前不要直接实现例外。
- 简单单表 CRUD 和条件查询优先使用 MyBatis-Plus 的 `BaseMapper`、`LambdaQueryWrapper`、`Wrappers.lambdaQuery` 等现有能力。避免为简单查询手写 XML SQL，也不要重复引入 MyBatis Starter。
- 聚合、多表关联或现有 API 难以清晰表达的复杂查询，可以使用 XML/手写 SQL；写明选择原因，明确列、条件和分页边界，并检查索引与执行成本。不要为回避 XML 而拆成 N+1 查询。
- 批量写入、分页查询和事务边界按实际数据量与业务一致性设计，不凭空假设数据库规模或性能收益。

## 4. 判断与沟通

- 接任务先检查错误前提、逻辑跳跃和缺失信息。区分已核实事实、预测和主观判断；涉及版本、数字、人物和重要结论时尽量查可靠来源。
- 当前处于开发阶段。没有已上线接口或实际旧数据约束时，直接完善当前协议与实现，不保留废弃草案、不预设旧版兼容、不因开发中调整默认新增第二套协议或模板种子。简历并发保存版本仍须保留。
- 不迎合明显错误的要求；直接指出依据、风险、替代解释，以及容易遗漏的变量、成本和偏差。
- 发现影响继续编码的约束、冲突或关键信息缺口时，先停下来说明并讨论，不自行扩大设计或擅自迁移数据、修改架构。
- 汇报工作时说明改了什么、为什么、如何验证及仍有限制；不要把一串待执行命令当作交付结果。

## 5. Java 代码风格

- 新增或修改的类、字段及关键业务逻辑应有清晰注释；类的 Javadoc 必须包含 `@author ninestick`。注释解释用途、约束或原因，避免重复代码字面含义。
- 不使用 Java `record`，请求、响应、结果及内部数据类型均使用普通 `class`。包含密码等敏感信息的类不要自动生成会输出字段内容的 `toString()`。
- 避免为 `normalizeXXX`、`trimToNull` 等细碎操作反复创建小方法。优先复用项目已有封装、JDK 或已引入的成熟库（如适用的 `StringUtils`）。
- 复用前确认具体模块已声明相应依赖和方法语义；不要为了一个简单操作盲目增加库。只有复杂度、复用性或业务语义确实需要时再抽取方法。
- Redis 访问统一使用已有的 `RedisService`；该类只保留读写、计数、TTL、原子比较、脚本执行等通用能力，不放短信、邮箱或 JWT 会话等场景逻辑。场景的 Lua、键规则和结果处理放在所属模块的专用组件中。短信与邮箱发送限流共用 `user.verification.VerificationCodeRateLimiter`，通过策略声明额度和错误码，业务层不重复解析脚本返回码或直接注入 Redis 模板。
- 业务接口统一返回公共模型中的 `R<T>`；业务错误码实现 `Recode`，可由公共 `BusinessException` 携带默认消息，或使用 `new BusinessException(recode, "资源 {} 不存在", id)` 定义参数化消息。Spring MVC 服务依赖 `codekin_webmvc` 后自动装配全局异常处理器，保留 `Recode` 的错误码并返回格式化后的消息。当前接口约定使用 HTTP 200，由 `R.code` 表示业务结果。
- 日志统一使用 SLF4J，不记录密码、完整登录标识、JWT、密钥等敏感信息。
- Spring 容器管理的 Controller、Service 等业务组件使用 `@Resource` 在字段上注入依赖，不手写仅用于注入的构造器；普通数据对象以及通过 `@Bean` 显式创建且需要不可变状态的基础设施类不适用此规则。
- 业务 Service 采用接口与实现分离：接口放在 `service` 包，按 `IUserService` 这类 `I` 前缀命名；实现放在 `service.impl` 包，按 `UserServiceImpl` 命名并标注 `@Service`。Controller 依赖接口，不直接依赖实现类。基础设施组件不为满足命名约定而空造接口。
- 业务状态、类型等持久化编码不得在业务逻辑或数据库查询条件中直接与数字、字符串字面量比较（例如 `auth.getStatus() != 1`）。在所属业务模块定义语义明确的枚举，仅在枚举定义中维护编码；判断和 MyBatis-Plus 条件都引用枚举值。模块专属枚举不要提前放入 `codekin_model`。
- 跨模块复用的协议字面量（例如 Bearer 令牌类型及请求头前缀）放在所属公共模块的常量类中；使用常量时也不要保留对应的长度魔法数字。
- 网关通过 JWT 加 Redis 会话校验用户，按 Nacos 的直连或可信代理策略提取客户端 IP，并对用户 ID、会话 ID、该 IP、请求方法与目标签发短时 HMAC 身份头。代理模式必须限制可信代理 IP/CIDR，从 `X-Forwarded-For` 右侧向左排除可信代理；不得直接信任客户端提供的转发头。业务服务统一拦截器验签、防重放，再将身份和 IP 写入 `UserContextHolder`；需要当前用户 ID 或客户端 IP 时从该上下文读取，禁止在业务代码中重新解析 JWT 或自行读取转发头。公开接口也必须携带网关签发的匿名身份头。`ThreadLocal` 必须在请求完成后清理；线程池任务使用 TTL 包装并明确生命周期。
- 业务域的 Entity、DTO、VO 使用 Lombok `@Builder` 创建对象；业务代码避免长参数构造。需要 MyBatis 或 JSON 框架实例化的 Entity、请求 DTO 同时保留无参构造，不因使用 builder 破坏框架绑定。
- 普通可变配置对象优先用 Lombok `@Getter`、`@Setter`，避免重复手写访问方法；保留有业务语义的手写方法。

- 文件上传优先复用 S3 SDK 的 POST Policy 签名，不自行实现签名算法。枚举中的 MIME/大小限制仍须在确认时核验真实内容和像素数；正式文件保存为服务端生成的独立对象，不能复用仍可被上传凭证覆盖的临时 key。服务间文件核验通过 `codekin_dubbo_api` 契约，不跨模块直接访问文件表。

- RPC 服务引用 `codekin_dubbo` 以自动启用公共 SPI 异常处理。业务层仍可抛公共 `BusinessException`，提供方保留 Recode 及自定义 message 并转换为失败的 RpcResult；普通未知异常返回安全说明及异常类型，不输出原始堆栈、凭证或请求参数。消费方集群过滤器在最终调用失败时转换为 RpcResult，保留 Dubbo 原有重试行为。框架元数据接口不改写，JVM 致命 Error 不伪装为可恢复失败；代理初始化和调用链之外的失败由应用边界兜底。
- `codekin_dubbo` 在 Boot 启动阶段将 Dubbo 内部 JSON 实现默认指定为 Fastjson2，避免可选 JSON 扩展缺依赖导致注册失败。RPC 传输的 `dubbo.protocol.serialization` 与内部 JSON 选择是两个设置；不得为修复此问题盲目引入旧 Fastjson 1，或把启动前必须确定的 JVM 属性当作 Nacos 动态配置。
