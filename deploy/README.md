# 本地中间件部署文件

`compose.yml` 定义 MySQL、Redis、Nacos 3 和 SeaweedFS 单机服务。镜像版本固定为 MySQL 8.4.11、Redis 7.4.11、Nacos 3.2.4-slim、SeaweedFS 4.47。MySQL 的 `codekin` 用户与 `root` 用户、Redis 的 `default` 用户，以及 Nacos 的 `nacos` 管理员，开发环境密码统一为 `Wcb123123`，可由 `CODEKIN_DEV_PASSWORD` 统一覆盖。SeaweedFS 使用单独的 S3 和管理后台凭据变量，未设置时回退到该开发密码。MySQL 会在**空数据目录的首次初始化**时执行 `deploy.sql`。

本地 `deploy/.env` 指向 `backend/java-service/.env`，Compose 从该文件读取中间件初始化参数。该文件保存独立随机密钥并已限制文件权限；仓库只保留 `deploy/.env.example`。直接在主机或 IDE 中启动的 Java 服务不会自动读取 Compose 环境变量，运行配置须加载同一份 `.env`。

容器持久化数据都在仓库根目录 `.data/`：`mysql/`、`redis/`、`nacos/data/`、`nacos/logs/` 和 `seaweedfs/`。容器端口仅发布到主机 `127.0.0.1`：MySQL `3306`、Redis `6379`、Nacos API `8848`、gRPC `9848`、控制台 `8082`、SeaweedFS S3 `8333` 和管理后台 `23646`。Nacos 控制台使用独立的 `8082` 主机端口，避免与应用网关的 `8080` 冲突。

Nacos 3 首次启动不会预设管理员密码。`nacos-init` 一次性容器会通过初始化 API 设置同一个开发密码，重复运行只验证已有密码，然后把 `nacos-config/*.yml` 发布到 Nacos；再次执行 Compose 启动时会更新同名配置。Nacos 自身还要求独立的 Base64 token secret 与 server identity；它们不是登录密码。`CODEKIN_NACOS_AUTH_TOKEN`、`CODEKIN_NACOS_IDENTITY_KEY` 和 `CODEKIN_NACOS_IDENTITY_VALUE` 必须填入各自独立的值，`compose.yml` 在缺失时拒绝解析。

Nacos 的配置副本和同步规则见 [`nacos-config/README.md`](nacos-config/README.md)。保留 `.data/` 数据目录时，修改 Compose 密码环境变量不会修改数据库、Redis 或 Nacos 中已经初始化的凭据，需要另外做凭据轮换。此 Compose 仅用于本地开发；统一密码不适合对外环境。

## SeaweedFS 对象存储

采用项目提供的 [`weed mini` 单机模式](https://github.com/seaweedfs/seaweedfs/wiki/Quick-Start-with-weed-mini)，Master、Volume、Filer、S3 和管理后台在同一个容器运行。固定的 `chrislusf/seaweedfs:4.47` 镜像支持 Linux ARM64 和 AMD64，适配 Apple Silicon 开发机。关闭本阶段不用的 WebDAV、Iceberg、Lance 服务和匿名统计上报。

| 用途 | 本地地址 / 配置 | 默认凭据 |
| --- | --- | --- |
| S3 API | `http://127.0.0.1:8333` | Access Key `codekin`，Secret Key `Wcb123123` |
| 管理后台 | `http://127.0.0.1:23646` | 用户名 `codekin`，密码 `Wcb123123` |
| 默认存储桶 | `codekin` | `S3_BUCKET` 在启动时创建不存在的桶 |
| 持久化目录 | `.data/seaweedfs/` | 文件、元数据和管理后台状态都保存在该目录 |

S3 凭据使用 `CODEKIN_STORAGE_ACCESS_KEY`、`CODEKIN_STORAGE_SECRET_KEY`；管理后台使用 `CODEKIN_SEAWEEDFS_ADMIN_USER`、`CODEKIN_SEAWEEDFS_ADMIN_PASSWORD`。两组凭据用途不同，即使开发环境使用相同密码，也需要分别维护。现有 `.env` 显式填写的值优先于 Compose 中的回退值。

只向主机发布 S3 与有密码保护的管理后台端口；Filer、Master、Volume 和内部 gRPC 端口不发布到主机。未配置匿名访问策略，业务文件通过签名 S3 请求访问。健康检查同时检查 S3 `/readyz` 和管理后台 `/health`；通过检查不代表头像上传业务已接入。

文件服务通过 S3 SDK 的 path-style 寻址访问 SeaweedFS。`CODEKIN_STORAGE_ENDPOINT` 用于服务端读写，`CODEKIN_STORAGE_PUBLIC_ENDPOINT` 用于浏览器直传和头像访问签名；本地均为 `http://127.0.0.1:8333`。若 Java 服务迁入 Compose 网络，内部 endpoint 改为 `http://seaweedfs:8333`，浏览器地址仍须从浏览器所在机器可达。Secret Key 只供服务端使用，不放入前端环境变量；表单中的 Access Key ID 和短期签名属于 S3 协议字段。后端使用 MinIO Java SDK 的 S3 兼容接口，部署的存储服务仍是 SeaweedFS。

单机数据挂载支持容器重建后保留数据，但不能代替备份；需要备份完整的 `.data/seaweedfs/`，而非只复制对象文件。


## 文件服务与头像直传

业务模块 `backend/java-service/codekin_module/codekin_file` 在本机使用 HTTP `8083`、Dubbo `20883`；网关转发 `/api/files/**` 到 Nacos 中的 `codekin-file`，所有上传接口都要求登录。用户服务通过 `codekin_dubbo_api` 的 `IFileApi` 核验文件归属与确认状态，再保存头像，不能直接接受客户端提供的对象路径。

首次创建空数据库时 `deploy.sql` 已包含 `file_object` 表。对于保留现有数据的数据库，新增表脚本是 [`sql/20261004_file_object.sql`](sql/20261004_file_object.sql)，重启已有 MySQL 容器不会自动执行新的初始化 SQL。本次仅准备脚本，没有操作现有数据库。

上传接入还需要 Nacos 中的 `codekin-file.yml`、更新后的 `codekin-user.yml` 和 `codekin-gateway.yml`；配置副本已维护在 `nacos-config/`。文件服务和用户服务使用同一份环境变量中的 `CODEKIN_DUBBO_FILE_TOKEN`，通过有账号密码的 Nacos 注册中心发现 Dubbo 接口。`CODEKIN_DUBBO_HOST` 本地设为 `127.0.0.1`，避免自动选取代理或 VPN 网卡。完整接口与限制见 [`头像直传接入说明`](../dosc/3.%20头像直传与文件服务-2026-10-04.md)。本次没有启动中间件、上传 Nacos 配置或执行数据库迁移。

## 简历服务

简历维护和 PDF 导出由 `codekin-resume` 提供，HTTP 默认 `8085`，网关保留 `/api/users/me/resumes` 及子路径并优先转发到该服务。启动前发布 `nacos-config/codekin-resume.yml` 和更新后的网关配置；Java 进程加载与其他服务相同的 Nacos、MySQL、Redis 和网关签名环境配置。

原简历表及数据保持不变，本次迁移无需执行 SQL。PDF 渲染资源和字体由 React 构建并打入简历 JAR；运行账户需可启动 Chromium，可用 `CODEKIN_RESUME_PDF_EXECUTABLE_PATH` 指定路径。详细配置与验证方式见后端 README。
