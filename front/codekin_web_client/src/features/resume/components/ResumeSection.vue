<script setup lang="ts">
import { computed } from 'vue'
import type { ResumeSection, ResumeItem } from '../types'
import { dateRange, safeResumeImage, safeResumeLink } from '../rendering'
import ResumeRichText from './ResumeRichText'
import ResumeFields from './ResumeFields.vue'
const props = defineProps<{ section: ResumeSection; assets: Record<string, string> }>()
const items = computed(() => props.section.items.filter(item => item.visible !== false))
function customFields(item: ResumeItem) {
  return [...(item.customFields ?? []), ...(props.section.fieldDefinitions ?? []).map(field => ({ ...field, value: item.values?.[field.id] }))]
}
</script>

<template>
  <section v-if="section.visible" class="resume-section" :data-section-id="section.id" :data-section-type="section.type">
    <h2 class="resume-section-title"><span>{{ section.title }}</span></h2>
    <ResumeRichText v-if="section.content" :value="section.content" />
    <ResumeFields :fields="section.customFields" />
    <div class="resume-items" :class="{ 'has-timeline': items.length > 2 }">
      <article v-for="item in items" :key="item.id" class="resume-item" :data-item-id="item.id">
        <div class="resume-item-heading">
          <h3 v-if="item.company || item.school || item.name || item.title">{{ item.company || item.school || item.name || item.title }}</h3>
          <span v-if="dateRange(item)" class="resume-date">{{ dateRange(item) }}</span>
        </div>
        <p v-if="item.position || item.role || item.major || item.degree || item.subtitle || item.gpa" class="resume-subtitle">{{ [item.position || item.role || item.major || item.subtitle, item.degree, item.gpa ? `GPA ${item.gpa}` : undefined].filter(Boolean).join(' · ') }}</p>
        <a v-if="safeResumeLink(item.link)" class="resume-project-link" :href="safeResumeLink(item.link)" target="_blank" rel="noopener noreferrer">{{ item.linkLabel || item.link }}</a>
        <ResumeRichText v-if="item.description" :value="item.description" />
        <ResumeFields :fields="customFields(item)" />
        <img v-if="item.fileId && safeResumeImage(assets[item.fileId])" class="resume-certificate" :src="safeResumeImage(assets[item.fileId])" :alt="item.title || '证书图片'" :style="{ width: `${Math.min(100, Math.max(10, item.imageWidthPercent ?? 50))}%` }" />
      </article>
    </div>
  </section>
</template>
