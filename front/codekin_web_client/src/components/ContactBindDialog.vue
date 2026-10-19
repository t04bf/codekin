<script setup lang="ts">
import '@arco-design/web-vue/es/grid/style/css.js'
import { Grid as AGrid } from '@arco-design/web-vue'
import { computed, reactive, ref, watch } from 'vue'
import { useTimestamp } from '@vueuse/core'
import { ElButton, ElCol, ElDialog, ElForm, ElFormItem, ElInput, ElMessage, ElRow, ElText, type FormInstance, type FormRules } from 'element-plus'
import { userApi } from '@/api/private'
import { maskEmail, maskPhone } from '@/utils/contact-display'

const visible = defineModel<boolean>({ required: true })
const props = defineProps<{ kind: 'phone' | 'email'; currentValue: string | null | undefined }>()
const emit = defineEmits<{ bound: [kind: 'phone' | 'email', address: string] }>()
const formRef = ref<FormInstance>()
const form = reactive({ address: '', code: '' })
const sending = ref(false)
const saving = ref(false)
const now = useTimestamp({ interval: 1000 })
// 冷却按收件人区分，关闭再打开弹窗也不会重置；实际额度仍以服务端校验为准。
const cooldownUntil = reactive(new Map<string, number>())
const address = computed(() => props.kind === 'email' ? form.address.trim().toLowerCase() : form.address.trim())
const targetKey = computed(() => `${props.kind}:${address.value}`)
const cooldown = computed(() => Math.max(0, Math.ceil(((cooldownUntil.get(targetKey.value) || 0) - now.value) / 1000)))
const busy = computed(() => sending.value || saving.value)
const contactLabel = computed(() => props.kind === 'phone' ? '手机号' : '邮箱')
const title = computed(() => `${props.currentValue ? '换绑' : '绑定'}${contactLabel.value}`)
const maskedCurrent = computed(() => props.kind === 'phone' ? maskPhone(props.currentValue) : maskEmail(props.currentValue))
const rules = computed<FormRules>(() => ({
  address: [
    { required: true, whitespace: true, message: `请输入${contactLabel.value}`, trigger: 'blur' },
    ...(props.kind === 'phone'
      ? [{ pattern: /^1[3-9]\d{9}$/, message: '请输入有效的中国大陆手机号', trigger: 'blur' }]
      : [{ type: 'email' as const, message: '请输入有效的邮箱地址', trigger: 'blur' }, { max: 254, message: '邮箱地址过长', trigger: 'blur' }]),
  ],
  code: [{ required: true, message: '请输入验证码', trigger: 'blur' }, { pattern: /^\d{6}$/, message: '验证码应为六位数字', trigger: 'blur' }],
}))

watch(visible, (open) => {
  if (!open) return
  form.address = ''
  form.code = ''
})
watch(address, () => {
  // 更换发送目标后清空旧验证码，防止将上一个目标的验证码带入绑定请求。
  form.code = ''
})

async function sendCode() {
  if (busy.value || cooldown.value > 0 || !await formRef.value?.validateField('address').catch(() => false)) return
  // 异步表单校验期间可能再次点击，校验后再次检查，避免并发发送。
  if (busy.value || cooldown.value > 0) return
  const target = address.value
  form.address = target
  sending.value = true
  try {
    if (props.kind === 'phone') await userApi.sendBindPhoneCode(target)
    else await userApi.sendBindEmailCode(target)
    cooldownUntil.set(`${props.kind}:${target}`, Date.now() + 60_000)
    ElMessage.success('验证码已发送，请注意查收')
  } catch (cause) {
    ElMessage.error(cause instanceof Error ? cause.message : '验证码发送失败，请稍后重试')
  } finally {
    sending.value = false
  }
}

async function bindContact() {
  if (busy.value || !await formRef.value?.validate().catch(() => false)) return
  if (busy.value) return
  const target = address.value
  const kind = props.kind
  const action = props.currentValue ? '换绑' : '绑定'
  saving.value = true
  try {
    if (kind === 'phone') await userApi.bindPhone(target, form.code)
    else await userApi.bindEmail(target, form.code)
    emit('bound', kind, target)
    visible.value = false
    ElMessage.success(`${contactLabel.value}${action}成功`)
  } catch (cause) {
    ElMessage.error(cause instanceof Error ? cause.message : '绑定失败，请稍后重试')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <ElDialog v-model="visible" :title="title" width="min(500px, 94vw)"
    align-center destroy-on-close :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy">
    <AGrid :cols="1" :row-gap="20">
    <ElText type="info">{{ currentValue ? `当前绑定：${maskedCurrent}。请验证新的${contactLabel}。` : `验证码将发送到你填写的${contactLabel}，验证后即可绑定。` }}</ElText>
    <ElForm ref="formRef" :model="form" :rules="rules" label-position="top" :disabled="busy" @submit.prevent="bindContact">
      <ElFormItem :label="`${currentValue ? '新' : ''}${contactLabel}`" prop="address">
        <ElInput v-model.trim="form.address" :maxlength="kind === 'phone' ? 11 : 254" :inputmode="kind === 'phone' ? 'tel' : 'email'"
          :placeholder="kind === 'phone' ? '请输入 11 位手机号' : '请输入邮箱地址'" autocomplete="off">
          <template v-if="kind === 'phone'" #prepend>+86</template>
        </ElInput>
      </ElFormItem>
      <ElFormItem label="验证码" prop="code">
        <ElRow :gutter="12">
          <ElCol :span="14">
          <ElInput v-model.trim="form.code" maxlength="6" inputmode="numeric" autocomplete="one-time-code" placeholder="6 位数字验证码" />
          </ElCol><ElCol :span="10"><ElButton :disabled="busy || cooldown > 0" :loading="sending" @click="sendCode">{{ cooldown > 0 ? `${cooldown} 秒后重试` : '获取验证码' }}</ElButton></ElCol>
        </ElRow>
      </ElFormItem>
    </ElForm>
    </AGrid>
    <template #footer><ElButton :disabled="busy" @click="visible = false">取消</ElButton><ElButton type="primary" :loading="saving" :disabled="sending" @click="bindContact">{{ currentValue ? '确认换绑' : '确认绑定' }}</ElButton></template>
  </ElDialog>
</template>
