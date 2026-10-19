<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { ElButton, ElCol, ElDialog, ElDivider, ElMessage, ElProgress, ElRow, ElSkeleton, ElSpace, ElText, ElTooltip, ElUpload, type UploadFile } from 'element-plus'
import UserAvatar from '@/components/UserAvatar.vue'
import { fileApi, type UploadTypePolicy } from '@/api/file'
import { userApi, type DefaultAvatar } from '@/api/private'
import { useProfileStore } from '@/stores/profile'

const profile = useProfileStore()
const dialogOpen = ref(false)
const policy = ref<UploadTypePolicy>()
const loadingPolicy = ref(false)
const defaults = ref<DefaultAvatar[]>([])
const loadingDefaults = ref(false)
const defaultsFailed = ref(false)
const selectedKey = ref('')
const selectedFile = ref<File>()
const localPreview = ref('')
const saving = ref(false)
const percentage = ref(0)
const phase = ref('')
const accept = computed(() => policy.value?.extensions.map(extension => `.${extension}`).join(',') || '')
const hint = computed(() => policy.value
  ? `${policy.value.extensions.map(extension => extension.toUpperCase()).join(' / ')}，最大 ${policy.value.maxBytes / 1024 / 1024} MiB`
  : '正在获取上传限制')
const preview = computed(() => localPreview.value || defaults.value.find(item => item.objectKey === selectedKey.value)?.url || profile.me?.avatarUrl)
const canConfirm = computed(() => !!profile.me && (!!selectedFile.value || !!selectedKey.value && selectedKey.value !== profile.me.avatarKey))

function clearLocalImage() {
  if (localPreview.value) URL.revokeObjectURL(localPreview.value)
  localPreview.value = ''
  selectedFile.value = undefined
}
onBeforeUnmount(clearLocalImage)

function openDialog() {
  clearLocalImage()
  selectedKey.value = profile.me?.avatarKey || ''
  dialogOpen.value = true
  // 每次打开重新签名，避免缓存的展示链接已过期；两项加载互不阻塞。
  void loadDefaults()
  if (!policy.value && !loadingPolicy.value) void loadPolicy()
}

async function loadDefaults() {
  loadingDefaults.value = true
  defaultsFailed.value = false
  try {
    defaults.value = await userApi.getDefaultAvatars()
  } catch (cause) {
    defaults.value = []
    defaultsFailed.value = true
    ElMessage.error(cause instanceof Error ? cause.message : '无法获取默认头像')
  } finally {
    loadingDefaults.value = false
  }
}

async function loadPolicy() {
  loadingPolicy.value = true
  try {
    policy.value = (await fileApi.uploadTypes()).find(item => item.code === 'AVATAR')
    if (!policy.value) throw new Error('头像上传暂未开放')
  } catch (cause) {
    ElMessage.error(cause instanceof Error ? cause.message : '无法获取上传限制')
  } finally {
    loadingPolicy.value = false
  }
}

function selectDefault(avatar: DefaultAvatar) {
  clearLocalImage()
  selectedKey.value = avatar.objectKey
}

function selectFile(upload: UploadFile) {
  const file = upload.raw
  const restriction = policy.value
  if (!file || !restriction || saving.value) return
  const extension = file.name.split('.').pop()?.toLowerCase() || ''
  if (!restriction.extensions.includes(extension) || !restriction.contentTypes.includes(file.type)) {
    ElMessage.error('请选择支持的图片格式')
    return
  }
  if (file.size < 1 || file.size > restriction.maxBytes) {
    ElMessage.error(`图片大小必须在 1 字节至 ${restriction.maxBytes / 1024 / 1024} MiB 之间`)
    return
  }
  clearLocalImage()
  selectedKey.value = ''
  selectedFile.value = file
  localPreview.value = URL.createObjectURL(file)
}

async function confirmAvatar() {
  if (!canConfirm.value || saving.value) return
  saving.value = true
  percentage.value = 0
  try {
    if (selectedFile.value) {
      // 本地选择只做预览，确认后才上传，取消不会生成无用途的存储对象。
      phase.value = '获取上传凭证'
      const signature = await fileApi.signature('AVATAR', selectedFile.value)
      phase.value = '上传图片'
      await fileApi.directUpload(signature, selectedFile.value, percent => { percentage.value = percent })
      phase.value = '核验图片并保存头像'
      const file = await fileApi.complete(signature.fileId)
      await userApi.setAvatar(file.fileId)
    } else {
      await userApi.setDefaultAvatar(selectedKey.value)
    }
    await profile.load()
    ElMessage.success('头像已更新')
    dialogOpen.value = false
  } catch (cause) {
    // 存储接口不使用 R<T>；错误 XML、签名 URL 与密钥不展示或输出到日志。
    ElMessage.error(cause instanceof Error && !('isAxiosError' in cause)
      ? cause.message : '头像未更新，请检查网络或稍后重试')
  } finally {
    saving.value = false
    phase.value = ''
  }
}
</script>

<template>
  <ElButton link :disabled="!profile.me" aria-label="更换头像" :style="{ padding: '0', height: 'auto' }" @click="openDialog">
    <UserAvatar :src="profile.me?.avatarUrl" :nickname="profile.me?.nickname" :size="100" />
  </ElButton>
  <ElDialog v-model="dialogOpen" title="选择头像" width="min(560px, 94vw)" align-center
    :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" @closed="clearLocalImage">
    <ElSkeleton :loading="loadingDefaults" :rows="2" animated>
      <ElRow :gutter="12" justify="center" aria-label="默认头像">
        <ElCol v-for="avatar in defaults" :key="avatar.objectKey" :xs="6" :sm="4" :style="{ textAlign: 'center', marginBottom: '16px' }">
          <ElTooltip :content="avatar.name" placement="top">
            <ElButton circle :type="selectedKey === avatar.objectKey ? 'primary' : 'default'" plain
              :aria-label="avatar.name" :aria-pressed="selectedKey === avatar.objectKey" :disabled="saving"
              :style="{ width: '68px', height: '68px', padding: '3px' }" @click="selectDefault(avatar)">
              <UserAvatar :src="avatar.url" :nickname="avatar.name" :size="58" shape="circle" />
            </ElButton>
          </ElTooltip>
        </ElCol>
      </ElRow>
    </ElSkeleton>
    <ElRow v-if="defaultsFailed" justify="center">
      <ElButton text type="primary" :disabled="saving" @click="loadDefaults">重新加载默认头像</ElButton>
    </ElRow>
    <ElDivider />
    <ElRow justify="center">
      <ElSpace direction="vertical" alignment="center" :size="16">
        <UserAvatar v-if="selectedFile" :src="preview" :nickname="profile.me?.nickname" :size="88" shape="circle" />
        <ElUpload :accept="accept" :auto-upload="false" :show-file-list="false" :multiple="false"
          :file-list="[]" :disabled="saving || !policy || !profile.me" :on-change="selectFile">
          <ElButton type="primary" :loading="loadingPolicy" :disabled="saving || !policy || !profile.me">上传头像</ElButton>
        </ElUpload>
        <ElText v-if="policy || loadingPolicy" type="info" size="small">{{ hint }}</ElText>
        <ElSpace v-if="saving && selectedFile" direction="vertical" alignment="center">
          <ElText type="info" size="small">{{ phase }}</ElText>
          <ElProgress :percentage="percentage" :stroke-width="6" :style="{ width: '220px' }" />
        </ElSpace>
        <ElButton v-if="!policy && !loadingPolicy" text type="primary" :disabled="saving" @click="loadPolicy">重新获取上传限制</ElButton>
      </ElSpace>
    </ElRow>
    <template #footer>
      <ElButton :disabled="saving" @click="dialogOpen = false">取消</ElButton>
      <ElButton type="primary" :loading="saving" :disabled="!canConfirm" @click="confirmAvatar">确认</ElButton>
    </template>
  </ElDialog>
</template>
