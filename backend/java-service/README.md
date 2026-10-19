# 后端 Maven 父工程

此目录是 Maven 父工程（`packaging=pom`）。根工程聚合 `codekin_common`、`codekin_module` 和 `codekin_gateway`；前两个聚合模块同样使用 `packaging=pom`，各自登记直接子模块。公共子模块继承 `codekin_common`，业务子模块继承 `codekin_module`，依赖版本管理最终继承根 POM。新增子模块只在对应聚合 POM 中登记。

## 版本管理

| 项目 | 版本来源 |
| --- | --- |
| Java | 26 |
| Spring Boot、Web MVC、Redis、JDBC、MySQL Connector/J、Jackson JSON | Spring Boot 4.1.1 父 POM |
| Spring Cloud | 2025.1.3 BOM |
| Spring Cloud Alibaba / Nacos | 2025.1.0.0 BOM；网关与用户服务按需引用 Config 和 Discovery Starter |
| MyBatis-Plus | 3.5.17 BOM，Boot 4 使用 `mybatis-plus-spring-boot4-starter` |
| Lombok | 1.18.48，父工程已配置 JDK 26 所需的注解处理器 |
| 阿里云号码认证异步 SDK | `alibabacloud-dypnsapi20170525` 2.0.0，由父 POM 统一管理 |
| 服务端滑块验证 | `tianai-captcha-springboot-starter` 1.5.5，由父 POM 统一管理；Web SDK 同步使用 1.5.5 |

父工程只管理版本，不把所有依赖强加给每个子模块。子模块按需声明依赖，通常不写版本号。例如 Web API 模块可选用：

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webmvc</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-json</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>
    <dependency>
        <groupId>com.baomidou</groupId>
        <artifactId>mybatis-plus-spring-boot4-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-j</artifactId>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

Spring Cloud 的具体 starter（如 OpenFeign、Gateway、Config）应在确定服务拆分和通信方式后由需要的子模块单独添加。MyBatis-Plus 已包含所需的 MyBatis 集成，不要再额外引入 `mybatis-spring-boot-starter`。分页等 SQL 解析功能需要时再加入 `mybatis-plus-jsqlparser`。

## 用户服务当前配置

`codekin_module/codekin_user` 已提供手机号/邮箱密码登录、手机与邮箱验证码登录、绑定或换绑手机号、当前用户资料、公开资料、密码设置、令牌刷新和退出登录接口。建表定义在仓库的 `deploy/deploy.sql`。服务引导配置仅包含 Nacos 地址、账号密码和 `spring.config.import`；业务配置副本位于 `deploy/nacos-config/`。MySQL、Redis、JWT 和网关签名密钥由该配置中的环境变量占位符解析。JWT 和网关签名密钥均为 Base64 编码，解码后至少 32 字节；网关与用户服务须使用同一组密钥。

本地运行变量保存在本目录 `.env`，网关与用户服务的运行配置均须加载它；Java 进程不会因为文件存在就自动读取。`CODEKIN_NACOS_DISCOVERY_IP=127.0.0.1` 指定同机服务注册到 Nacos 的地址，避免代理网卡被自动选中。这个地址只适用于网关和用户服务都在本机运行；跨主机或容器运行时须改为调用方可达的地址。

短信发送使用阿里云异步 SDK，从运行环境的 `ALIBABA_CLOUD_ACCESS_KEY_ID` 和 `ALIBABA_CLOUD_ACCESS_KEY_SECRET` 读取具有号码认证服务发送权限的凭据。客户端区域从 Nacos 的 `codekin.sms.region` 读取，默认 `cn-shenzhen`；当前 SDK 要求请求同时提供 `SignName` 和 `TemplateCode`，分别由 `CODEKIN_SMS_SIGN_NAME` 和 `CODEKIN_SMS_TEMPLATE_CODE` 配置。`TemplateParam` 传入服务端生成的验证码，验证码仍由用户模块在 Redis 中保存摘要并自行核验。用户模块负责生成验证码、限流、摘要存储及核验，策略在 Nacos 的 `codekin.user.sms` 下配置：默认手机号每日 50 次、客户端 IP 每日 100 次，均按北京时间自然日统计；登录和绑定分别配置 60 秒冷却及核验次数。所有用户服务实例必须使用相同的 `CODEKIN_SMS_CODE_HASH_SECRET`（至少 32 字节），由环境变量注入；缺少摘要密钥时服务启动失败。当前只接收中国大陆 11 位手机号，数据库保存 `+86` 前缀。

网关按 Nacos 中的 `codekin.gateway.client-ip.mode` 提取客户端 IP：`DIRECT` 使用 TCP 源地址；`TRUSTED_PROXY` 仅在连接方属于配置的可信代理 IP/CIDR 时解析 `X-Forwarded-For`。提取后的 IP 随身份头签名，业务服务验签后写入 `UserContextHolder`，短信限流从该上下文读取。代理模式的可信地址通过 `CODEKIN_GATEWAY_TRUSTED_PROXY_CIDRS` 指定；部署拓扑未确定时保持 `DIRECT`，不能直接信任请求自带的转发头。

| 接口 | 作用 |
| --- | --- |
| `GET /api/users/me` | 获取本人基础资料及绑定标识，性别、生日和真实姓名仅在本人接口返回。 |
| `PUT /api/users/me/profile` | 替换本人昵称、简介、性别、生日和真实姓名；选填字段传 `null` 清空，不能修改用户 ID、头像标识或认证字段。 |
| `POST /api/auth/captcha/slider/generate` | 用户服务的登录滑块图片生成接口，返回图片与随机 ID；按网关提供的客户端 IP 限制生成次数。 |
| `POST /api/auth/captcha/slider/check` | 用户服务的登录滑块核验接口；提交 `id` 与配套 SDK 的 `data` 轨迹，成功返回一次性 `captchaId`。 |
| `POST /api/auth/sms/login/send` | 发送登录验证码，请求体 `{"phone":"13800138000","captchaId":"滑块通过凭据"}`。 |
| `POST /api/auth/sms/login` | 校验验证码并返回 Access/Refresh 令牌对；未注册号码自动创建账号和默认资料，请求体含 `phone`、`code`。 |
| `POST /api/auth/email/login/send` | 发送邮箱登录验证码，请求体含 `email`、`captchaId`。 |
| `POST /api/auth/email/login` | 校验邮箱验证码并登录；未注册邮箱自动创建账号和默认资料，请求体含 `email`、`code`。 |
| `POST /api/auth/refresh` | 请求体含 `refreshToken`，轮换并返回新 Access/Refresh 令牌对。 |
| `PUT /api/users/me/password` | 携带 Access JWT；首次设置提交 `newPassword`，已有密码时还须提交 `oldPassword`。 |
| `POST /api/users/me/phone/sms/send` | 携带 Bearer JWT，发送绑定或换绑验证码。 |
| `POST /api/users/me/phone/bind` | 携带 Bearer JWT，验证新号码并绑定或换绑，请求体含 `phone`、`code`。 |
| `POST /api/users/me/email/code/send` | 携带 Bearer JWT，向新邮箱发送绑定或换绑验证码。 |
| `POST /api/users/me/email/bind` | 携带 Bearer JWT，验证新邮箱并绑定或换绑，请求体含 `email`、`code`。 |

登录和刷新响应包含 `accessToken`、`refreshToken`、`expiresAt`、`refreshExpiresAt` 和 `sessionExpiresAt`。Access JWT 有效期 30 分钟；Refresh Token 每次成功使用后轮换，并把空闲期限续到 7 天；会话最多存在 30 天，绝对到期时间不变。旧 Refresh Token 不能重复使用。退出登录撤销当前会话，设置或修改密码使该用户所有旧会话失效。前端应在 Access Token 到期前合并并发刷新请求；草稿自动保存属于答题业务，当前认证改动不包含它。

邮箱验证码由用户服务生成并在 Redis 保存 HMAC 摘要；公共邮件模块通过网易个人邮箱 SMTP 发送。SMTP 账号和授权码需在本机环境变量中填写，未填写时发送接口返回邮件服务不可用。邮箱和客户端 IP 的每日配额按北京时间自然日计。

匿名短信和邮箱登录发送接口先消费服务端滑块通过凭据，再执行原有发送冷却与日额度。缺失、过期、重复使用的凭据都会返回 `CAPTCHA_REQUIRED`。公共组件 `codekin_captcha` 使用 `RedisService` 和 `JsonUtils` 适配天爱缓存，答案与通过记录均保存在 Redis；前端 `CaptchaDialog.vue` 复用 Axios，在 SDK 请求后处理钩子中将 `R.code="0"` 转换为组件协议的数值 `200`。绑定手机号、邮箱的已登录接口暂不要求滑块凭据。配置与复用方式见 `codekin_common/codekin_captcha/README.md`。

所有业务响应均为 HTTP 200，`code` 为 `0` 表示成功，其他值表示业务错误。客户端和网关必须读取 `code`；HTTP 状态不能单独用于判断请求是否成功。账号密码散列采用带算法标识的格式（例如 `{bcrypt}` 前缀）。手机号验证码由服务端生成和核验，阿里云负责发送，Redis 保存短时 HMAC 摘要和限流计数。换绑只验证新号码；如果需要更强的账号保护，应另行设计重新认证、通知旧号码和会话撤销策略。

网关使用 Spring Cloud Gateway WebFlux，默认监听 `8080`，通过 Nacos 服务发现将 `/api/auth/**` 与用户资料请求转发给 `codekin-user`，将 `/api/users/me/resumes` 及其子路径优先转发给 `codekin-resume`。网关校验 JWT 和 Redis 会话，并签发绑定请求方法、路径、用户身份及已解析客户端 IP 的短时身份头；公开接口同样由网关签发匿名身份头。业务服务通过 `codekin_webmvc` 验签、防重放并把身份及 IP 放入 `UserContextHolder`。网关和业务服务都必须配置相同的 `CODEKIN_GATEWAY_CONTEXT_SECRET_BASE64`（Base64 解码后至少 32 字节），并保持公开接口规则一致。业务服务应仅在受控网络中开放，避免绕过网关造成无意义的拒绝请求。

Spring MVC 业务服务通过依赖 `codekin_common/codekin_webmvc` 自动获得统一异常处理器。`R<T>`、`Recode`、`BusinessException` 留在 `codekin_model`，不引入 Web 框架依赖；响应式网关使用自己的错误响应适配器，返回 HTTP 200 与业务错误码。

## 内容服务基础骨架

`codekin_module/codekin_content` 是内容服务的 Spring Boot Web MVC 基础模块，继承 `codekin_module` 并由其聚合构建。服务名为 `codekin-content`，默认 HTTP 端口为 `8084`，通过 `CODEKIN_CONTENT_PORT` 覆盖；`8082` 已用于 Nacos 控制台。

服务沿用用户、文件服务的 Nacos 引导配置，读取 `codekin-common.yml` 和 [`codekin-content.yml`](../../deploy/nacos-config/codekin-content.yml)，并注册到 Nacos。运行前需要将这两个 Data ID 发布到 `public` 命名空间、`CODEKIN` Group；现有 `nacos-init` 会同步配置目录中的全部 YAML 文件。Java 运行环境需要加载 Nacos 凭据、Redis 参数及与网关一致的 `CODEKIN_GATEWAY_CONTEXT_SECRET_BASE64`。

模块复用 `codekin_webmvc` 的统一异常处理和网关身份验签，并提供参数校验、Lombok、测试依赖及可执行 JAR 打包配置。目前尚无内容业务接口，因此未配置网关业务路由；数据库、MyBatis-Plus 和 Dubbo 在实际业务需要时再添加。

## 简历服务

`codekin_module/codekin_resume` 独立提供模板目录、简历创建/查询/保存/删除及 PDF 导出。启动类为 `CodekinResumeApplication`，服务名 `codekin-resume`，默认端口 `8085`。HTTP 路径仍为 `/api/users/me/resumes`；网关通过优先级更高的专用路由转发，用户资料仍由用户服务处理。

`resume_template`、`user_resume`、`user_resume_revision` 继续使用原数据库和表结构，由简历服务维护；迁移不需要重新初始化表或模板数据。保存保留 `expectedVersion` 并发校验、`mutationId` 重试语义，以及当前状态与显式快照的事务。证件照归属、用途、READY 状态及访问签名继续使用文件 Dubbo 接口，列表批量查询模板和照片。

简历服务只信任公共 WebMVC 验签后的 `UserContextHolder`，不读取用户认证表、不调用用户状态 RPC。网关负责 JWT、Redis 会话和用户级撤销校验。当前尚未提供账号停用入口；后续实现停用/注销时，必须调用 `JwtSessionService.revokeAll(userId)` 撤销已有会话。仅通过 SQL 修改账号状态不会撤销现有会话。

运行前发布 `codekin-common.yml`、[`codekin-resume.yml`](../../deploy/nacos-config/codekin-resume.yml) 和更新后的 `codekin-gateway.yml`，并加载 Nacos、MySQL、Redis 及与网关相同的签名密钥环境变量。配置导入不启用动态刷新，发布后重启相关服务。

PDF 使用 React 生成的渲染组件及字体。后端打包前先在 `front/codekin_web_react` 完成前端构建，简历 JAR 会包含 `resume-pdf` 资源和唯一的 `contracts/resume/*.json` 文档契约。Chromium 必须在简历进程运行账户下可用；可通过 `CODEKIN_RESUME_PDF_EXECUTABLE_PATH` 指定可执行文件，`CODEKIN_RESUME_PDF_ASSETS_DIRECTORY` 留空时使用 JAR 内资源。默认每实例并发 2、超时 30000 毫秒，可通过对应环境变量调整。

简历测试已随实现迁入本模块。普通测试之外，MySQL 集成测试仅允许指定名称包含 `codekin_resume_test_` 的测试库；真实 PDF 测试需显式启用 `resume.pdf.integration=true`，必要时指定 `resume.pdf.chromium`。迁移验收应执行并发保存、幂等重试、事务回滚、归属隔离、批量查询及真实 Chromium 导出测试。
