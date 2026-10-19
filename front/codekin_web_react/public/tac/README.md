# TAC Web SDK 1.5.5

官方发布包：https://gitee.com/dromara/tianai-captcha/releases/download/tianai-captcha-1.5.5/tac-web.zip

源码：https://github.com/dromara/tianai-captcha/tree/master/tianai-captcha-web-sdk

原始 JS、CSS 与图片保留在此目录。项目通过 `CaptchaDialog.vue` 封装公开请求方法和请求后处理钩子，复用 Axios，并将统一 `R<T>` 的状态码适配为 SDK 所需的数值状态码。升级后端依赖时同步升级 Web SDK，并重新验证协议。
