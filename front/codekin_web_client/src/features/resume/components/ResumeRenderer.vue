<script setup lang="ts">
// Vue migration of Magic Resume's nine template layouts, 2026-10-05.
// Changed: shared schema v1, safe rich text, scoped CSS, no editor/store dependency.
// License and attribution: public/licenses/magic-resume.txt / THIRD_PARTY_NOTICES.md.
import { computed } from 'vue'
import type { ResumeContent, ResumeLayout } from '../types'
import { getResumeTemplate } from '../templates'
import { arrangeSections } from '../rendering'
import ResumeBasic from './ResumeBasic.vue'
import ResumeSection from './ResumeSection.vue'

const props = withDefaults(defineProps<{
  templateKey: string
  templateVersion: number
  content: ResumeContent
  layout?: ResumeLayout
  assets?: Record<string, string>
}>(), { assets: () => ({}) })
const template = computed(() => getResumeTemplate(props.templateKey, props.templateVersion))
const layout = computed(() => props.layout ?? template.value.defaultLayout)
const columns = computed(() => arrangeSections(props.content, layout.value))
const sections = computed(() => [...columns.value.sidebar, ...columns.value.main])
const style = computed(() => ({
  '--resume-theme': layout.value.themeColor,
  '--resume-text': layout.value.textColor,
  '--resume-muted': layout.value.secondaryColor,
  '--resume-background': layout.value.backgroundColor,
  '--resume-font': layout.value.fontKey === 'serif' ? '"Songti SC", "SimSun", serif' : '"PingFang SC", "Microsoft YaHei", sans-serif',
  '--resume-size': `${layout.value.baseFontSize}px`,
  '--resume-line-height': layout.value.lineHeight,
  '--resume-padding': `${layout.value.pagePadding}px`,
  '--resume-section-gap': `${layout.value.sectionSpacing}px`,
  '--resume-item-gap': `${layout.value.paragraphSpacing}px`,
  '--resume-title-gap': `${layout.value.sectionTitleSpacing}px`,
  '--resume-heading-size': `${layout.value.headerSize}px`,
  '--resume-subheading-size': `${layout.value.subheaderSize}px`,
}))
</script>

<template>
  <article class="resume-document" :class="`template-${template.templateKey}`" :style="style" :data-template="`${template.templateKey}@${template.templateVersion}`" :aria-label="`${template.name}简历预览`">
    <div v-if="template.templateKey === 'modern'" class="resume-two-columns">
      <aside class="resume-sidebar">
        <ResumeBasic :basic="content.basic" :layout="layout" :assets="assets" />
        <ResumeSection v-for="section in columns.sidebar" :key="section.id" :section="section" :assets="assets" />
      </aside>
      <div class="resume-main">
        <ResumeSection v-for="section in columns.main" :key="section.id" :section="section" :assets="assets" />
      </div>
    </div>
    <template v-else>
      <ResumeBasic :basic="content.basic" :layout="layout" :assets="assets" />
      <div class="resume-main">
        <ResumeSection v-for="section in sections" :key="section.id" :section="section" :assets="assets" />
      </div>
    </template>
  </article>
</template>

<style scoped>
/* Styles belong only to the printable document, never the surrounding application. */
.resume-document { width: 210mm; min-height: 297mm; padding: var(--resume-padding); background: var(--resume-background); color: var(--resume-text); font: var(--resume-size)/var(--resume-line-height) var(--resume-font); text-align: left; overflow-wrap: anywhere; }
.resume-document :deep(*) { box-sizing: border-box; }
.resume-document :deep(h1), .resume-document :deep(h2), .resume-document :deep(h3), .resume-document :deep(p) { margin: 0; }
.resume-document :deep(a) { color: inherit; text-decoration: underline; text-underline-offset: 3px; }
.resume-document :deep(.resume-basic) { margin-bottom: max(16px, var(--resume-section-gap)); }
.resume-document :deep(.resume-identity) { display: flex; align-items: center; justify-content: space-between; gap: 20px; }
.resume-document :deep(.resume-name-block) { flex: 1; min-width: 0; }
.resume-document :deep(h1) { font-size: 32px; line-height: 1.25; color: var(--resume-theme); font-weight: 750; }
.resume-document :deep(.resume-job-title) { margin-top: 8px; font-size: var(--resume-subheading-size); }
.resume-document :deep(.resume-photo) { object-fit: cover; flex-shrink: 0; }
.resume-document :deep(.resume-contacts) { display: flex; flex-wrap: wrap; gap: 4px 16px; margin-top: 12px; }
.resume-document :deep(.resume-contact) { min-width: 0; max-width: 100%; }
.resume-document :deep(.resume-contact-label) { color: var(--resume-muted); }
.resume-document :deep(.align-center) { text-align: center; }
.resume-document :deep(.align-center .resume-identity) { flex-direction: column-reverse; }
.resume-document :deep(.align-center .resume-contacts), .resume-document :deep(.align-center .resume-fields) { justify-content: center; }
.resume-document :deep(.align-right) { text-align: right; }
.resume-document :deep(.align-right .resume-identity) { flex-direction: row-reverse; }
.resume-document :deep(.align-right .resume-contacts), .resume-document :deep(.align-right .resume-fields) { justify-content: flex-end; }
.resume-document :deep(.resume-section) { margin-top: var(--resume-section-gap); }
.resume-document :deep(.resume-section-title) { font-size: var(--resume-heading-size); color: var(--resume-theme); margin-bottom: var(--resume-title-gap); border-bottom: 1px solid var(--resume-theme); padding-bottom: 6px; break-after: avoid; }
.resume-document :deep(.resume-item) { margin-top: var(--resume-item-gap); }
.resume-document :deep(.resume-item-heading) { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: baseline; gap: 4px 12px; break-after: avoid; }
.resume-document :deep(.resume-item-heading h3) { font-size: var(--resume-subheading-size); flex: 1; min-width: 0; }
.resume-document :deep(.resume-date) { color: var(--resume-muted); font-size: .9em; }
.resume-document :deep(.resume-subtitle) { color: var(--resume-muted); margin: 3px 0 6px; break-after: avoid; }
.resume-document :deep(.resume-project-link) { display: inline-block; margin-bottom: 4px; }
.resume-document :deep(.resume-rich-text p) { margin: 3px 0; orphans: 2; widows: 2; }
.resume-document :deep(.resume-rich-text ul), .resume-document :deep(.resume-rich-text ol) { padding-left: 20px; margin: 4px 0; }
.resume-document :deep(.resume-rich-text li) { margin: 2px 0; }
.resume-document :deep(.resume-rich-text pre) { white-space: pre-wrap; }
.resume-document :deep(.resume-rich-text blockquote) { margin: 6px 0; border-left: 2px solid var(--resume-theme); padding-left: 12px; }
.resume-document :deep(.resume-fields) { display: flex; flex-wrap: wrap; gap: 4px 16px; margin-top: 6px; }
.resume-document :deep(.resume-field) { display: flex; flex-wrap: wrap; min-width: 0; max-width: 100%; }
.resume-document :deep(.resume-field-label) { font-weight: 600; }
.resume-document :deep(.resume-certificate) { display: block; max-width: 100%; height: auto; margin: 10px 0; break-inside: avoid; }

/* Classic: thin rules and compact, left-aligned information. */
.template-classic :deep(.resume-name-block) { padding: 4px 0; }
/* Modern: one-third colored sidebar with education, two-thirds main body. */
.resume-two-columns { display: grid; grid-template-columns: 1fr 2fr; min-height: calc(297mm - 2 * var(--resume-padding)); }
.resume-sidebar { background: var(--resume-theme); color: #fff; padding: 24px 16px; min-width: 0; }
.resume-two-columns > .resume-main { padding: 16px 20px; min-width: 0; }
.resume-sidebar :deep(h1), .resume-sidebar :deep(.resume-section-title), .resume-sidebar :deep(.resume-contact-label), .resume-sidebar :deep(.resume-date), .resume-sidebar :deep(.resume-subtitle) { color: inherit; border-color: #ffffff70; }
.resume-sidebar :deep(.resume-contacts) { flex-direction: column; }
.resume-sidebar :deep(.resume-fields) { gap: 8px; }
.resume-sidebar :deep(.resume-section) { margin-top: 24px; }
.resume-sidebar :deep(.resume-item-heading) { display: block; }
.resume-sidebar :deep(.resume-photo) { max-width: 100%; }
/* Left-right: colored title strips, not a second sidebar layout. */
.template-left-right :deep(.resume-section-title) { border-bottom: 0; border-left: 3px solid var(--resume-theme); padding: 5px 14px; background: color-mix(in srgb, var(--resume-theme) 10%, white); }
/* Timeline: connected section rail and solid dots. */
.template-timeline > .resume-main { padding-left: 8px; }
.template-timeline :deep(.resume-section) { border-left: 2px solid #e5e7eb; padding: 0 0 20px 22px; position: relative; }
.template-timeline :deep(.resume-section::before) { content: ''; position: absolute; top: 8px; left: -7px; width: 12px; height: 12px; border-radius: 50%; background: var(--resume-theme); }
.template-timeline :deep(.resume-section-title) { border: 0; padding: 0; }
/* Minimalist: generous whitespace, uppercase titles and no dividers. */
.template-minimalist :deep(.resume-basic) { margin-bottom: 36px; }
.template-minimalist :deep(h1) { font-weight: 500; letter-spacing: .12em; }
.template-minimalist :deep(.resume-section-title) { border: 0; letter-spacing: .18em; text-transform: uppercase; }
.template-minimalist :deep(.resume-contact-label) { display: none; }
/* Elegant: centered section captions with fine lines on each side. */
.template-elegant :deep(.resume-section-title) { display: flex; align-items: center; gap: 16px; justify-content: center; border: 0; padding: 0; }
.template-elegant :deep(.resume-section-title::before), .template-elegant :deep(.resume-section-title::after) { content: ''; height: 1px; background: var(--resume-theme); opacity: .3; flex: 1; }
.template-elegant :deep(h1) { letter-spacing: .16em; }
/* Creative: full colored header with rounded bottom corners. */
.template-creative :deep(.resume-basic) { background: var(--resume-theme); color: #fff; padding: 30px 20px; border-radius: 0 0 24px 24px; }
.template-creative :deep(.resume-basic h1), .template-creative :deep(.resume-contact-label) { color: inherit; }
.template-creative > .resume-main { padding: 0 10px; }
.template-creative :deep(.resume-section-title) { border: 0; display: flex; align-items: center; gap: 10px; }
.template-creative :deep(.resume-section-title::before) { content: ''; height: 18px; width: 4px; border-radius: 3px; background: var(--resume-theme); }
/* Editorial: oversized masthead, heavy rule, spaced uppercase captions and optional item rail. */
.template-editorial { padding-top: calc(var(--resume-padding) + 16px); }
.template-editorial :deep(h1) { font-size: 40px; letter-spacing: .1em; line-height: 1.1; }
.template-editorial :deep(.resume-identity) { padding-bottom: 16px; border-bottom: 3px solid var(--resume-theme); }
.template-editorial :deep(.resume-contacts) { margin-top: 24px; justify-content: flex-end; font-size: .9em; color: var(--resume-muted); }
.template-editorial :deep(.resume-section-title) { border: 0; padding: 0; text-transform: uppercase; letter-spacing: .2em; }
.template-editorial :deep(.resume-item-heading) { display: block; }
.template-editorial :deep(.resume-date) { display: block; margin-top: 5px; letter-spacing: .08em; }
.template-editorial :deep(.has-timeline .resume-item) { border-left: 1.5px solid #e5e7eb; padding-left: 20px; position: relative; }
.template-editorial :deep(.has-timeline .resume-item::before) { content: ''; position: absolute; width: 6px; height: 6px; background: var(--resume-theme); border-radius: 50%; top: 8px; left: -4px; }
/* Swiss: geometric accents, bold name, contact capsules and date badges. */
.template-swiss :deep(.resume-name-block) { border-left: 4px solid var(--resume-theme); padding-left: 20px; }
.template-swiss :deep(h1) { font-size: 38px; font-weight: 900; letter-spacing: -.04em; }
.template-swiss :deep(.resume-job-title) { letter-spacing: .12em; font-size: 12px; color: var(--resume-muted); text-transform: uppercase; }
.template-swiss :deep(.resume-contacts) { margin-top: 22px; gap: 8px; }
.template-swiss :deep(.resume-contact) { padding: 5px 10px; background: #f8fafc; border: 1px solid #eef2f6; border-radius: 8px; }
.template-swiss :deep(.resume-contact-label) { display: none; }
.template-swiss :deep(.resume-section-title) { border-color: #e2e8f0; padding-bottom: 8px; font-weight: 900; letter-spacing: .08em; }
.template-swiss :deep(.resume-section-title span) { border-left: 6px solid var(--resume-theme); padding-left: 10px; }
.template-swiss :deep(.resume-date) { background: #f8fafc; border: 1px solid #eef2f6; border-radius: 4px; padding: 2px 7px; font-family: monospace; }
.template-swiss :deep(.resume-item > .resume-rich-text) { border-left: 1.5px solid color-mix(in srgb, var(--resume-theme) 20%, white); padding-left: 14px; margin-top: 8px; }
@media print {
  .resume-document { width: 100%; min-height: 0; print-color-adjust: exact; -webkit-print-color-adjust: exact; }
  .resume-two-columns { min-height: 0; }
  .resume-document :deep(a) { text-decoration: none; }
}
</style>
