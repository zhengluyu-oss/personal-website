<script setup lang="ts">
import Card from "@/components/Card/index.vue";

/** 个人站默认不展示；若开启 SHOW_CHARGING，请改为真实打赏记录，勿使用模板假数据 */
const chargingList = ref<
  { name: string; money: string; message: string }[]
>([])
</script>

<template>
  <div>
    <Card v-if="chargingList.length" title="赞赏名单" prefix-icon="money" :isScale="true">
      <div class="charging-list">
        <div v-for="(item, index) in chargingList" :key="index" class="charging-item">
          <div class="name">{{ item.name }}</div>
          <div class="money">￥{{ item.money }}</div>
          <div class="message">{{ item.message }}</div>
        </div>
      </div>
    </Card>
  </div>
</template>

<style scoped lang="scss">
.charging-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.charging-item {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 4px 12px;
  font-size: 13px;
  .message {
    grid-column: 1 / -1;
    color: var(--el-text-color-secondary);
  }
}
</style>
