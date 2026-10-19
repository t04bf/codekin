<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElButton, ElCard, ElCol, ElContainer, ElDialog, ElEmpty, ElInput, ElMain, ElRow, ElSpace, ElTag, ElText } from 'element-plus'
import AppHeader from '@/components/AppHeader.vue'
import ResumePreview from '@/features/resume/components/ResumePreview.vue'
import { createPreviewResumeContent, resumeTemplates } from '@/features/resume/templates'
import type { ResumeTemplate } from '@/features/resume/types'
const query = ref('')
const selected = ref<ResumeTemplate>()
const dialogVisible = ref(false)
const content = createPreviewResumeContent()
const filtered = computed(() => resumeTemplates.filter(template => `${template.name} ${template.description}`.toLowerCase().includes(query.value.trim().toLowerCase())))
function preview(template: ResumeTemplate) { selected.value = template; dialogVisible.value = true }
</script>

<template>
  <ElContainer direction="vertical">
    <AppHeader />
    <ElMain>
      <ElRow justify="center">
        <ElCol :xs="24" :sm="23" :lg="21" :xl="18">
          <ElSpace direction="vertical" alignment="stretch" :size="24" fill>
            <ElSpace wrap :size="16">
              <ElText tag="h1" size="large">简历模板</ElText>
              <ElTag effect="plain">{{ resumeTemplates.length }} 套模板</ElTag>
              <ElText type="info">选择喜欢的版式，预览排版效果。预览内容为虚构示例。</ElText>
            </ElSpace>
            <ElInput v-model="query" placeholder="搜索模板名称或风格" aria-label="搜索简历模板" clearable />
            <ElEmpty v-if="!filtered.length" description="没有匹配的模板" />
            <ElRow v-else :gutter="24">
              <ElCol v-for="template in filtered" :key="`${template.templateKey}@${template.templateVersion}`" :xs="24" :sm="12" :lg="8">
                <ElCard shadow="hover" class="template-card" :body-style="{ padding: '0' }">
                  <ResumePreview :template="template" :content="content" thumbnail />
                  <template #footer>
                    <ElSpace direction="vertical" alignment="stretch" fill :size="12">
                      <ElSpace><ElText tag="strong">{{ template.name }}</ElText><ElTag size="small" type="info">v{{ template.templateVersion }}</ElTag></ElSpace>
                      <ElText type="info" size="small">{{ template.description }}</ElText>
                      <ElButton :aria-label="`预览${template.name}`" @click="preview(template)">预览模板</ElButton>
                    </ElSpace>
                  </template>
                </ElCard>
              </ElCol>
            </ElRow>
          </ElSpace>
        </ElCol>
      </ElRow>
    </ElMain>
    <ElDialog v-model="dialogVisible" :title="selected?.name" width="min(900px, 96vw)" align-center destroy-on-close>
      <ResumePreview v-if="selected" :template="selected" :content="content" />
      <template #footer><ElButton @click="dialogVisible = false">关闭预览</ElButton></template>
    </ElDialog>
  </ElContainer>
</template>

<style scoped>
.template-card { margin-bottom: 24px; }
</style>
