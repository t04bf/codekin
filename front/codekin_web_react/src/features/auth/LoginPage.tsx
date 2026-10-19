import { useEffect, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useMutation } from '@tanstack/react-query'
import { Eye, EyeOff, KeyRound, Mail, Smartphone } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card } from '@/components/ui/card'
import { FormField } from '@/components/FormField'
import { emailSchema, phoneSchema, codeSchema } from './validation'
import { authApi } from '@/api/auth'
import { useAuthStore } from './session'
import { useCodeCooldown } from './useCodeCooldown'
import { CaptchaDialog } from './CaptchaDialog'
import { errorMessage } from '@/lib/errors'
import brandMark from '@/assets/brand-mark.png'
import wordmark from '@/assets/brand-wordmark.png'
import illustration from '@/assets/login-offer.png'

type Mode = 'password' | 'email' | 'phone'
type Fields = { identifier: string; address: string; password: string; code: string }
const modes = [{ id: 'password', label: '账号密码登录', Icon: KeyRound },
  { id: 'email', label: '邮箱验证码登录', Icon: Mail }, { id: 'phone', label: '手机验证码登录', Icon: Smartphone }] as const


export default function LoginPage() {
  const [mode, setMode] = useState<Mode>('password')
  const [showPassword, setShowPassword] = useState(false)
  const [captchaOpen, setCaptchaOpen] = useState(false)
  const [params, setParams] = useSearchParams()
  const navigate = useNavigate()
  const target = useRef<{ mode: 'email' | 'phone'; address: string }>()
  const sending = useRef(false)
  const [isSending, setIsSending] = useState(false)
  const schema = z.object({ identifier: z.string(), address: z.string(), password: z.string(), code: z.string() }).superRefine((data, ctx) => {
    if (mode === 'password') {
      if (!data.identifier.trim()) ctx.addIssue({ code: 'custom', path: ['identifier'], message: '请输入邮箱或手机号' })
      if (!data.password) ctx.addIssue({ code: 'custom', path: ['password'], message: '请输入密码' })
    } else {
      const address = (mode === 'email' ? emailSchema : phoneSchema).safeParse(data.address)
      if (!address.success) ctx.addIssue({ code: 'custom', path: ['address'], message: address.error.issues[0].message })
      if (!codeSchema.safeParse(data.code).success) ctx.addIssue({ code: 'custom', path: ['code'], message: '请输入六位数字验证码' })
    }
  })
  const form = useForm<Fields>({ resolver: zodResolver(schema), defaultValues: { identifier: '', address: '', password: '', code: '' } })
  const recipient = `${mode}:${form.watch('address').trim().toLowerCase()}`
  const cooldown = useCodeCooldown(recipient)
  const login = useMutation({ mutationFn: (data: Fields) => mode === 'password'
    ? authApi.passwordLogin(data.identifier.trim(), data.password)
    : mode === 'email' ? authApi.emailLogin(data.address.trim().toLowerCase(), data.code.trim())
    : authApi.smsLogin(data.address.trim(), data.code.trim()) })
  useEffect(() => {
    let alive = true
    if (useAuthStore.getState().refreshToken) void useAuthStore.getState().ensureAccessToken().then(ready => {
      if (alive && ready) navigate('/profile', { replace: true })
    })
    return () => { alive = false }
  }, [navigate])
  useEffect(() => {
    if (params.get('passwordUpdated') !== '1') return
    toast.success('密码已保存，请重新登录', { id: 'password-saved' })
    const next = new URLSearchParams(params)
    next.delete('passwordUpdated')
    setParams(next, { replace: true })
  }, [params, setParams])
  async function submit(data: Fields) {
    if (login.isPending) return
    try {
      useAuthStore.getState().accept(await login.mutateAsync(data))
      const redirect = params.get('redirect')
      navigate(redirect?.startsWith('/') && !redirect.startsWith('//') && !redirect.includes('\\') && !redirect.startsWith('/login') ? redirect : '/profile', { replace: true })
    } catch (cause) { toast.error(errorMessage(cause, '登录失败，请稍后重试')) }
  }
  function requestCaptcha() {
    if (sending.current || cooldown.remaining > 0 || mode === 'password') return
    const result = (mode === 'email' ? emailSchema : phoneSchema).safeParse(form.getValues('address'))
    if (!result.success) { form.setError('address', { message: result.error.issues[0].message }); return }
    form.clearErrors('address')
    target.current = { mode, address: result.data }
    setCaptchaOpen(true)
  }
  async function sendCode(captchaId: string) {
    const snapshot = target.current
    if (!snapshot || sending.current) return
    sending.current = true
    setIsSending(true)
    try {
      if (snapshot.mode === 'email') await authApi.sendEmailCode(snapshot.address, captchaId)
      else await authApi.sendSmsCode(snapshot.address, captchaId)
      cooldown.start(`${snapshot.mode}:${snapshot.address}`)
      toast.success('验证码已发送，请注意查收')
    } catch (cause) { toast.error(errorMessage(cause, '发送失败，请稍后重试')) }
    finally { sending.current = false; setIsSending(false); target.current = undefined }
  }
  const busy = form.formState.isSubmitting || login.isPending || isSending
  const errors = form.formState.errors
  return <main className="codekin-theme flex min-h-screen items-center justify-center bg-background p-5">
    <Card className="grid w-full max-w-[1200px] overflow-hidden bg-white md:grid-cols-2">
      <div className="flex items-center justify-center px-6 py-10 sm:px-12">
        <div className="w-full space-y-5">
          <div className="flex items-center gap-3"><img src={brandMark} className="h-9 w-9" alt="Codekin Logo"/><img src={wordmark} className="h-[42px] w-[124px] object-contain" alt="codekin"/></div>
          <h1 className="text-2xl font-semibold">欢迎回来</h1>
          <div className="grid gap-3 sm:grid-cols-2">{modes.filter(item => item.id !== mode).map(({ id, label, Icon }) => <Button key={id} variant="outline" size="lg" disabled={busy} onClick={() => {
            setMode(id); setShowPassword(false); form.reset(); target.current = undefined; setCaptchaOpen(false)
          }}><Icon className="h-4 w-4"/>{label}</Button>)}</div>
          <div className="flex items-center gap-4 text-sm text-muted-foreground"><div className="h-px flex-1 bg-border"/>或<div className="h-px flex-1 bg-border"/></div>
          <form className="space-y-5" noValidate onSubmit={form.handleSubmit(submit)}>
            <fieldset disabled={busy} className="space-y-5">
              {mode === 'password' ? <>
                <FormField id="login-identifier" label="邮箱或手机号" error={errors.identifier?.message}><Input id="login-identifier" autoComplete="username" placeholder="请输入邮箱或手机号" {...form.register('identifier')}/></FormField>
                <FormField id="login-password" label="密码" error={errors.password?.message}><div className="relative"><Input id="login-password" className="pr-12" type={showPassword ? "text" : "password"} autoComplete="current-password" placeholder="请输入密码" {...form.register('password')}/><Button type="button" variant="ghost" size="icon" className="absolute right-0 top-0" aria-label={showPassword ? "隐藏密码" : "显示密码"} onClick={() => setShowPassword(value => !value)}>{showPassword ? <EyeOff className="h-4 w-4"/> : <Eye className="h-4 w-4"/>}</Button></div></FormField>
              </> : <>
                <FormField id="login-address" label={mode === 'email' ? '邮箱地址' : '手机号码'} error={errors.address?.message}><Input id="login-address" type={mode === 'email' ? 'email' : 'tel'} autoComplete={mode === 'email' ? 'email' : 'tel'} placeholder={mode === 'email' ? '请输入邮箱地址' : '请输入手机号'} {...form.register('address', { onChange: () => form.setValue('code', '') })}/></FormField>
                <FormField id="login-code" label="验证码" error={errors.code?.message}><div className="grid grid-cols-[1fr_auto] gap-3"><Input id="login-code" inputMode="numeric" maxLength={6} autoComplete="one-time-code" placeholder="六位验证码" {...form.register('code')}/><Button type="button" variant="outline" disabled={cooldown.remaining > 0} onClick={requestCaptcha}>{cooldown.remaining > 0 ? `${cooldown.remaining}s 后重发` : '获取验证码'}</Button></div></FormField>
                <p className="text-sm text-muted-foreground">首次使用验证码登录时，会自动创建账号。</p>
              </>}
              <Button type="submit" size="lg" className="w-full">{busy ? '正在登录' : '登录并继续'}</Button>
            </fieldset>
          </form>
          <p className="text-xs leading-6 text-muted-foreground">注册或登录即代表您已阅读并同意<a className="text-primary" href="/terms" target="_blank" rel="noopener noreferrer">《用户协议》</a>，并已阅读<a className="text-primary" href="/privacy" target="_blank" rel="noopener noreferrer">《隐私政策》</a>。</p>
        </div>
      </div>
      <img src={illustration} className="hidden h-[680px] w-full object-cover md:block" alt="吉祥物伸手迎向写有 OFFER 的信封"/>
    </Card>
    <CaptchaDialog open={captchaOpen} onOpenChange={setCaptchaOpen} onVerified={id => { void sendCode(id) }}/>
  </main>
}
