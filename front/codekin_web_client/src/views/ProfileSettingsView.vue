<script setup lang="ts">
import '@arco-design/web-vue/es/grid/style/css.js'
import { computed, onMounted, reactive, ref } from 'vue'
import { ElButton, ElCard, ElCol, ElContainer, ElDatePicker, ElDialog, ElEmpty, ElForm, ElFormItem, ElIcon, ElInput, ElMain, ElMessage, ElOption, ElPageHeader, ElRow, ElSelect, ElSkeleton, ElSpace, ElText, type FormInstance, type FormRules } from 'element-plus'
import { useRouter } from 'vue-router'
import { Grid as AGrid, List as AList, ListItem as AListItem, ListItemMeta as AListItemMeta } from '@arco-design/web-vue'
import '@arco-design/web-vue/es/list/style/css.js'
import { CalendarDays, ChevronRight, IdCard, KeyRound, Mail, Smartphone, UserRound, VenusAndMars } from '@lucide/vue'
import AppHeader from '@/components/AppHeader.vue'
import AvatarUpload from '@/components/AvatarUpload.vue'
import ContactBindDialog from '@/components/ContactBindDialog.vue'
import PasswordSetDialog from '@/components/PasswordSetDialog.vue'
import { maskEmail, maskPhone } from '@/utils/contact-display'
import { userApi, type ProfileUpdate } from '@/api/private'
import { useProfileStore } from '@/stores/profile'

type EditSection = 'basic' | 'gender' | 'birthday' | 'name'
const router = useRouter()
const profile = useProfileStore()
const passwordDialogOpen = ref(false)
const contactDialogOpen = ref(false)
const contactKind = ref<'phone' | 'email'>('phone')
const editing = ref<EditSection>('basic')
const dialogOpen = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<ProfileUpdate>({ nickname: '', bio: null, genderCode: null, birthDate: null, realName: null })
const genderLabels = { MALE: '男', FEMALE: '女', OTHER: '其他' }
const genderLabel = computed(() => genderLabels[profile.me?.genderCode as keyof typeof genderLabels] || '未填写')
const infoRows = computed(() => [
  { section: 'basic' as const, label: '昵称与简介', value: profile.me?.nickname || '未填写', icon: UserRound },
  { section: 'gender' as const, label: '性别', value: genderLabel.value, icon: VenusAndMars },
  { section: 'birthday' as const, label: '生日', value: profile.me?.birthDate || '未填写', icon: CalendarDays },
  { section: 'name' as const, label: '真实姓名', value: profile.me?.realName || '未填写', icon: IdCard },
])
const contactRows = computed(() => [
  { kind: 'email' as const, label: '邮箱', value: maskEmail(profile.me?.email), bound: !!profile.me?.email, icon: Mail },
  { kind: 'phone' as const, label: '手机号', value: maskPhone(profile.me?.phone), bound: !!profile.me?.phone, icon: Smartphone },
])
const titles: Record<EditSection, string> = { basic: '编辑昵称与简介', gender: '编辑性别', birthday: '编辑生日', name: '编辑真实姓名' }
const rules: FormRules = {
  nickname: [{ required: true, whitespace: true, message: '请输入昵称', trigger: 'blur' }, { max: 40, message: '昵称不能超过 40 个字符', trigger: 'blur' }],
  bio: [{ max: 500, message: '简介不能超过 500 个字符', trigger: 'blur' }],
  realName: [{ max: 100, message: '真实姓名不能超过 100 个字符', trigger: 'blur' }],
}

onMounted(() => { void profile.load() })

function openContactEditor(kind: 'phone' | 'email') {
  if (!profile.me) return
  contactKind.value = kind
  contactDialogOpen.value = true
}

async function contactBound(kind: 'phone' | 'email', address: string) {
  if (profile.me) profile.me = { ...profile.me, [kind]: kind === 'phone' ? `+86${address}` : address }
  await profile.load()
}

function openEditor(section: EditSection) {
  const me = profile.me
  if (!me || saving.value) return
  Object.assign(form, { nickname: me.nickname, bio: me.bio, genderCode: me.genderCode, birthDate: me.birthDate, realName: me.realName })
  editing.value = section
  dialogOpen.value = true
}

async function saveProfile() {
  if (saving.value || !await formRef.value?.validate().catch(() => false)) return
  if (saving.value) return
  saving.value = true
  const payload: ProfileUpdate = {
    nickname: form.nickname.trim(), bio: form.bio?.trim() || null, genderCode: form.genderCode || null,
    birthDate: form.birthDate || null, realName: form.realName?.trim() || null,
  }
  try {
    await userApi.updateProfile(payload)
    if (profile.me) profile.me = { ...profile.me, ...payload }
    dialogOpen.value = false
    ElMessage.success('个人资料已保存')
    await profile.load()
  } catch (cause) {
    ElMessage.error(cause instanceof Error ? cause.message : '保存失败，请稍后重试')
  } finally {
    saving.value = false
  }
}

function futureDate(date: Date) {
  const today = new Date()
  today.setHours(23, 59, 59, 999)
  return date.getTime() > today.getTime()
}
</script>


<template>
  <ElContainer direction="vertical">
    <AppHeader />
    <ElMain>
      <ElRow justify="center" :gutter="24">
        <ElCol :xs="24" :sm="22" :md="18" :lg="14">
          <AGrid :cols="1" :row-gap="24">
            <ElPageHeader content="个人资料" title="个人主页" @back="router.push('/profile')" />
            <ElSpace direction="vertical"><AvatarUpload /></ElSpace>
            <ElButton v-if="profile.error" :loading="profile.loading" @click="profile.load">重新加载</ElButton>
            <ElSkeleton v-if="profile.loading && !profile.me" :rows="7" animated />
            <template v-else-if="profile.me">
              <AGrid :cols="1" :row-gap="12">
                <ElText tag="strong" size="large">个人信息</ElText>
                <ElText type="info" size="small">管理个人资料</ElText>
                <AList size="large" hoverable>
                  <AListItem v-for="row in infoRows" :key="row.section" role="button" :tabindex="profile.me ? 0 : -1" :aria-disabled="!profile.me"
                    @click="openEditor(row.section)" @keydown.enter.prevent="openEditor(row.section)" @keydown.space.prevent="openEditor(row.section)">
                    <AListItemMeta>
                      <template #avatar><ElIcon :size="20"><component :is="row.icon" /></ElIcon></template>
                      <template #title><ElSpace wrap :size="16"><ElText tag="strong">{{ row.label }}</ElText><ElText type="info">{{ row.value }}</ElText></ElSpace></template>
                    </AListItemMeta>
                    <template #actions><ElIcon><ChevronRight /></ElIcon></template>
                  </AListItem>
                </AList>
                <ElText type="info" size="small">性别、生日和真实姓名仅本人可见。</ElText>
              </AGrid>
              <AGrid :cols="1" :row-gap="12">
                <ElText tag="strong" size="large">账号与安全</ElText>
                <ElText type="info" size="small">管理登录使用的联系方式和密码</ElText>
                <AList size="large" hoverable>
                  <AListItem v-for="row in contactRows" :key="row.kind" role="button" :tabindex="profile.me ? 0 : -1" :aria-disabled="!profile.me"
                    @click="openContactEditor(row.kind)" @keydown.enter.prevent="openContactEditor(row.kind)" @keydown.space.prevent="openContactEditor(row.kind)">
                    <AListItemMeta>
                      <template #avatar><ElIcon :size="20"><component :is="row.icon" /></ElIcon></template>
                      <template #title><ElText tag="strong">{{ row.label }}</ElText></template>
                    </AListItemMeta>
                    <template #actions><ElSpace :size="16"><ElText type="info">{{ row.value }}</ElText><ElText type="primary">{{ row.bound ? '换绑' : '绑定' }}</ElText><ElIcon><ChevronRight /></ElIcon></ElSpace></template>
                  </AListItem>
                  <AListItem role="button" tabindex="0"
                    @click="passwordDialogOpen = true"
                    @keydown.enter.prevent="passwordDialogOpen = true"
                    @keydown.space.prevent="passwordDialogOpen = true">
                    <AListItemMeta>
                      <template #avatar><ElIcon :size="20"><KeyRound /></ElIcon></template>
                      <template #title><ElText tag="strong">账号密码</ElText></template>
                    </AListItemMeta>
                    <template #actions><ElSpace :size="16"><ElText type="info">{{ profile.me.hasPassword ? '已设置' : '未设置' }}</ElText><ElText type="primary">{{ profile.me?.hasPassword ? '修改密码' : '设置密码' }}</ElText><ElIcon><ChevronRight /></ElIcon></ElSpace></template>
                  </AListItem>
                </AList>
              </AGrid>
              <ElCard shadow="never" header="个人经历"><ElEmpty :image-size="70" description="工作经历、教育经历与技能标签即将开放" /></ElCard>
            </template>
          </AGrid>
        </ElCol>
      </ElRow>
    </ElMain>
    <ContactBindDialog v-model="contactDialogOpen" :kind="contactKind" :current-value="contactKind === 'phone' ? profile.me?.phone : profile.me?.email" @bound="contactBound" />
    <PasswordSetDialog v-if="profile.me" v-model="passwordDialogOpen" :has-password="profile.me.hasPassword" />
    <ElDialog v-model="dialogOpen" :title="titles[editing]" width="min(500px, 94vw)" align-center destroy-on-close :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving">
      <AGrid :cols="1" :row-gap="20">
        <ElText type="info">{{ editing === 'basic' ? '昵称和简介将展示在个人主页。' : '此项为选填信息，仅你自己可见。' }}</ElText>
        <ElForm ref="formRef" :model="form" :rules="rules" label-position="top" :disabled="saving" @submit.prevent="saveProfile">
          <template v-if="editing === 'basic'">
            <ElFormItem label="昵称" prop="nickname"><ElInput v-model="form.nickname" maxlength="40" placeholder="给自己取个昵称" autocomplete="nickname" /></ElFormItem>
            <ElFormItem label="个人简介" prop="bio"><ElInput v-model="form.bio" type="textarea" :rows="5" maxlength="500" show-word-limit placeholder="介绍一下你自己（兴趣、经历等）" /></ElFormItem>
          </template>
          <ElFormItem v-else-if="editing === 'gender'" label="性别" prop="genderCode"><ElSelect v-model="form.genderCode" clearable placeholder="未填写"><ElOption v-for="(label, value) in genderLabels" :key="value" :label="label" :value="value" /></ElSelect></ElFormItem>
          <ElFormItem v-else-if="editing === 'birthday'" label="生日" prop="birthDate"><ElDatePicker v-model="form.birthDate" type="date" value-format="YYYY-MM-DD" format="YYYY-MM-DD" :disabled-date="futureDate" clearable placeholder="选择生日" /></ElFormItem>
          <ElFormItem v-else label="真实姓名" prop="realName"><ElInput v-model="form.realName" maxlength="100" placeholder="选填，不会公开展示" autocomplete="name" /></ElFormItem>
        </ElForm>
      </AGrid>
      <template #footer><ElButton :disabled="saving" @click="dialogOpen = false">取消</ElButton><ElButton type="primary" :loading="saving" @click="saveProfile">保存</ElButton></template>
    </ElDialog>
  </ElContainer>
</template>
