<script setup lang="ts">
import type { CustomField } from '../types'
import { dateRange, safeResumeLink } from '../rendering'
import ResumeRichText from './ResumeRichText'
defineProps<{ fields?: CustomField[] }>()
</script>

<template>
  <div v-if="fields?.some(field => field.visible !== false && field.value)" class="resume-fields">
    <template v-for="field in fields" :key="field.id">
      <div v-if="field.visible !== false && field.value" class="resume-field" :data-field-id="field.id">
        <span v-if="field.showLabel !== false && field.label" class="resume-field-label">{{ field.label }}：</span>
        <ResumeRichText v-if="field.type === 'RICH_TEXT' && typeof field.value === 'object' && 'type' in field.value" :value="field.value" />
        <span v-else-if="field.type === 'DATE_RANGE' && typeof field.value === 'object' && !('type' in field.value)">{{ dateRange(field.value) }}</span>
        <template v-else-if="typeof field.value === 'string'">
          <a v-if="field.type === 'LINK' && safeResumeLink(field.value)" :href="safeResumeLink(field.value)" target="_blank" rel="noopener noreferrer">{{ field.value }}</a>
          <span v-else>{{ field.value }}</span>
        </template>
      </div>
    </template>
  </div>
</template>
