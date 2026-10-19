<script setup lang="ts">
import { ElAvatar } from 'element-plus'
import mascotAvatar from '@/assets/mascot-avatar.png'
import { computed, ref, watch } from 'vue'

const props = withDefaults(defineProps<{ nickname?: string | null; size?: number; src?: string | null; shape?: 'circle' | 'square' }>(), { size: 80, shape: 'square' })
const imageFailed = ref(false)
const imageSource = computed(() => props.src && !imageFailed.value ? props.src : mascotAvatar)
watch(() => props.src, () => { imageFailed.value = false })
</script>

<template>
  <ElAvatar :key="imageSource" :src="imageSource" :size="size" :shape="shape" fit="contain" :style="{ border: '1px solid var(--ck-line)', padding: '2px', backgroundColor: 'var(--ck-surface)' }"
    :alt="`${nickname || '用户'}的头像`" @error="imageFailed = true" />
</template>
