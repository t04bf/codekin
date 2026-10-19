<script setup lang="ts">
import '@arco-design/web-vue/es/grid/style/css.js'
import { Grid as AGrid } from '@arco-design/web-vue'
import { onUnmounted, ref, useId, watch } from 'vue'
import { ElButton, ElContainer, ElDialog, ElMessage, ElScrollbar, ElText } from 'element-plus'
import { publicHttp } from '@/api/http'
import { loadCaptchaSdk, type CaptchaSdkInstance, type CaptchaSdkResponse } from '@/utils/captcha-sdk'

const visible = defineModel<boolean>({ required: true })
const emit = defineEmits<{ verified: [captchaId: string] }>()
const elementId = `codekin-captcha-${useId().replaceAll(':', '')}`
const errorMessage = ref('')
const loading = ref(false)
let instance: CaptchaSdkInstance | undefined

// 保留错误状态供重试入口使用；错误只通过全局 Message 展示，合并 SDK 的重复通知。
watch(errorMessage, (message) => {
  if (message) ElMessage.error({ message, grouping: true })
})

function destroyVerification() {
  instance?.destroyWindow()
  instance = undefined
}

async function startVerification() {
  if (loading.value) return
  destroyVerification()
  errorMessage.value = ''
  loading.value = true
  try {
    await loadCaptchaSdk()
    if (!visible.value || !window.TAC) return
    const current = new window.TAC({
      bindEl: `#${elementId}`,
      requestCaptchaDataUrl: '/api/auth/captcha/slider/generate',
      validCaptchaUrl: '/api/auth/captcha/slider/check',
      validSuccess: (response: CaptchaSdkResponse) => {
        if (instance !== current || !visible.value) return
        const captchaId = response.data?.captchaId
        if (!captchaId) {
          errorMessage.value = '验证响应异常，请重新验证'
          current.reloadCaptcha()
          return
        }
        // 只有后端核验成功才触发发送，发送接口还会再次消费一次性凭据。
        emit('verified', captchaId)
        visible.value = false
      },
      validFail: (response: CaptchaSdkResponse) => {
        if (instance !== current || !visible.value) return
        errorMessage.value = response.message || '验证未通过，请重试'
        current.reloadCaptcha()
      },
      btnCloseFun: () => { visible.value = false },
      btnRefreshFun: () => {
        errorMessage.value = ''
        current.reloadCaptcha()
      },
    }, { logoUrl: null, moveTrackMaskBgColor: '#dcebdc', moveTrackMaskBorderColor: '#508568' })
    instance = current

    // SDK 的公开请求方法复用项目 Axios 实例，保留统一代理、超时和环境配置。
    current.config.doSendRequest = async (request) => {
      try {
        const response = await publicHttp.request<CaptchaSdkResponse>({
          url: request.url, method: request.method, data: request.data, headers: request.headers,
        })
        return response.data
      } catch {
        return { code: 'CAPTCHA_NETWORK_ERROR', message: '安全验证网络连接失败，请稍后重试' }
      }
    }
    current.config.addRequestChain({
      postRequest(_type, _request, response) {
        // 在组件协议边界转换 R.code，后端其他业务状态码不受影响。
        const success = response.code === '0'
        if (!success) errorMessage.value = response.message || '安全验证暂时不可用'
        response.code = success ? 200 : 4001
        response.msg = response.message
        return true
      },
    })
    current.init()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '安全验证组件加载失败'
  } finally {
    loading.value = false
  }
}

onUnmounted(destroyVerification)
</script>


<template>
  <ElDialog v-model="visible" title="安全验证" width="min(358px, 94vw)" align-center destroy-on-close :close-on-click-modal="false" @opened="startVerification" @closed="destroyVerification">
    <AGrid :cols="1" :row-gap="16">
      <ElText type="info" size="small">拖动滑块，完成拼图后发送验证码</ElText>
      <ElScrollbar height="360px"><ElContainer :id="elementId" :aria-busy="loading" /></ElScrollbar>
      <ElButton v-if="errorMessage" size="small" :loading="loading" @click="startVerification">重新加载</ElButton>
    </AGrid>
  </ElDialog>
</template>
