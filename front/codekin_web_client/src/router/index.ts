import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', redirect: '/profile' },
    { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue'), meta: { title: '登录' } },
    { path: '/resume/templates', name: 'resume-templates', component: () => import('@/views/ResumeTemplatesView.vue'), meta: { title: '简历模板 · Codekin' } },
    { path: '/terms', name: 'terms', component: () => import('@/views/TermsView.vue'), meta: { title: '用户协议 · Codekin' } },
    { path: '/privacy', name: 'privacy', component: () => import('@/views/PrivacyView.vue'), meta: { title: '隐私政策 · Codekin' } },
    {
      path: '/workspace',
      name: 'workspace',
      redirect: { name: 'profile' },
    },
    { path: '/profile', name: 'profile', component: () => import('@/views/ProfileView.vue'), meta: { requiresAuth: true, title: '个人主页 · Codekin' } },
    { path: '/settings/profile', name: 'profile-settings', component: () => import('@/views/ProfileSettingsView.vue'), meta: { requiresAuth: true, title: '个人资料 · Codekin' } },
    { path: '/:pathMatch(.*)*', redirect: '/profile' },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (to.meta.requiresAuth && !await auth.ensureAccessToken()) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login' && (auth.accessToken || auth.refreshToken)
      && await auth.ensureAccessToken()) {
    return { name: 'profile' }
  }
})

router.afterEach((to) => {
  document.title = typeof to.meta.title === 'string' ? to.meta.title : 'Codekin'
})

export default router
