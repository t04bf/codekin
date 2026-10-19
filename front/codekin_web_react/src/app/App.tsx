import { lazy, Suspense, useEffect } from 'react'
import { Navigate, Outlet, Route, Routes, useLocation } from 'react-router'
import { RequireAuth } from '@/features/auth/RequireAuth'
import { ResumeEditorProvider } from '@/store/useResumeStore'
import { LoadingState } from '@/components/LoadingState'

const LoginPage = lazy(() => import('@/features/auth/LoginPage'))
const ProfilePage = lazy(() => import('@/features/profile/ProfilePage'))
const ProfileSettingsPage = lazy(() => import('@/features/profile/ProfileSettingsPage'))
const TemplatesPage = lazy(() => import('@/features/resume/TemplatesPage'))
const ResumesPage = lazy(() => import('@/features/resume/ResumesPage'))
const ResumeEditorPage = lazy(() => import('@/features/resume/ResumeEditorPage'))
const TermsPage = lazy(() => import('@/features/legal/TermsPage'))
const PrivacyPage = lazy(() => import('@/features/legal/PrivacyPage'))

export function App() {
  const location = useLocation()
  useEffect(() => {
    const titles: Record<string, string> = { '/login': '登录', '/profile': '个人主页', '/settings/profile': '个人资料', '/resume': '我的简历', '/resume/templates': '简历模板', '/terms': '用户协议', '/privacy': '隐私政策' }
    document.title = `${titles[location.pathname] || 'Codekin'} · Codekin`
  }, [location.pathname])
  return <Suspense fallback={<LoadingState/>}><Routes>
    <Route path="/login" element={<LoginPage/>}/><Route path="/terms" element={<TermsPage/>}/><Route path="/privacy" element={<PrivacyPage/>}/>
    <Route path="/resume" element={<ResumeEditorProvider><Outlet/></ResumeEditorProvider>}>
      <Route path="templates" element={<TemplatesPage/>}/>
      <Route element={<RequireAuth/>}>
        <Route index element={<ResumesPage/>}/>
        <Route path=":id/edit" element={<ResumeEditorPage/>}/>
      </Route>
    </Route>
    <Route element={<RequireAuth/>}>
      <Route path="/profile" element={<ProfilePage/>}/>
      <Route path="/settings/profile" element={<ProfileSettingsPage/>}/>
    </Route>
    <Route path="*" element={<Navigate to="/profile" replace/>}/>
  </Routes></Suspense>
}
