<script setup lang="ts">
const emit = defineEmits(['navigate'])
</script>
<template>
  <RouterLink to="/diary" @click="emit('navigate')"><slot>我的日记</slot></RouterLink>
</template>
