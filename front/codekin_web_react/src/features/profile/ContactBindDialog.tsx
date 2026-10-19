import { useEffect, useRef } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Dialog, DialogContent, DialogDescription, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { FormField } from '@/components/FormField'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { userApi, type MyUser } from '@/api/private'
import { useCodeCooldown } from '@/features/auth/useCodeCooldown'
import { codeSchema, emailSchema, phoneSchema } from '@/features/auth/validation'
import { useAuthStore } from '@/features/auth/session'
import { maskEmail, maskPhone } from '@/utils/contact-display'
import { errorMessage } from '@/lib/errors'

export function ContactBindDialog({ open, kind, me, onClose }: { open: boolean; kind: 'email' | 'phone'; me: MyUser; onClose: () => void }) {
  const queryClient = useQueryClient()
  const label = kind === 'email' ? '邮箱' : '手机号'
  const current = me[kind]
  const addressSchema = kind === 'email' ? emailSchema : phoneSchema
  const form = useForm<{ address: string; code: string }>({ resolver: zodResolver(z.object({ address: addressSchema, code: codeSchema })), defaultValues: { address: '', code: '' } })
  const { reset } = form
  useEffect(() => { if (open) reset({ address: '', code: '' }) }, [open, kind, reset])
  const cooldown = useCodeCooldown(`${kind}:${form.watch('address').trim().toLowerCase()}`)
  const lock = useRef(false)
  const send = useMutation({ mutationFn: ({ kind: targetKind, address }: { kind: 'email' | 'phone'; address: string }) => targetKind === 'email' ? userApi.sendBindEmailCode(address) : userApi.sendBindPhoneCode(address) })
  const bind = useMutation({ mutationFn: ({ kind: targetKind, address, code }: { kind: 'email' | 'phone'; address: string; code: string }) => targetKind === 'email' ? userApi.bindEmail(address, code) : userApi.bindPhone(address, code) })
  const busy = send.isPending || bind.isPending || form.formState.isSubmitting
  async function sendCode() {
    if (lock.current || cooldown.remaining > 0) return
    const result = addressSchema.safeParse(form.getValues('address'))
    if (!result.success) { form.setError('address', { message: result.error.issues[0].message }); return }
    form.clearErrors('address')
    lock.current = true
    const targetKind = kind
    try {
      await send.mutateAsync({ kind: targetKind, address: result.data })
      cooldown.start(`${targetKind}:${result.data}`)
      toast.success('验证码已发送，请注意查收')
    } catch (cause) { toast.error(errorMessage(cause, '验证码发送失败，请稍后重试')) }
    finally { lock.current = false }
  }
  async function submit(values: { address: string; code: string }) {
    if (lock.current) return
    lock.current = true
    const targetKind = kind
    try {
      await bind.mutateAsync({ kind: targetKind, ...values })
      if (useAuthStore.getState().userId !== me.userId) return
      queryClient.setQueryData<MyUser>(['me', me.userId], profile => profile ? { ...profile, [targetKind]: targetKind === 'phone' ? `+86${values.address}` : values.address } : profile)
      onClose()
      toast.success(`${label}${current ? '换绑' : '绑定'}成功`)
      void queryClient.invalidateQueries({ queryKey: ['me', me.userId] })
    } catch (cause) { toast.error(errorMessage(cause, '绑定失败，请稍后重试')) }
    finally { lock.current = false }
  }
  return <Dialog open={open} onOpenChange={value => { if (!value && !busy) onClose() }}><DialogContent hideClose={busy} className="codekin-theme w-[min(500px,94vw)]" onInteractOutside={event => event.preventDefault()}>
    <DialogTitle>{current ? '换绑' : '绑定'}{label}</DialogTitle><DialogDescription>{current ? `当前绑定：${kind === 'email' ? maskEmail(current) : maskPhone(current)}。请验证新的${label}。` : `验证码将发送到你填写的${label}，验证后即可绑定。`}</DialogDescription>
    <form noValidate className="space-y-5" onSubmit={form.handleSubmit(submit)}><fieldset disabled={busy} className="space-y-5">
      <FormField id="bind-address" label={`${current ? '新' : ''}${label}`} error={form.formState.errors.address?.message}><div className="flex items-center gap-2">{kind === 'phone' && <span className="text-sm">+86</span>}<Input id="bind-address" type={kind === 'email' ? 'email' : 'tel'} maxLength={kind === 'email' ? 254 : 11} autoComplete="off" {...form.register('address', { onChange: () => form.setValue('code', '') })}/></div></FormField>
      <FormField id="bind-code" label="验证码" error={form.formState.errors.code?.message}><div className="grid grid-cols-[1fr_auto] gap-3"><Input id="bind-code" maxLength={6} inputMode="numeric" autoComplete="one-time-code" {...form.register('code')}/><Button type="button" variant="outline" disabled={busy || cooldown.remaining > 0} onClick={() => { void sendCode() }}>{cooldown.remaining > 0 ? `${cooldown.remaining} 秒后重试` : '获取验证码'}</Button></div></FormField>
    </fieldset><DialogFooter><Button type="button" variant="outline" disabled={busy} onClick={onClose}>取消</Button><Button type="submit" disabled={busy}>{bind.isPending ? '保存中' : `确认${current ? '换绑' : '绑定'}`}</Button></DialogFooter></form>
  </DialogContent></Dialog>
}
