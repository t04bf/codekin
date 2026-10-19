<script setup lang="ts">
import '@arco-design/web-vue/es/grid/style/css.js'
import '@arco-design/web-vue/es/image/style/css.js'
import '@arco-design/web-vue/es/typography/style/css.js'
import { Grid as AGrid, GridItem as AGridItem, Image as AImage, TypographyTitle as ATypographyTitle } from '@arco-design/web-vue'
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import axios from 'axios'
import { ElAvatar, ElButton, ElCard, ElCol, ElContainer, ElDivider, ElForm, ElFormItem, ElIcon, ElInput, ElLink, ElMain, ElMessage, ElRow, ElSpace, ElText } from 'element-plus'
import { KeyRound, Mail, Smartphone, UserRound } from '@lucide/vue'
import CaptchaDialog from '@/components/CaptchaDialog.vue'
import { authApi } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import brandMark from '@/assets/brand-mark.png'
import brandWordmark from '@/assets/brand-wordmark.png'
import loginIllustration from '@/assets/login-offer.png'

type LoginMode = 'password' | 'email' | 'phone'
const modes: { value: LoginMode; label: string }[] = [
  { value: 'password', label: '账号密码登录' },
  { value: 'email', label: '邮箱验证码登录' },
  { value: 'phone', label: '手机验证码登录' },
]

const mode = ref<LoginMode>('password')
const identifier = ref('')
const email = ref('')
const phone = ref('')
const password = ref('')
const code = ref('')
const submitting = ref(false)
const sendingCode = ref(false)
const cooldown = ref(0)
const verificationOpen = ref(false)
let verificationTarget: { mode: 'email' | 'phone'; address: string } | undefined
const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
let countdownTimer: ReturnType<typeof setInterval> | undefined

const codeMode = computed(() => mode.value !== 'password')
const alternativeModes = computed(() => modes.filter((item) => item.value !== mode.value))

onMounted(() => {
  // 兼容已打开的旧跳转链接；消费一次提示标记，刷新页面不会再次显示保存成功。
  if (route.query.passwordUpdated !== '1') return
  ElMessage.success('密码已保存，请重新登录')
  const query = { ...route.query }
  delete query.passwordUpdated
  void router.replace({ name: 'login', query, hash: route.hash })
})

function selectMode(next: LoginMode) {
  if (mode.value === next || sendingCode.value) return
  mode.value = next
  code.value = ''
  cooldown.value = 0
  verificationOpen.value = false
  if (countdownTimer) clearInterval(countdownTimer)
}

function startCooldown() {
  cooldown.value = 60
  if (countdownTimer) clearInterval(countdownTimer)
  countdownTimer = setInterval(() => {
    cooldown.value -= 1
    if (cooldown.value <= 0 && countdownTimer) clearInterval(countdownTimer)
  }, 1_000)
}

function openVerification() {
  if (mode.value === 'email' && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email.value.trim())) {
    ElMessage.warning('请输入有效的邮箱地址')
    return
  }
  if (mode.value === 'phone' && !/^1[3-9]\d{9}$/.test(phone.value.trim())) {
    ElMessage.warning('请输入有效的中国大陆手机号')
    return
  }
  if (!codeMode.value || sendingCode.value || cooldown.value > 0) return
  verificationTarget = mode.value === 'email'
    ? { mode: 'email', address: email.value.trim().toLowerCase() }
    : { mode: 'phone', address: phone.value.trim() }
  verificationOpen.value = true
}

async function sendCode(captchaId: string) {
  const target = verificationTarget
  if (!target || sendingCode.value || cooldown.value > 0) return
  verificationOpen.value = false
  sendingCode.value = true
  try {
    if (target.mode === 'email') await authApi.sendEmailCode(target.address, captchaId)
    else await authApi.sendSmsCode(target.address, captchaId)
    startCooldown()
    ElMessage.success('验证码已发送，请注意查收')
  } catch (error) {
    ElMessage.error(axios.isAxiosError(error) ? '网络连接失败，请稍后重试' :
      error instanceof Error ? error.message : '发送失败，请稍后重试')
  } finally {
    sendingCode.value = false
  }
}

async function submit() {
  if (submitting.value) return
  if (mode.value === 'password' && (!identifier.value.trim() || !password.value)) {
    ElMessage.warning('请输入邮箱或手机号以及密码')
    return
  }
  if (mode.value === 'email' && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email.value.trim())) {
    ElMessage.warning('请输入有效的邮箱地址')
    return
  }
  if (mode.value === 'phone' && !/^1[3-9]\d{9}$/.test(phone.value.trim())) {
    ElMessage.warning('请输入有效的中国大陆手机号')
    return
  }
  if (codeMode.value && !/^\d{6}$/.test(code.value.trim())) {
    ElMessage.warning('请输入六位数字验证码')
    return
  }
  submitting.value = true
  try {
    const tokens = mode.value === 'password'
      ? await authApi.passwordLogin(identifier.value.trim(), password.value)
      : mode.value === 'email'
        ? await authApi.emailLogin(email.value.trim().toLowerCase(), code.value.trim())
        : await authApi.smsLogin(phone.value.trim(), code.value.trim())
    auth.accept(tokens)
    const redirect = route.query.redirect
    await router.replace(typeof redirect === 'string' && redirect.startsWith('/')
      && !redirect.startsWith('//') && redirect !== '/login' ? redirect : '/profile')
  } catch (error) {
    ElMessage.error(axios.isAxiosError(error) ? '网络连接失败，请稍后重试' :
      error instanceof Error ? error.message : '登录失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

onUnmounted(() => { if (countdownTimer) clearInterval(countdownTimer) })
</script>


<template>
  <ElContainer direction="vertical">
    <ElMain>
      <ElRow justify="center" align="middle" :style="{ minHeight: 'calc(100dvh - 40px)' }">
        <ElCol :span="24">
          <ElRow justify="center">
          <ElCard shadow="never" :body-style="{ padding: '0' }" :style="{ width: '100%', maxWidth: '1200px' }">
            <ElRow align="middle">
              <ElCol :xs="24" :md="12">
                <ElRow justify="center">
                <ElCol :xs="22" :sm="20" :md="20">
                <ElContainer direction="vertical" :style="{ padding: '24px 0' }">
                <AGrid :cols="1" :row-gap="12">
                  <ElSpace aria-label="Codekin"><ElAvatar :src="brandMark" :size="36" shape="square" fit="contain" alt="Codekin Logo" /><AImage :src="brandWordmark" :width="124" :height="42" fit="contain" :preview="false" alt="codekin" /></ElSpace>
                  <ATypographyTitle :heading="4">欢迎回来</ATypographyTitle>
                  <AGrid :cols="{ xs: 1, sm: 2 }" :col-gap="12" :row-gap="12">
                    <ElContainer v-for="item in alternativeModes" :key="item.value" direction="vertical">
                      <ElButton size="large" :disabled="sendingCode || submitting" @click="selectMode(item.value)">
                        <ElSpace :size="6"><ElIcon><KeyRound v-if="item.value === 'password'" /><Mail v-else-if="item.value === 'email'" /><Smartphone v-else /></ElIcon>{{ item.label }}</ElSpace>
                      </ElButton>
                    </ElContainer>
                  </AGrid>
                  <ElDivider>或</ElDivider>
                  <ElForm label-position="top" :disabled="submitting || sendingCode" @submit.prevent="submit">
                    <template v-if="mode === 'password'">
                      <ElFormItem label="邮箱或手机号"><ElInput v-model="identifier" size="large" clearable autocomplete="username" placeholder="请输入邮箱或手机号"><template #prefix><ElIcon><UserRound /></ElIcon></template></ElInput></ElFormItem>
                      <ElFormItem label="密码"><ElInput v-model="password" type="password" size="large" autocomplete="current-password" show-password placeholder="请输入密码"><template #prefix><ElIcon><KeyRound /></ElIcon></template></ElInput></ElFormItem>
                    </template>
                    <template v-else>
                      <ElFormItem :label="mode === 'email' ? '邮箱地址' : '手机号码'">
                        <ElInput v-if="mode === 'email'" v-model="email" size="large" clearable type="email" autocomplete="email" placeholder="请输入邮箱地址"><template #prefix><ElIcon><Mail /></ElIcon></template></ElInput>
                        <ElInput v-else v-model="phone" size="large" clearable inputmode="numeric" autocomplete="tel" maxlength="11" placeholder="请输入手机号"><template #prepend>+86</template></ElInput>
                      </ElFormItem>
                      <ElFormItem label="验证码">
                        <ElCol :span="24">
                          <AGrid :cols="12" :col-gap="12">
                            <AGridItem :span="7"><ElInput v-model="code" size="large" inputmode="numeric" maxlength="6" autocomplete="one-time-code" placeholder="六位验证码" /></AGridItem>
                            <AGridItem :span="5"><ElContainer direction="vertical"><ElButton size="large" :loading="sendingCode" :disabled="cooldown > 0" @click="openVerification">{{ cooldown > 0 ? `${cooldown}s 后重发` : '获取验证码' }}</ElButton></ElContainer></AGridItem>
                          </AGrid>
                        </ElCol>
                      </ElFormItem>
                      <ElFormItem><ElText type="info" size="small">首次使用验证码登录时，会自动创建账号。</ElText></ElFormItem>
                    </template>
                    <ElFormItem><ElCol :span="24"><ElContainer direction="vertical"><ElButton native-type="submit" type="primary" size="large" :loading="submitting">{{ submitting ? '正在登录' : '登录并继续' }}</ElButton></ElContainer></ElCol></ElFormItem>
                  </ElForm>
                  <ElText type="info" size="small">注册或登录即代表您已阅读并同意<ElLink href="/terms" target="_blank" rel="noopener noreferrer" type="primary">《用户协议》</ElLink>，并已阅读<ElLink href="/privacy" target="_blank" rel="noopener noreferrer" type="primary">《隐私政策》</ElLink>。</ElText>
                </AGrid>
                </ElContainer>
                </ElCol>
                </ElRow>
              </ElCol>
              <ElCol :xs="0" :md="12"><AImage :src="loginIllustration" width="100%" height="680px" fit="cover" :preview="false" alt="吉祥物伸手迎向写有 OFFER 的信封" /></ElCol>
            </ElRow>
          </ElCard>
          </ElRow>
        </ElCol>
      </ElRow>
    </ElMain>
    <CaptchaDialog v-model="verificationOpen" @verified="sendCode" />
  </ElContainer>
</template>
