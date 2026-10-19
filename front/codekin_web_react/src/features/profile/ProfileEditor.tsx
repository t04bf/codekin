import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { userApi, type MyUser, type ProfileUpdate } from '@/api/private'
import { Dialog, DialogContent, DialogDescription, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { FormField } from '@/components/FormField'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Button } from '@/components/ui/button'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { errorMessage } from '@/lib/errors'
import { useAuthStore } from '@/features/auth/session'

export type EditSection = 'basic' | 'gender' | 'birthday' | 'name'
const titles: Record<EditSection, string> = { basic: '编辑昵称与简介', gender: '编辑性别', birthday: '编辑生日', name: '编辑真实姓名' }
export const genderLabels = { MALE: '男', FEMALE: '女', OTHER: '其他' }
const schema = z.object({
  nickname: z.string().trim().min(1, '请输入昵称').max(40, '昵称不能超过 40 个字符'),
  bio: z.string().max(500, '简介不能超过 500 个字符'),
  genderCode: z.enum(['', 'MALE', 'FEMALE', 'OTHER']),
  birthDate: z.string().refine(value => !value || z.iso.date().safeParse(value).success && value <= new Date(Date.now() - new Date().getTimezoneOffset() * 60_000).toISOString().slice(0, 10), '生日应为有效日期，不能晚于今天'),
  realName: z.string().max(100, '真实姓名不能超过 100 个字符'),
})

// Mounted per edit session: cancel/reopen discards drafts and snapshots the current profile.
export function ProfileEditor({ me, section, onClose }: { me: MyUser; section: EditSection; onClose: () => void }) {
  const queryClient = useQueryClient()
  const form = useForm<z.infer<typeof schema>>({ resolver: zodResolver(schema), defaultValues: {
    nickname: me.nickname, bio: me.bio || '', genderCode: (me.genderCode || '') as '' | 'MALE' | 'FEMALE' | 'OTHER',
    birthDate: me.birthDate || '', realName: me.realName || '',
  } })
  const save = useMutation({ mutationFn: (payload: ProfileUpdate) => userApi.updateProfile(payload) })
  const busy = save.isPending || form.formState.isSubmitting
  async function submit(values: z.infer<typeof schema>) {
    const payload: ProfileUpdate = { nickname: values.nickname, bio: values.bio.trim() || null, genderCode: values.genderCode || null, birthDate: values.birthDate || null, realName: values.realName.trim() || null }
    try {
      await save.mutateAsync(payload)
      if (useAuthStore.getState().userId !== me.userId) return
      queryClient.setQueryData<MyUser>(['me', me.userId], current => current ? { ...current, ...payload } : current)
      toast.success('个人资料已保存')
      onClose()
      void queryClient.invalidateQueries({ queryKey: ['me', me.userId] })
    } catch (cause) { toast.error(errorMessage(cause, '保存失败，请稍后重试')) }
  }
  const errors = form.formState.errors
  return <Dialog open onOpenChange={open => { if (!open && !busy) onClose() }}><DialogContent hideClose={busy} className="codekin-theme w-[min(500px,94vw)]" onInteractOutside={event => event.preventDefault()}>
    <DialogTitle>{titles[section]}</DialogTitle><DialogDescription>{section === 'basic' ? '昵称和简介将展示在个人主页。' : '此项为选填信息，仅你自己可见。'}</DialogDescription>
    <form className="space-y-5" noValidate onSubmit={form.handleSubmit(submit)}><fieldset className="space-y-5" disabled={busy}>
      {section === 'basic' ? <>
        <FormField id="profile-nickname" label="昵称" error={errors.nickname?.message}><Input id="profile-nickname" maxLength={40} autoComplete="nickname" {...form.register('nickname')}/></FormField>
        <FormField id="profile-bio" label="个人简介" error={errors.bio?.message}><Textarea id="profile-bio" rows={5} maxLength={500} {...form.register('bio')}/></FormField>
      </> : section === 'gender' ? <FormField id="profile-gender" label="性别" error={errors.genderCode?.message}><Select value={form.watch('genderCode') || 'unset'} onValueChange={value => form.setValue('genderCode', value === 'unset' ? '' : value as 'MALE' | 'FEMALE' | 'OTHER')}><SelectTrigger id="profile-gender"><SelectValue placeholder="未填写"/></SelectTrigger><SelectContent className="codekin-theme"><SelectItem value="unset">未填写</SelectItem>{Object.entries(genderLabels).map(([value, label]) => <SelectItem key={value} value={value}>{label}</SelectItem>)}</SelectContent></Select></FormField>
      : section === 'birthday' ? <FormField id="profile-birthday" label="生日" error={errors.birthDate?.message}><Input id="profile-birthday" type="date" max={new Date(Date.now() - new Date().getTimezoneOffset() * 60_000).toISOString().slice(0, 10)} {...form.register('birthDate')}/></FormField>
      : <FormField id="profile-name" label="真实姓名" error={errors.realName?.message}><Input id="profile-name" maxLength={100} autoComplete="name" {...form.register('realName')}/></FormField>}
    </fieldset><DialogFooter><Button type="button" variant="outline" disabled={busy} onClick={onClose}>取消</Button><Button type="submit" disabled={busy}>{busy ? '保存中' : '保存'}</Button></DialogFooter></form>
  </DialogContent></Dialog>
}
