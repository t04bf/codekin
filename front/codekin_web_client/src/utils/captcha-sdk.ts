/** 官方 TAC 1.5.5 SDK 的协议边界，服务端仍返回统一 R<T>。 */
export interface CaptchaSdkResponse {
  code: string | number
  message?: string
  msg?: string
  data?: { captchaId?: string }
}

export interface CaptchaSdkRequest {
  url: string
  method: string
  headers: Record<string, string>
  data: unknown
}

export interface CaptchaSdkInstance {
  config: {
    doSendRequest(request: CaptchaSdkRequest): Promise<CaptchaSdkResponse>
    addRequestChain(chain: {
      postRequest(type: string, request: CaptchaSdkRequest, response: CaptchaSdkResponse): boolean
    }): void
  }
  init(): CaptchaSdkInstance
  reloadCaptcha(): void
  destroyWindow(): void
}

declare global {
  interface Window {
    TAC?: new (config: Record<string, unknown>, style?: Record<string, unknown>) => CaptchaSdkInstance
  }
}

let sdkLoading: Promise<void> | undefined

/** 延迟加载仓库内的 SDK 静态资源，同一页面只加载一次。 */
export function loadCaptchaSdk(): Promise<void> {
  if (window.TAC) return Promise.resolve()
  if (sdkLoading) return sdkLoading
  sdkLoading = new Promise<void>((resolve, reject) => {
    const assets = `${import.meta.env.BASE_URL}tac/`
    if (!document.getElementById('codekin-captcha-styles')) {
      const link = document.createElement('link')
      link.id = 'codekin-captcha-styles'
      link.rel = 'stylesheet'
      link.href = `${assets}css/tac.css`
      document.head.append(link)
    }
    const script = document.createElement('script')
    script.src = `${assets}js/tac.min.js`
    script.onload = () => window.TAC ? resolve() : reject(new Error('安全验证组件加载失败'))
    script.onerror = () => reject(new Error('安全验证组件加载失败，请稍后重试'))
    document.head.append(script)
  }).catch((error: unknown) => {
    sdkLoading = undefined
    throw error
  })
  return sdkLoading
}
