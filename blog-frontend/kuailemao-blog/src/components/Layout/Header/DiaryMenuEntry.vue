<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import useUserStore from '@/store/modules/user'
import { diaryApi } from '@/utils/diary-api'
const user = useUserStore(), router = useRouter(), allowed = ref(false)
let sequence = 0
watch(() => [user.token, user.sessionState, user.userInfo?.username], async () => {
  const current = ++sequence; allowed.value = false
  if (user.sessionState !== 'ready' || !user.token) return
  try { const result = await diaryApi.capabilities(); if (current === sequence) allowed.value = result.allowed && result.available } catch { /* 私密入口不可用不影响公开导航。 */ }
}, { immediate: true })
</script>
<template><el-dropdown-item v-if="allowed" @click="router.push('/diary')">我的日记</el-dropdown-item></template>
