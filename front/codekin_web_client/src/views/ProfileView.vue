<script setup lang="ts">
import '@arco-design/web-vue/es/grid/style/css.js'
import { Grid as AGrid } from '@arco-design/web-vue'
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElButton, ElCard, ElCol, ElContainer, ElDivider, ElEmpty, ElMain, ElRow, ElSkeleton, ElSpace, ElTag, ElText } from 'element-plus'
import AppHeader from '@/components/AppHeader.vue'
import UserAvatar from '@/components/UserAvatar.vue'
import { useProfileStore } from '@/stores/profile'

const router = useRouter()
const profile = useProfileStore()
onMounted(() => { void profile.load() })
</script>

<template>
  <ElContainer direction="vertical">
    <AppHeader />
    <ElMain>
      <ElRow :gutter="24" justify="center">
        <ElCol :xs="24" :sm="8" :md="6" :lg="5">
          <ElCard shadow="never">
            <ElSkeleton v-if="profile.loading && !profile.me" :rows="5" animated />
            <AGrid v-else :cols="1" :row-gap="16">
              <ElSpace><UserAvatar :src="profile.me?.avatarUrl" :nickname="profile.me?.nickname" :size="80" /><ElSpace direction="vertical" alignment="start"><ElText tag="strong" size="large">{{ profile.me?.nickname || '我的主页' }}</ElText><ElText type="info" size="small">ID · {{ profile.me?.userId || '—' }}</ElText></ElSpace></ElSpace>
              <ElText type="info">{{ profile.me?.bio || '还没有填写个人简介' }}</ElText>
              <ElButton type="primary" plain @click="router.push('/settings/profile')">编辑个人资料</ElButton>
              <ElButton v-if="profile.error" :loading="profile.loading" @click="profile.load">重新加载</ElButton>
              <ElDivider />
              <ElText tag="strong">基本信息</ElText><ElText type="info">公开主页展示昵称和个人简介。</ElText>
              <ElButton link type="primary" @click="router.push('/settings/profile')">查看我的完整资料</ElButton>
              <ElDivider />
              <ElText tag="strong">成长空间</ElText><ElText type="info">学习与求职记录将在这里汇集。</ElText><ElTag type="info">即将开放</ElTag>
            </AGrid>
          </ElCard>
        </ElCol>
        <ElCol :xs="24" :sm="16" :md="18" :lg="15">
          <AGrid :cols="1" :row-gap="20">
            <ElRow :gutter="20">
              <ElCol :xs="24" :md="12"><ElCard shadow="never" header="练习概览"><ElEmpty :image-size="70" description="练习功能即将开放" /></ElCard></ElCol>
              <ElCol :xs="24" :md="12"><ElCard shadow="never" header="成长成就"><ElEmpty :image-size="70" description="成就功能即将开放" /></ElCard></ElCol>
            </ElRow>
            <ElCard shadow="never" header="学习活跃记录"><ElEmpty :image-size="70" description="练习记录开放后，将展示你的学习轨迹" /></ElCard>
            <ElCard shadow="never" header="最近练习"><ElEmpty :image-size="90" description="题库与练习功能正在准备中"><ElTag type="info">即将开放</ElTag></ElEmpty></ElCard>
          </AGrid>
        </ElCol>
      </ElRow>
    </ElMain>
  </ElContainer>
</template>
