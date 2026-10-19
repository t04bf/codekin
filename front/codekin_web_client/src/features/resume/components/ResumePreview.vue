<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import type { ResumeContent, ResumeTemplate } from '../types'
import ResumeRenderer from './ResumeRenderer.vue'
defineProps<{ template: ResumeTemplate; content: ResumeContent; thumbnail?: boolean }>()
const container = ref<HTMLElement>()
const paper = ref<HTMLElement>()
const width = ref(794)
const height = ref(1123)
const scale = computed(() => Math.min(1, width.value / (210 * 96 / 25.4)))
let observer: ResizeObserver | undefined
onMounted(() => {
  observer = new ResizeObserver(() => {
    width.value = container.value?.clientWidth ?? 794
    height.value = paper.value?.offsetHeight ?? 1123
  })
  if (container.value) observer.observe(container.value)
  if (paper.value) observer.observe(paper.value)
})
onBeforeUnmount(() => observer?.disconnect())
</script>

<template>
  <div ref="container" class="resume-preview" :class="{ thumbnail }" :style="thumbnail ? undefined : { height: `${height * scale}px` }">
    <div ref="paper" class="resume-preview-paper" :style="{ transform: `scale(${scale})` }" :inert="thumbnail || undefined" :aria-hidden="thumbnail || undefined">
      <ResumeRenderer :template-key="template.templateKey" :template-version="template.templateVersion" :content="content" />
    </div>
  </div>
</template>

<style scoped>
.resume-preview { position: relative; width: 100%; max-width: 210mm; margin: 0 auto; background: #fff; overflow: hidden; }
.resume-preview.thumbnail { aspect-ratio: 210 / 297; }
.resume-preview-paper { position: absolute; top: 0; left: 0; width: 210mm; transform-origin: top left; }
</style>
