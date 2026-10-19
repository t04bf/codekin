import { ref } from 'vue'
import { defineStore } from 'pinia'
import { ElMessage } from 'element-plus'
import { userApi, type MyUser } from '@/api/private'
import { useAuthStore } from '@/stores/auth'

/** 个人主页和资料页共用服务端资料，不在本地持久化真实姓名、生日等信息。 */
export const useProfileStore = defineStore('profile', () => {
  const me = ref<MyUser | null>(null)
  const loading = ref(false)
  const error = ref('')
  let inFlight: Promise<void> | null = null

  async function load() {
    if (inFlight) return inFlight
    loading.value = true
    error.value = ''
    const auth = useAuthStore()
    const requestedUserId = auth.userId
    inFlight = (async () => {
      try {
        const result = await userApi.getMe()
        if (auth.userId === requestedUserId) me.value = result
      } catch (cause) {
        if (auth.userId !== requestedUserId) return
        error.value = cause instanceof Error ? cause.message : '个人资料暂时无法加载'
        // 共用加载流程只提示一次，页面保留重试入口，不再放置错误横幅。
        ElMessage.error({ message: error.value, grouping: true })
      } finally {
        loading.value = false
        inFlight = null
      }
    })()
    return inFlight
  }

  function clear() {
    me.value = null
    error.value = ''
  }

  return { me, loading, error, load, clear }
})
