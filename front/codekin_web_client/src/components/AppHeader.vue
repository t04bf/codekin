<script setup lang="ts">
import { ref } from 'vue'
import { Image as AImage } from '@arco-design/web-vue'
import '@arco-design/web-vue/es/image/style/css.js'
import { useRouter } from 'vue-router'
import { LogOut, Settings, UserRound } from '@lucide/vue'
import { ElButton, ElCol, ElDropdown, ElDropdownItem, ElDropdownMenu, ElHeader, ElIcon, ElLink, ElMessage, ElRow, ElSpace } from 'element-plus'
import { userApi } from '@/api/private'
import { useAuthStore } from '@/stores/auth'
import { useProfileStore } from '@/stores/profile'
import UserAvatar from './UserAvatar.vue'
import brandWordmark from '@/assets/brand-wordmark.png'
import brandMark from '@/assets/brand-mark.png'

const router = useRouter()
const auth = useAuthStore()
const profile = useProfileStore()
const leaving = ref(false)
const menuOpen = ref(false)

function handleAccountCommand(command: string | number) {
  if (command === 'logout') void logout()
  else if (command === 'profile') void router.push('/profile')
  else if (command === 'settings') void router.push('/settings/profile')
}

async function logout() {
  if (leaving.value) return
  leaving.value = true
  try {
    await userApi.logout()
    ElMessage.success('已退出登录')
  } catch {
    // 网络不可用时仍退出本地登录，服务端会话按原有 TTL 失效。
    ElMessage.warning('已退出本机登录，服务器暂未确认退出')
  } finally {
    auth.clear()
    profile.clear()
    await router.replace('/login')
    leaving.value = false
  }
}
</script>

<template>
  <ElHeader height="auto">
    <ElRow justify="space-between" align="middle">
      <ElCol :span="12">
        <ElLink href="/profile" :underline="false" aria-label="Codekin 个人主页" @click.prevent="router.push('/profile')">
          <ElSpace><AImage :src="brandMark" :width="30" :height="30" fit="contain" :preview="false" alt="Codekin Logo" /><AImage :src="brandWordmark" :width="96" :height="32" fit="contain" :preview="false" alt="codekin" /></ElSpace>
        </ElLink>
      </ElCol>
      <ElCol :span="12">
        <ElRow justify="end" align="middle">
          <ElButton text @click="router.push('/resume/templates')">简历模板</ElButton>
          <ElDropdown trigger="click" placement="bottom-end" @command="handleAccountCommand" @visible-change="menuOpen = $event">
            <ElButton text size="large" aria-label="打开个人账号菜单" aria-haspopup="menu" :aria-expanded="menuOpen" :disabled="leaving"><UserAvatar :src="profile.me?.avatarUrl" :nickname="profile.me?.nickname" :size="34" /></ElButton>
            <template #dropdown><ElDropdownMenu>
              <ElDropdownItem command="profile"><ElIcon><UserRound /></ElIcon>个人主页</ElDropdownItem>
              <ElDropdownItem command="settings"><ElIcon><Settings /></ElIcon>个人资料</ElDropdownItem>
              <ElDropdownItem command="logout" divided :disabled="leaving"><ElIcon><LogOut /></ElIcon>{{ leaving ? '退出中' : '退出登录' }}</ElDropdownItem>
            </ElDropdownMenu></template>
          </ElDropdown>
        </ElRow>
      </ElCol>
    </ElRow>
  </ElHeader>
</template>
