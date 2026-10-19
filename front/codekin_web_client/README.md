# Codekin Web Client

前端工程位于 `front/codekin_web_client`，使用 Vue 3、TypeScript 和 Vite。Vue Router 负责页面路由，Pinia 管理登录态，Axios 访问后端，VueUse 持久化一次性 Refresh Token，Element Plus 提供布局、表单、弹窗和提示组件，Arco Design 的 List 提供资料分组列表，Lucide 提供统一线性图标。

## 页面与登录

`/login` 默认显示账号密码登录，可切换邮箱验证码和手机验证码登录。验证码首次登录由后端自动注册账号。登录成功后进入 `/profile`（兼容旧 `/workspace` 地址），读取 `GET /api/users/me`；退出时请求 `POST /api/auth/logout`。

个人主页采用左侧资料与右侧学习内容布局，显示真实昵称、简介和用户 ID；练习统计、成就及记录为明确标注的占位。`/settings/profile` 管理现有基础资料，通过 `PUT /api/users/me/profile` 保存昵称、简介、性别、生日和真实姓名，选填字段传 `null` 可清空。性别编码为 `MALE`、`FEMALE`、`OTHER`，生日不能晚于今天；性别、生日及真实姓名仅本人可见。当前 `avatarKey` 保存稳定对象路径，`avatarUrl` 为文件服务生成的短期签名访问链接。公共 `UserAvatar.vue` 使用 Element Plus 头像组件保留细边框；未设置或图片读取失败时回退到 `design/codekin/brand/mascot-avatar.png` 的前端资源副本。顶部点击头像打开 Element Plus 下拉菜单，提供个人主页、个人资料和退出登录入口，移动端同样保留头像入口。资料状态只驻留内存，退出时清除。

点击获取手机或邮箱验证码时，`CaptchaDialog.vue` 弹出天爱滑块，延迟加载本地 `public/tac/` 1.5.5 SDK。图片与轨迹由后端 `codekin_captcha` 生成和核验；成功返回的 `captchaId` 随发送请求提交，服务端原子消费后才执行发送限额和投递。SDK 请求复用 Axios，并通过请求后处理钩子适配统一 `R<T>`，不将浏览器的成功回调当作发送授权。后端需要载入最新 Nacos 用户服务配置和公共公开路径配置。

登录页使用图形 Logo，并提供本站 `/terms` 用户协议与 `/privacy` 隐私政策入口。两份页面目前标为开发阶段草案，内容按当前已实现的账号、资料与验证码服务编写。正式对外开放前须补齐运营主体法定名称、部署与保存期限，以及账号注销的实际办理流程。

前端按后端统一返回 `R<T>` 的 `code` 判断成功，不能只看 HTTP 200。Access Token 放在内存；Refresh Token 使用 `localStorage`，页面恢复时静默轮换。剩余不足五分钟时，多个并发请求共用一次刷新 Promise。密码和验证码不持久化。当前后端以 JSON 返回 Refresh Token，因而存在浏览器脚本泄漏风险；正式对外部署前宜把 Refresh Token 改为由服务端设置的 HttpOnly Cookie，并配套 CSRF 保护。

资料页的手机号和邮箱支持绑定、换绑。`ContactBindDialog.vue` 共用表单，通过登录态 Axios 调用 `/api/users/me/phone/sms/send`、`/api/users/me/phone/bind`、`/api/users/me/email/code/send` 和 `/api/users/me/email/bind`；验证码发送成功后按目标地址显示 60 秒倒计时，实际额度和冷却以服务端为准。已登录的绑定接口沿用现有规则，无需登录滑块凭据；绑定成功后重新查询本人资料。现有联系方式只以脱敏形式展示，手机号如 `158****1751`，邮箱如 `nin****@163.com`；完整联系方式仅在填写新绑定目标时展示，展示脱敏不会改变后端本人资料接口的返回契约。

账号与安全中的密码行调用 `PUT /api/users/me/password`。`GET /api/users/me` 的 `hasPassword` 判断首次设置或修改：首次只填写新密码与确认密码，已设置时须额外输入旧密码。新密码满足后端的 8 至 72 字符及最多 72 个 UTF-8 字节限制。保存后后端撤销所有旧会话，前端清除登录状态并返回登录页。`hasPassword` 是个人信息接口必填布尔值；字段缺失时按资料加载失败处理并提供重试，不能当作未设置密码或展示设置密码入口。

## 头像选择

资料页点击头像打开 `AvatarUpload.vue` 的 Element Plus 居中弹窗，展示 `GET /api/users/avatar/defaults` 返回的默认头像。桌面每行六个，小屏每行四个；当前选择有边框和 `aria-pressed` 标记。选择后点击“确认”，通过 `PUT /api/users/me/avatar/default` 保存 `objectKey`，取消不写资料；不会持久化签名 URL。

弹窗保留“上传头像”入口，格式和大小提示来自 `GET /api/files/upload-types`。选择本地图片先预览，确认后才依次申请签名、直传、确认图片、调用 `PUT /api/users/me/avatar` 保存正式文件，取消本地选择不会创建存储对象。默认头像与上传互斥，失败通过 ElMessage 提示并保留重试能力。预览对象 URL 在切换、关闭和组件卸载时释放。

## 环境和代理

- `.env.development`：`DEV_API_TARGET=http://127.0.0.1:8080`，Vite 将浏览器同源请求 `/api/**` 代理到本地网关，无需在网关开放浏览器跨域访问。
- `.env.production`：`VITE_API_BASE_URL=/`，浏览器仍请求同源 `/api/**`；部署网站的反向代理须把该路径转发至 Gateway，并给 Vue Router 路由配置 `index.html` 回退。
- `.env.example`：环境变量模板。`VITE_*` 会进入浏览器构建产物，禁止填写凭据或密钥；网关目标 `DEV_API_TARGET` 只在 Vite 开发服务器使用。

反向代理配置示意（生产环境的 TLS、主机名及 Gateway 地址按部署调整）：

```nginx
location /api/ {
    proxy_pass http://127.0.0.1:8080;
    proxy_set_header Host $host;
}
location / {
    try_files $uri $uri/ /index.html;
}
```

本地 Node.js 依赖由 `package-lock.json` 固定。进入本目录后运行 `npm ci` 安装，`npm run dev` 启动开发服务器，`npm run build` 生成生产文件。后端 Gateway 默认监听 `127.0.0.1:8080`，它需要同时连接已启动的用户服务、MySQL、Redis 和 Nacos。

右侧登录插画使用用户提供的原图，存放于 `src/assets/login-offer.png`；品牌图标来自 `design/codekin/brand/logo-icon.png`。全局浅色变量、Element Plus 品牌色及基础重置集中在 `src/style.css`。页面布局和控件使用第三方组件，Element Plus 完整 CSS 在 `main.ts` 加载，并通过 `ElConfigProvider` 启用中文；Arco List 按需加载其样式。个人资料保留分组列表与点击整行弹窗编辑的交互。
