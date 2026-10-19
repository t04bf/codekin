<script setup lang="ts">
import { computed } from 'vue'
import type { ResumeContent, ResumeLayout } from '../types'
import { safeResumeImage, safeResumeLink } from '../rendering'
import ResumeFields from './ResumeFields.vue'
const props = defineProps<{ basic: ResumeContent['basic']; layout: ResumeLayout; assets: Record<string, string> }>()
const labels = { email: '邮箱', phone: '电话', location: '所在地', birthDate: '出生年月', employmentStatus: '求职状态' }
const showName = computed(() => props.basic.fieldOrder.find(field => field.key === 'name')?.visible !== false)
const showTitle = computed(() => props.basic.fieldOrder.find(field => field.key === 'title')?.visible !== false)
const fields = computed(() => props.basic.fieldOrder.filter(field => field.visible && field.key !== 'name' && field.key !== 'title').map(field => ({ ...field, label: labels[field.key as keyof typeof labels], value: props.basic[field.key] })).filter(field => field.value))
const photo = computed(() => props.basic.photoFileId ? safeResumeImage(props.assets[props.basic.photoFileId]) : undefined)
</script>

<template>
  <header v-if="basic.visible" class="resume-basic" :class="`align-${layout.basicAlignment}`">
    <div class="resume-identity">
      <div class="resume-name-block">
        <h1 v-if="showName && basic.name">{{ basic.name }}</h1>
        <p v-if="showTitle && basic.title" class="resume-job-title">{{ basic.title }}</p>
      </div>
      <img v-if="photo && layout.photo.visible" class="resume-photo" :src="photo" :alt="`${basic.name}的简历照片`" :style="{ width: `${layout.photo.width}px`, height: `${layout.photo.height}px`, borderRadius: `${layout.photo.borderRadius}px` }" />
    </div>
    <div class="resume-contacts">
      <span v-for="field in fields" :key="field.key" class="resume-contact">
        <span class="resume-contact-label">{{ field.label }}：</span>
        <a v-if="field.key === 'email' && safeResumeLink(`mailto:${field.value}`)" :href="safeResumeLink(`mailto:${field.value}`)">{{ field.value }}</a>
        <span v-else>{{ field.value }}</span>
      </span>
    </div>
    <ResumeFields :fields="basic.customFields" />
  </header>
</template>
