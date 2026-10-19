import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useMutation } from '@tanstack/react-query'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { userApi, type MyUser } from '@/api/private'
import { useAuthStore } from '@/features/auth/session'
import { Dialog, DialogContent, DialogDescription, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { FormField } from '@/components/FormField'
import { PasswordInput } from '@/components/PasswordInput'
import { Button } from '@/components/ui/button'
import { errorMessage } from '@/lib/errors'

export function PasswordDialog({ me, onClose }: { me: MyUser; onClose: () => void }) {
  const navigate = useNavigate()
  const schema = z.object({ oldPassword: z.string(), newPassword: z.string().min(8, '密码长度应为 8 至 72 个字符').max(72, '密码长度应为 8 至 72 个字符').refine(value => new TextEncoder().encode(value).length <= 72, '密码不能超过 72 个 UTF-8 字节'), confirmPassword: z.string() }).superRefine((data, ctx) => {
    if (me.hasPassword && !data.oldPassword) ctx.addIssue({ code: 'custom', path: ['oldPassword'], message: '请输入当前密码' })
    if (data.confirmPassword !== data.newPassword) ctx.addIssue({ code: 'custom', path: ['confirmPassword'], message: '两次输入的密码不一致' })
  })
  const form = useForm<z.infer<typeof schema>>({ resolver: zodResolver(schema), defaultValues: { oldPassword: '', newPassword: '', confirmPassword: '' } })
  const save = useMutation({ mutationFn: (values: z.infer<typeof schema>) => userApi.setPassword(values.newPassword, me.hasPassword ? values.oldPassword : undefined) })
  const busy = save.isPending || form.formState.isSubmitting
  async function submit(values: z.infer<typeof schema>) {
    try {
      await save.mutateAsync(values)
      if (useAuthStore.getState().userId !== me.userId) return
      useAuthStore.getState().clear()
      onClose()
      navigate('/login', { replace: true })
      toast.success('密码已保存，请重新登录', { id: 'password-saved' })
    } catch (cause) { toast.error(errorMessage(cause, '密码保存失败，请稍后重试')) }
  }
  return <Dialog open onOpenChange={open => { if (!open && !busy) onClose() }}><DialogContent hideClose={busy} className="codekin-theme w-[min(500px,94vw)]" onInteractOutside={event => event.preventDefault()}>
    <DialogTitle>{me.hasPassword ? '修改密码' : '设置密码'}</DialogTitle><DialogDescription>保存成功后，所有原有登录会话将失效，请使用新密码重新登录。</DialogDescription>
    <form className="space-y-5" noValidate onSubmit={form.handleSubmit(submit)}><fieldset disabled={busy} className="space-y-5">
      {me.hasPassword && <FormField id="password-old" label="当前密码" error={form.formState.errors.oldPassword?.message}><PasswordInput id="password-old" autoComplete="current-password" {...form.register('oldPassword')}/></FormField>}
      <FormField id="password-new" label="新密码" error={form.formState.errors.newPassword?.message}><PasswordInput id="password-new" maxLength={72} autoComplete="new-password" {...form.register('newPassword')}/></FormField>
      <FormField id="password-confirm" label="确认新密码" error={form.formState.errors.confirmPassword?.message}><PasswordInput id="password-confirm" maxLength={72} autoComplete="new-password" {...form.register('confirmPassword')}/></FormField>
    </fieldset><DialogFooter><Button type="button" variant="outline" disabled={busy} onClick={onClose}>取消</Button><Button type="submit" disabled={busy}>{busy ? '保存中' : '保存并重新登录'}</Button></DialogFooter></form>
  </DialogContent></Dialog>
}
