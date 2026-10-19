<script setup lang="ts">
import '@arco-design/web-vue/es/grid/style/css.js'
import { Grid as AGrid } from '@arco-design/web-vue'
import { reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElAlert, ElButton, ElDialog, ElForm, ElFormItem, ElInput, ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { userApi } from '@/api/private'
import { useAuthStore } from '@/stores/auth'
import { useProfileStore } from '@/stores/profile'

const visible = defineModel<boolean>({ required: true })
const props = defineProps<{ hasPassword: boolean }>()
const router = useRouter()
const auth = useAuthStore()
const profile = useProfileStore()
const formRef = ref<FormInstance>()
const form = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const saving = ref(false)
const rules: FormRules = {
  oldPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, max: 72, message: '密码长度应为 8 至 72 个字符', trigger: 'blur' },
    { validator: (_rule, value: string, callback) => new TextEncoder().encode(value || '').length <= 72 ? callback() : callback(new Error('密码不能超过 72 个 UTF-8 字节')), trigger: 'blur' },
  ],
  confirmPassword: [{ required: true, message: '请再次输入新密码', trigger: 'blur' },
    { validator: (_rule, value, callback) => value === form.newPassword ? callback() : callback(new Error('两次输入的密码不一致')), trigger: 'blur' }],
}

watch(visible, () => {
  Object.assign(form, { oldPassword: '', newPassword: '', confirmPassword: '' })
})

async function savePassword() {
  if (saving.value || !await formRef.value?.validate().catch(() => false)) return
  if (saving.value) return
  saving.value = true
  try {
    await userApi.setPassword(form.newPassword, props.hasPassword ? form.oldPassword : undefined)
    // 后端设置或修改密码会撤销全部旧会话，前端同步清除令牌，不继续刷新旧会话。
    auth.clear()
    profile.clear()
    visible.value = false
    await router.replace({ name: 'login' })
    ElMessage.success('密码已保存，请重新登录')
  } catch (cause) {
    ElMessage.error(cause instanceof Error ? cause.message : '密码保存失败，请稍后重试')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <ElDialog v-model="visible" :title="hasPassword ? '修改密码' : '设置密码'" width="min(500px, 94vw)" align-center
    destroy-on-close :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving">
    <AGrid :cols="1" :row-gap="20">
      <ElAlert type="info" title="保存成功后，所有原有登录会话将失效，请使用新密码重新登录。" :closable="false" show-icon />
      <ElForm ref="formRef" :model="form" :rules="rules" label-position="top" :disabled="saving" @submit.prevent="savePassword">
        <ElFormItem v-if="hasPassword" label="当前密码" prop="oldPassword"><ElInput v-model="form.oldPassword" type="password" show-password autocomplete="current-password" /></ElFormItem>
        <ElFormItem label="新密码" prop="newPassword"><ElInput v-model="form.newPassword" type="password" show-password autocomplete="new-password" maxlength="72" placeholder="至少 8 个字符" /></ElFormItem>
        <ElFormItem label="确认新密码" prop="confirmPassword"><ElInput v-model="form.confirmPassword" type="password" show-password autocomplete="new-password" maxlength="72" /></ElFormItem>
      </ElForm>
    </AGrid>
    <template #footer><ElButton :disabled="saving" @click="visible = false">取消</ElButton><ElButton type="primary" :loading="saving" @click="savePassword">保存并重新登录</ElButton></template>
  </ElDialog>
</template>
