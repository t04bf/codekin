import { Link } from 'react-router'
import { BookOpen, Award, Activity, Code2 } from 'lucide-react'
import { AppHeader } from '@/components/AppHeader'
import { UserAvatar } from '@/components/UserAvatar'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { useProfile } from './useProfile'
import { LoadingState } from '@/components/LoadingState'

export default function ProfilePage() {
  const profile = useProfile()
  const me = profile.data
  return <div className="codekin-theme min-h-screen bg-background"><AppHeader/><main className="mx-auto grid max-w-7xl gap-6 p-5 md:grid-cols-[300px_1fr]">
    <Card className="h-fit space-y-5 bg-white p-6">
      {profile.isPending ? <LoadingState variant="section"/> : <><div className="flex items-center gap-4"><UserAvatar src={me?.avatarUrl} nickname={me?.nickname}/><div className="min-w-0"><h1 className="break-words text-lg font-semibold">{me?.nickname || '我的主页'}</h1><p className="break-all text-xs text-muted-foreground">ID · {me?.userId || '—'}</p></div></div><p className="whitespace-pre-wrap break-words text-sm text-muted-foreground">{me?.bio || '还没有填写个人简介'}</p></>}
      <Button asChild variant="outline" className="w-full"><Link to="/settings/profile">编辑个人资料</Link></Button>
      {profile.isError && <Button disabled={profile.isFetching} onClick={() => { void profile.refetch() }}>重新加载</Button>}
      <div className="space-y-3 border-t pt-5"><h2 className="font-semibold">基本信息</h2><p className="text-sm text-muted-foreground">公开主页展示昵称和个人简介。</p><Link className="text-sm text-primary" to="/settings/profile">查看我的完整资料</Link></div>
      <div className="space-y-3 border-t pt-5"><h2 className="font-semibold">成长空间</h2><p className="text-sm text-muted-foreground">学习与求职记录将在这里汇集。</p><span className="inline-block rounded-md bg-secondary px-3 py-1 text-xs">即将开放</span></div>
    </Card>
    <div className="grid content-start gap-5 sm:grid-cols-2">{[
      { title: '练习概览', description: '练习功能即将开放', Icon: BookOpen },
      { title: '成长成就', description: '成就功能即将开放', Icon: Award },
      { title: '学习活跃记录', description: '练习记录开放后，将展示你的学习轨迹', Icon: Activity, wide: true },
      { title: '最近练习', description: '题库与练习功能正在准备中', Icon: Code2, wide: true },
    ].map(({ title, description, Icon, wide }) => <Card className={`bg-white ${wide ? 'sm:col-span-2' : ''}`} key={title}><h2 className="border-b px-6 py-4 font-semibold">{title}</h2><div className="flex min-h-40 flex-col items-center justify-center gap-4 p-6 text-center text-sm text-muted-foreground"><Icon className="h-10 w-10 opacity-40"/>{description}</div></Card>)}</div>
  </main></div>
}
