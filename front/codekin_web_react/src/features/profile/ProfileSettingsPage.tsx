import { useState } from 'react'
import { LoadingState } from '@/components/LoadingState'
import { Link } from 'react-router'
import { ArrowLeft, CalendarDays, ChevronRight, Contact2, KeyRound, Mail, Smartphone, UserRound, Users, type LucideIcon } from 'lucide-react'
import { AppHeader } from '@/components/AppHeader'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { useProfile } from './useProfile'
import { ProfileEditor, genderLabels, type EditSection } from './ProfileEditor'
import { maskEmail, maskPhone } from '@/utils/contact-display'
import { ContactBindDialog } from './ContactBindDialog'
import { PasswordDialog } from './PasswordDialog'
import { AvatarPicker } from './AvatarPicker'

function SettingRow({ label, value, action, Icon, onClick }: { label: string; value: string; action?: string; Icon: LucideIcon; onClick: () => void }) {
  return <button className="flex w-full items-center gap-4 p-5 text-left transition-colors hover:bg-accent focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-ring" onClick={onClick}><Icon className="h-5 w-5 shrink-0 text-muted-foreground"/><span className="font-medium">{label}</span><span className="ml-auto min-w-0 truncate text-sm text-muted-foreground">{value}</span>{action && <span className="shrink-0 text-sm text-primary">{action}</span>}<ChevronRight className="h-4 w-4 shrink-0 text-muted-foreground"/></button>
}
export default function ProfileSettingsPage() {
  const profile = useProfile()
  const me = profile.data
  const [section, setSection] = useState<EditSection>()
  const [contact, setContact] = useState<'email' | 'phone'>('email')
  const [contactOpen, setContactOpen] = useState(false)
  const [passwordOpen, setPasswordOpen] = useState(false)
  return <div className="codekin-theme min-h-screen bg-background"><AppHeader/><main className="mx-auto max-w-3xl space-y-6 p-5 sm:py-8">
    <div className="flex items-center gap-4"><Button asChild variant="ghost" size="icon"><Link to="/profile" aria-label="返回个人主页"><ArrowLeft className="h-5 w-5"/></Link></Button><h1 className="text-xl font-semibold">个人资料</h1></div>
    {me && <div className="flex justify-center"><AvatarPicker me={me}/></div>}
    {profile.isError && <Button disabled={profile.isFetching} onClick={() => { void profile.refetch() }}>重新加载</Button>}
    {profile.isPending ? <LoadingState variant="section"/> : me && <>
      <section className="space-y-3"><h2 className="font-semibold">个人信息</h2><p className="text-sm text-muted-foreground">管理个人资料</p><Card className="divide-y overflow-hidden bg-white">
        <SettingRow Icon={UserRound} label="昵称与简介" value={me.nickname} onClick={() => setSection('basic')}/>
        <SettingRow Icon={Users} label="性别" value={genderLabels[me.genderCode as keyof typeof genderLabels] || '未填写'} onClick={() => setSection('gender')}/>
        <SettingRow Icon={CalendarDays} label="生日" value={me.birthDate || '未填写'} onClick={() => setSection('birthday')}/>
        <SettingRow Icon={Contact2} label="真实姓名" value={me.realName || '未填写'} onClick={() => setSection('name')}/>
      </Card><p className="text-xs text-muted-foreground">性别、生日和真实姓名仅本人可见。</p></section>
      <section className="space-y-3"><h2 className="font-semibold">账号与安全</h2><p className="text-sm text-muted-foreground">管理登录使用的联系方式和密码</p><Card className="divide-y overflow-hidden bg-white">
        <SettingRow Icon={Mail} label="邮箱" value={maskEmail(me.email)} action={me.email ? '换绑' : '绑定'} onClick={() => { setContact('email'); setContactOpen(true) }}/>
        <SettingRow Icon={Smartphone} label="手机号" value={maskPhone(me.phone)} action={me.phone ? '换绑' : '绑定'} onClick={() => { setContact('phone'); setContactOpen(true) }}/>
        <SettingRow Icon={KeyRound} label="账号密码" value={me.hasPassword ? '已设置' : '未设置'} action={me.hasPassword ? '修改密码' : '设置密码'} onClick={() => setPasswordOpen(true)}/>
      </Card></section>
      <Card className="bg-white p-6"><h2 className="font-semibold">个人经历</h2><p className="py-10 text-center text-sm text-muted-foreground">工作经历、教育经历与技能标签即将开放</p></Card>
    </>}
    {me && section && <ProfileEditor me={me} section={section} onClose={() => setSection(undefined)}/>}
    {me && <ContactBindDialog open={contactOpen} kind={contact} me={me} onClose={() => setContactOpen(false)}/>}
    {me && passwordOpen && <PasswordDialog me={me} onClose={() => setPasswordOpen(false)}/>}
  </main></div>
}
