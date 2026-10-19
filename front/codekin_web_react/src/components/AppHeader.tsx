import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { LogOut, Settings, UserRound } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from './ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from './ui/dropdown-menu'
import { UserAvatar } from './UserAvatar'
import { useProfile } from '@/features/profile/useProfile'
import { useAuthStore } from '@/features/auth/session'
import { userApi } from '@/api/private'
import brandMark from '@/assets/brand-mark.png'
import wordmark from '@/assets/brand-wordmark.png'

export function AppHeader() {
  const { data: me } = useProfile()
  const [leaving, setLeaving] = useState(false)
  const navigate = useNavigate()
  async function logout() {
    if (leaving) return
    setLeaving(true)
    try { await userApi.logout(); toast.success('已退出登录') }
    catch { toast.warning('已退出本机登录，服务器暂未确认退出') }
    finally { useAuthStore.getState().clear(); navigate('/login', { replace: true }); setLeaving(false) }
  }
  return <header className="border-b bg-white px-5 py-3"><div className="mx-auto flex max-w-7xl items-center justify-between gap-3">
    <Link to="/profile" aria-label="Codekin 个人主页" className="flex items-center gap-2"><img src={brandMark} className="h-[30px] w-[30px]" alt="Codekin Logo"/><img src={wordmark} className="h-8 w-24 object-contain" alt="codekin"/></Link>
    <nav className="flex items-center gap-2"><Button asChild variant="ghost"><Link to="/resume">我的简历</Link></Button><Button asChild variant="ghost"><Link to="/resume/templates">简历模板</Link></Button>
      <DropdownMenu><DropdownMenuTrigger asChild><Button variant="ghost" disabled={leaving} aria-label="打开个人账号菜单"><UserAvatar src={me?.avatarUrl} nickname={me?.nickname} size={34}/></Button></DropdownMenuTrigger>
        <DropdownMenuContent align="end" className="codekin-theme"><DropdownMenuItem asChild><Link to="/profile"><UserRound/>个人主页</Link></DropdownMenuItem><DropdownMenuItem asChild><Link to="/settings/profile"><Settings/>个人资料</Link></DropdownMenuItem><DropdownMenuSeparator/><DropdownMenuItem disabled={leaving} onSelect={() => { void logout() }}><LogOut/>{leaving ? '退出中' : '退出登录'}</DropdownMenuItem></DropdownMenuContent>
      </DropdownMenu>
    </nav>
  </div></header>
}
