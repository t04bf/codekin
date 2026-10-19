# codekin_captcha

公共滑块验证组件，封装天爱 1.5.5 的图片生成、轨迹匹配与二次验证。验证码状态统一通过项目 `RedisService` 存取，JSON 统一使用 `JsonUtils`；第三方组件内部缓存协议由 `CaptchaCacheStore` 适配。

## 接入其他业务服务

声明 `org.ninestick.codekin.captcha:codekin_captcha:${project.version}` 依赖后自动装配 `CaptchaService`、图片资源和 Redis 缓存适配。HTTP 接口由各业务模块按用途定义，并调用公共服务的 `generate`、`verify` 和 `consume`。用户登录接口位于用户服务的 `user.controller.UserLoginCaptchaController`；响应式网关负责路由、匿名身份签名和公开路径配置。

各服务需使用相同的 Redis、验证码缓存前缀与有效期。启用二次验证属于必需配置，缺失时启动失败，防止业务误以为已具备一次性校验：

```yaml
codekin:
  captcha:
    generate-limit: 30
    generate-window: 1m
captcha:
  prefix: codekin:captcha:image
  init-default-resource: true
  local-cache-enabled: false
  expire:
    default: 120000
    SLIDER: 120000
  secondary:
    enabled: true
    expire: 120000
    key-prefix: codekin:captcha:verified
```

天爱 `expire` 与 `secondary.expire` 使用毫秒。部署副本位于 `deploy/nacos-config/codekin-user.yml`；业务服务与网关须同步加载 `codekin-common.yml` 的公开路径规则。

## 用户登录请求链路

以下接口由 `codekin_user` 定义，公共组件只提供底层生成、核验和一次性消费能力。

1. `POST /api/auth/captcha/slider/generate` 生成图片，返回统一 `R`，`data` 为官方 SDK 的图片协议；正确答案不返回客户端。
2. `POST /api/auth/captcha/slider/check` 提交 `{id,data}`，后端匹配轨迹。成功返回 `R.data.captchaId`，组件在 Redis 写入短时通过记录。
3. 业务请求携带该 `captchaId`，调用 `CaptchaService.consume(captchaId)` 后继续执行。Redis 原子读取并删除通过记录，同一凭据并发只能消费一次。

图片状态匹配时也会消费，失败后前端重新生成图片。业务操作失败后，已消费的凭据不恢复，用户需要重新验证；短信和邮件原有的额度回滚语义仍由发送限流组件管理。

组件内置凭据不绑定手机号、邮箱或业务类型，是一个通用的一次性放行凭据。它不代替短信/邮件验证码核验、登录鉴权或业务发送限额。默认背景使用依赖自带图片，后续可通过自定义 `ResourceStore` 提供本地背景图片池，默认模板仍由天爱加载。

前端对应 `CaptchaDialog.vue` 与本地 `public/tac/` SDK；所有请求复用项目 Axios，SDK 的请求后处理钩子将统一业务响应转换成其预期协议。不要修改后端 `R<T>` 的成功码来迁就组件。

## 验证

`UserServiceImplCaptchaTest` 覆盖缺失/非法凭据不会投递短信或邮件。`CaptchaRedisIntegrationTest` 由 `CODEKIN_TEST_REDIS=true` 显式启用，使用 `CODEKIN_TEST_REDIS_HOST/PORT/USERNAME/PASSWORD` 连接测试 Redis，验证 Boot 4 自动装配、图片生成、JSON 数字协议、TTL 与并发一次性消费，只操作随机隔离的测试键并清理。
