import { useEffect, useId, useRef, useState } from 'react'
import { toast } from 'sonner'
import { Dialog, DialogContent, DialogTitle, DialogDescription } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { publicHttp } from '@/api/http'
import { loadCaptchaSdk, type CaptchaSdkInstance, type CaptchaSdkResponse } from '@/utils/captcha-sdk'
import { errorMessage } from '@/lib/errors'

export function CaptchaDialog({ open, onOpenChange, onVerified }: {
  open: boolean; onOpenChange: (open: boolean) => void; onVerified: (id: string) => void
}) {
  const elementId = `codekin-captcha-${useId().replaceAll(':', '')}`
  const handlers = useRef({ onOpenChange, onVerified })
  handlers.current = { onOpenChange, onVerified }
  const [attempt, setAttempt] = useState(0)
  const [failed, setFailed] = useState(false)
  const [loading, setLoading] = useState(false)
  useEffect(() => {
    if (!open) return
    let disposed = false
    let instance: CaptchaSdkInstance | undefined
    const fail = (message: string) => {
      if (disposed) return
      setFailed(true)
      toast.error(message, { id: 'captcha-error' })
    }
    setLoading(true)
    setFailed(false)
    void (async () => {
      try {
        await loadCaptchaSdk()
        if (disposed || !window.TAC) return
        const current = new window.TAC({
          bindEl: `#${elementId}`,
          requestCaptchaDataUrl: '/api/auth/captcha/slider/generate',
          validCaptchaUrl: '/api/auth/captcha/slider/check',
          validSuccess(response: CaptchaSdkResponse) {
            if (disposed || instance !== current) return
            const captchaId = response.data?.captchaId
            if (!captchaId) { fail('验证响应异常，请重新验证'); current.reloadCaptcha(); return }
            handlers.current.onOpenChange(false)
            handlers.current.onVerified(captchaId)
          },
          validFail(response: CaptchaSdkResponse) {
            if (disposed) return
            fail(response.message || '验证未通过，请重试')
            current.reloadCaptcha()
          },
          btnCloseFun: () => handlers.current.onOpenChange(false),
          btnRefreshFun: () => { setFailed(false); current.reloadCaptcha() },
        }, { logoUrl: null, moveTrackMaskBgColor: '#dcebdc', moveTrackMaskBorderColor: '#508568' })
        instance = current
        current.config.doSendRequest = async request => {
          try { return (await publicHttp.request<CaptchaSdkResponse>(request)).data }
          catch { return { code: 'CAPTCHA_NETWORK_ERROR', message: '安全验证网络连接失败，请稍后重试' } }
        }
        current.config.addRequestChain({ postRequest(_type, _request, response) {
          const success = response.code === '0'
          if (!success) fail(response.message || '安全验证暂时不可用')
          response.code = success ? 200 : 4001
          response.msg = response.message
          return true
        } })
        current.init()
      } catch (cause) { fail(errorMessage(cause, '安全验证组件加载失败')) }
      finally { if (!disposed) setLoading(false) }
    })()
    return () => { disposed = true; instance?.destroyWindow(); instance = undefined }
  }, [open, elementId, attempt])
  return <Dialog open={open} onOpenChange={onOpenChange}><DialogContent className="codekin-theme w-[min(358px,94vw)]">
    <DialogTitle>安全验证</DialogTitle><DialogDescription>拖动滑块，完成拼图后发送验证码</DialogDescription>
    <div className="h-[360px] overflow-auto" id={elementId} aria-busy={loading}/>
    {failed && <Button variant="outline" disabled={loading} onClick={() => setAttempt(value => value + 1)}>重新加载</Button>}
  </DialogContent></Dialog>
}
