<script setup lang="ts">
import { h, ref } from 'vue'
import { ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'

defineProps({
  formState: Object,
  isList: {
    type: Boolean,
    default: true,
  },
})

const emit = defineEmits(['update:refreshFunc', 'update:onFinish', 'update:onFinishFailed', 'update:reset'])

// 是否显示搜索
const isShowForm = ref<boolean>(true)

const formRef = ref()

/**
 * 重置表单
 */
function resetForm() {
  formRef.value.resetFields()
  emit('update:reset')
}

/**
 * 刷新方法
 */
function refreshFunc() {
  emit('update:refreshFunc')
}

/**
 * 完成回调
 */
function onFinish(values: any) {
  emit('update:onFinish', values)
}

/**
 * 完成失败回调
 */
function onFinishFailed(errorInfo: any) {
  emit('update:onFinishFailed', errorInfo)
}
</script>

<template>
  <page-container class="admin-list">
    <template #content>
      <template v-if="isShowForm">
        <a-form
          class="admin-list__filters"
          ref="formRef"
          :model="formState"
          layout="inline"
          autocomplete="off"
          @finish="onFinish"
          @finish-failed="onFinishFailed"
        >
          <slot name="form-items" />

          <a-form-item v-if="isList">
            <a-button type="primary" html-type="submit">
              <template #icon>
                <SearchOutlined />
              </template>
              搜索
            </a-button>
            <a-button style="margin-left: 10px" @click="resetForm">
              <template #icon>
                <ReloadOutlined />
              </template>
              重置
            </a-button>
          </a-form-item>
        </a-form>
      </template>
      <div class="middle_btn admin-list__toolbar">
        <div class="admin-list__actions">
          <slot name="operate-btn" />
        </div>
        <div class="admin-list__tools">
          <template v-if="isList">
            <span>
              <a-tooltip title="隐藏搜索" @click="isShowForm = !isShowForm">
                <a-button shape="circle" :icon="h(SearchOutlined)" />
              </a-tooltip>
            </span>
            <span style="margin-left: 10px">
              <a-tooltip title="刷新" @click="refreshFunc">
                <a-button shape="circle" :icon="h(ReloadOutlined)" />
              </a-tooltip>
            </span>
          </template>
        </div>
      </div>
      <div class="admin-list__table">
        <slot name="table-content" />
      </div>
    </template>
  </page-container>
</template>

<style scoped lang="css">
.middle_btn {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 4px;
}

.admin-list__filters {
  padding: 16px 16px 6px;
  margin-bottom: 14px;
  border: 1px solid #e8edf3;
  border-radius: 10px;
  background: #fff;
}

.admin-list__actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.admin-list__tools {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.admin-list__table {
  min-width: 0;
  margin-top: 12px;
  overflow-x: auto;
}

::v-deep(.ant-form-item){
  margin-bottom: 10px;
}

@media (max-width: 640px) {
  .admin-list__filters {
    padding: 12px 12px 2px;
  }

  .admin-list__toolbar,
  .admin-list__actions {
    align-items: stretch;
  }

  .admin-list__actions {
    width: 100%;
  }

  .admin-list__tools {
    margin-left: auto;
  }
}
</style>
