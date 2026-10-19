# Nacos 配置清单

命名空间使用 Nacos 默认的 `public`，Group 为 `CODEKIN`，类型为 `YAML`。文件名就是 Data ID：

| Data ID | 使用方 | 内容 |
| --- | --- | --- |
| `codekin-common.yml` | 网关、用户服务、文件服务、内容服务、简历服务 | Redis、JWT、网关签名、公有接口规则 |
| `codekin-gateway.yml` | 网关 | 端口、客户端 IP 提取策略、WebFlux 路由；通过 Nacos 发现 `codekin-user`、`codekin-file` 与 `codekin-resume` |
| `codekin-user.yml` | 用户服务 | 端口、MySQL、网易邮箱 SMTP、短信 SDK 区域及签名模板、邮箱和短信验证码策略、默认头像目录、滑块及一次性凭据配置、Dubbo 文件接口消费方 |
| `codekin-file.yml` | 文件服务 | MySQL、SeaweedFS S3 内外地址、桶和凭据、上传签名与访问期限、CORS 来源、临时文件清理、Dubbo 提供方 |
| `codekin-content.yml` | 内容服务 | HTTP 端口，默认 `8084`，可由 `CODEKIN_CONTENT_PORT` 覆盖 |
| `codekin-resume.yml` | 简历服务 | HTTP 端口、原 MySQL 数据源、文件 Dubbo 消费方、PDF 渲染资源/Chromium 路径及超时/并发参数 |

认证参数位于 `codekin-common.yml` 的 `codekin.security.jwt`：Access JWT 30 分钟、Refresh 空闲期 7 天、会话绝对上限 30 天。邮箱登录和 `/api/auth/refresh` 是公开路径，网关与用户服务需同时使用更新后的规则。邮件账号、SMTP 授权码及邮箱验证码摘要密钥分别由 `CODEKIN_MAIL_USERNAME`、`CODEKIN_MAIL_AUTH_CODE`、`CODEKIN_EMAIL_CODE_HASH_SECRET` 注入；未配置账号或授权码时邮件发送不可用。

用户登录滑块配置位于 `codekin-user.yml` 的顶层 `captcha`，其中 `captcha.secondary.enabled=true` 必须启用；图片和通过凭据有效期均为 120000 毫秒。该配置与 `codekin.captcha` 的生成限流配置是两个独立配置段。手动上传时更新 `public` 命名空间、`CODEKIN` Group 的 `codekin-user.yml`，发布后重启用户服务生效。

`nacos-init` 容器在每次 Compose 启动时登录 Nacos，并把本目录所有 `.yml` 文件发布到 `public` 命名空间、`CODEKIN` Group；同名 Data ID 会被仓库文件覆盖，包括在控制台手工修改的内容。发布失败时容器以非零状态退出。服务的 `application.yml` 只保留名称、Nacos 地址与账号密码及 `spring.config.import`；配置缺失时服务启动失败，不使用本地静默回退。各服务均从 Nacos 读取配置并在 Nacos 中注册。

配置模板中的 `${CODEKIN_...}` 会在**后端 Java 进程**运行时解析。`deploy/.env.example` 列出开发环境变量；Docker Compose 的环境变量不会自动注入直接在主机运行的 Java 进程。短信策略目前在启动时绑定，导入或修改配置后需要重启用户服务；共享鉴权规则修改后，网关及使用该规则的业务服务需要一起重启。Nacos 的客户端地址和登录凭据必须在本地引导文件中，因为读取远端配置之前就需要连接 Nacos。`CODEKIN_NACOS_DISCOVERY_IP` 用于指定各服务注册到 Nacos 的实例地址：同机运行设为 `127.0.0.1`，跨主机或容器运行时须改为其他服务可达的地址。

网关的 `codekin.gateway.client-ip.mode` 默认 `DIRECT`，只用 TCP 连接源 IP。前面有 Nginx 或负载均衡时设为 `TRUSTED_PROXY`，并通过 `CODEKIN_GATEWAY_TRUSTED_PROXY_CIDRS` 指定实际连接网关的代理 IP 或 CIDR，多个值以逗号分隔；例如单个本机代理可写 `127.0.0.1/32`。代理模式缺少可信地址会启动失败。网关只在 TCP 对端属于可信代理时，从 `X-Forwarded-For` 右侧向左寻找第一个非代理 IP，并将结果签名传给业务服务的请求上下文；直连或非可信对端提交的转发头不会影响 IP。代理必须追加真实连接源 IP，不能把未经处理的客户端头直接转发。该策略在启动时绑定，修改后需重启网关。

短信 SDK 区域、签名和模板编号放在 `codekin.sms` 下，分别使用 `CODEKIN_SMS_REGION`、`CODEKIN_SMS_SIGN_NAME` 和 `CODEKIN_SMS_TEMPLATE_CODE` 环境变量；当前 SDK 在发送前校验签名和模板编号。用户业务的 `code-hash-secret`、日限额及登录/绑定策略放在 `codekin.user.sms` 下；摘要密钥使用环境变量占位符，所有用户服务实例共用至少 32 字节。用户业务生成六位验证码，按网关签名的客户端 IP 和北京时间自然日执行限流，Redis 保存 HMAC 摘要并完成核验；公共短信组件只调用阿里云发送，不调用云端验证码核验接口。登录和绑定使用固定的独立 Redis 键空间。缺少摘要密钥时服务启动检查失败。


文件服务从 `codekin-common.yml` 和 `codekin-file.yml` 读取配置。浏览器可达的 S3 地址由 `CODEKIN_STORAGE_PUBLIC_ENDPOINT` 注入，不能在签名完成后替换域名；CORS 来源通过 `CODEKIN_STORAGE_ALLOWED_ORIGINS` 配置。文件服务初始化存储桶时设置对应跨域规则。`AVATAR` 和 `RESUME_PHOTO` 的大小、格式、像素与缩放策略位于共享 `UploadType` 枚举，前端通过限制接口读取，不在 Nacos 中维护第二份业务白名单。有效期和基础设施地址放在 Nacos 中。简历证件照复用原有 `/api/files/upload/signature` 和确认接口，不向公开头像读取接口开放其对象。

本地 CORS 默认允许 `http://127.0.0.1:5173`、`http://localhost:5173`、`http://127.0.0.1:5174`、`http://localhost:5174`，覆盖原前端和 React 前端。浏览器来源包含端口，开发服务器换端口时需要同步该配置；生产环境应通过环境变量覆盖为实际前端来源。已有环境变量会覆盖 Nacos 占位符的默认值，因此本地 `.env` 也需要保持一致。文件服务仅在存储初始化时写入桶规则，修改 Nacos 后需在加载新环境变量的情况下重启文件服务，或通过认证 S3 SDK 更新现有桶规则；仅修改网关 CORS 无法影响直传存储的响应。验证应检查实际签名 POST 的响应头，不能只依据 OPTIONS 预检成功判断上传正常。

用户服务与文件服务的 Dubbo 接口注册在 Nacos 的 `CODEKIN_DUBBO` 分组，与 Spring Cloud 服务发现及配置 `CODEKIN` 分组区分；接口分组为 `codekin`，契约版本为 `2.0`（统一 `RpcResult<T>`，与旧版原始 DTO 返回隔离）。文件提供方使用 `CODEKIN_DUBBO_FILE_TOKEN`；该内部调用 token 须独立随机生成，不能用用户 JWT 或开发密码替代。两端引入共享 `codekin_dubbo_api` 契约。`CODEKIN_DUBBO_HOST=127.0.0.1` 仅适用于本机部署，容器或多机运行时同样必须设置成消费方可达的地址。


## 默认头像

`codekin-user.yml` 的 `codekin.user.avatar.defaults` 维护预置头像的名称和稳定对象路径，首期已配置 12 项。路径为桶内的 `uploads/avatar/default/*.png`，不能填写 `http://seaweedfs:8333/...` 或短期签名 URL。资源须事先上传到 `CODEKIN_STORAGE_BUCKET` 对应桶，配置不自动上传图片。路径非空、无重复且限于固定目录；缺少有效目录会使用户服务启动校验失败。

用户服务获取列表时通过文件 RPC 一次批量签名，采用 `CODEKIN_STORAGE_PUBLIC_ENDPOINT`（本地 `http://127.0.0.1:8333`）和文件服务访问有效期。数据库保存稳定对象路径；仅选择当前配置列表中的资源才可设置默认头像。首次手机/邮箱验证码注册随机选择一项，现有用户不会批量修改。

发布更新后的 `codekin-user.yml` 后，先重启含新 RPC 方法的文件服务，再重启用户服务。当前头像目录在启动时绑定，发布 Nacos 后不能只刷新前端页面。无需新增表或修改存储桶匿名读策略。详细接口见 [`默认头像与选择弹窗`](../../dosc/5.%20默认头像与选择弹窗-2026-10-04.md)。

## 简历服务

`codekin-resume` 接管 `/api/users/me/resumes` 及其子路径，网关专用路由 `order=-10` 优先于 `/api/users/**` 用户资料路由，路径不重写。更新配置后先启动简历服务，再切换并重启网关。简历服务使用原三张简历表，不重新导入模板或迁移现有数据；文件核验继续调用已有 `IFileApi`。账号身份来自网关签名上下文，不额外查询用户状态；停用账号必须由用户服务同步撤销其全部 Redis 会话。
