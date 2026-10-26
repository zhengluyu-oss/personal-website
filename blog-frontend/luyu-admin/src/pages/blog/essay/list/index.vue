<script setup lang="ts">
import type { Ref, UnwrapRef } from 'vue'
import { createVNode } from 'vue'
import { Modal, message } from 'ant-design-vue'
import { ExclamationCircleOutlined } from '@ant-design/icons-vue'
import type { CategoryType, TagType } from '~/pages/blog/essay/publish/type.ts'
import {
  articleCategory,
  articleSearch,
  articleTag,
  deleteArticle,
  updateArticleStatus,
  updateArticleTop,
} from '~/api/blog/article'
import { ARTICLE_PAGE_SIZE_OPTIONS, validArticlePage } from './pagination'

const formData = reactive({
  categoryId: undefined,
  tagId: undefined,
  articleTitle: undefined,
  articleType: undefined,
  isTop: undefined,
  status: undefined,
})

// 分类
const categoryList: Ref<UnwrapRef<CategoryType[]>> = ref([])
// 标签
const tagList: Ref<TagType[]> = ref([])

const skipActivateRefresh = ref(true)

onMounted(async () => {
  await refreshFunc()
  await getCategory()
  await getTag()
  skipActivateRefresh.value = true
})

onActivated(async () => {
  if (skipActivateRefresh.value) {
    skipActivateRefresh.value = false
    return
  }
  await refreshFunc()
})

async function getCategory() {
  const { data } = await articleCategory()
  categoryList.value = data
}
async function getTag() {
  const { data } = await articleTag()
  tagList.value = data
}

type Key = string | number

const state = reactive<{
  selectedRowKeys: Key[]
  loading: boolean
}>({
  selectedRowKeys: [], // Check here to configure the default column
  loading: false,
})

interface DataType {
  id: string
  title: string
  key: string
  orderNum: number
  status: boolean
  createTime: string
}

const loading = ref(false)
const tabData: Ref<UnwrapRef<DataType[]>> = ref([])
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
})

/**
 * 选中表格
 */
function onSelectChange(selectedRowKeys: Key[]) {
  state.selectedRowKeys = selectedRowKeys
}

const isNarrowList = useMediaQuery('(max-width: 767px)')

const showAdvanced = ref(false)
const columns = computed<any[]>(() => [
  { title: '文章', dataIndex: 'articleTitle', width: 360 },
  { title: '分类', dataIndex: 'categoryName', width: 120 },
  { title: '状态', dataIndex: 'status', width: 100 },
  { title: '访问量', dataIndex: 'visitCount', align: 'right', width: 100 },
  { title: '发布时间', dataIndex: 'createTime', width: 130 },
  { title: '操作', dataIndex: 'operation', width: 160, ...(isNarrowList.value ? {} : { fixed: 'right' as const }) },
])

function normalizeRows(rows: any[] = []) {
  return rows.map(item => ({ ...item, isTop: item.isTop === 1 }))
}

async function loadArticles() {
  loading.value = true
  state.selectedRowKeys = []
  try {
    const response = await articleSearch({
      ...formData,
      pageNum: pagination.current,
      pageSize: pagination.pageSize,
    })
    const data = response?.data
    tabData.value = normalizeRows(data?.page)
    pagination.total = Number(data?.total || 0)
    const validPage = validArticlePage(pagination.current, pagination.total, pagination.pageSize)
    if (validPage !== pagination.current) {
      pagination.current = validPage
      await loadArticles()
    }
  }
  finally {
    loading.value = false
  }
}

async function refreshFunc() {
  await loadArticles()
}

async function onFinish() {
  pagination.current = 1
  await loadArticles()
  if (!pagination.total) message.warn('没有查询到相关文章')
}

async function resetSearch() {
  await nextTick()
  pagination.current = 1
  await loadArticles()
}

async function handleTableChange(page: { current?: number, pageSize?: number }) {
  const sizeChanged = page.pageSize !== undefined && page.pageSize !== pagination.pageSize
  pagination.pageSize = page.pageSize || pagination.pageSize
  pagination.current = sizeChanged ? 1 : (page.current || 1)
  await loadArticles()
}

// 修改状态
async function updateArticleStatusFunc(id: string, status: number, record: any) {
  record.statusLoading = true
  try {
    const res = await updateArticleStatus({ id, status })
    if (res?.code === 200) {
      record.status = status
      message.success('状态修改成功')
    }
  }
  finally { record.statusLoading = false }
}

async function updateIsTopFunc(id: string, isTop: boolean, record: any) {
  record.isToploading = true
  try {
    const res = await updateArticleTop({ id, isTop: isTop ? 1 : 0 })
    if (res?.code === 200) {
      record.isTop = isTop
      message.success('置顶设置已更新')
    }
  }
  finally { record.isToploading = false }
}

// 删除文章
function onDelete(ids?: string[]) {
  if (ids) {
    deleteArticle(ids).then((res) => {
      if (res.code === 200) {
        message.success('删除成功')
        loadArticles()
      }
    })
  }
  else {
    // 批量删除
    const ids = state.selectedRowKeys
    Modal.confirm({
      title: '注意',
      icon: createVNode(ExclamationCircleOutlined),
      content: `确定删除编号为 【${ids.join(',')}】 的文章吗？`,
      okText: '确认',
      cancelText: '取消',
      onOk: () => {
        deleteArticle(ids as string[]).then((res) => {
          if (res.code === 200) {
            message.success('删除成功')
            loadArticles()
          }
        })
      },
    })
  }
}

// 前台域名
const domain = import.meta.env.VITE_APP_DOMAIN_NAME_FRONT
</script>

<template>
  <layout title="文章管理" :form-state="formData" @update:refresh-func="refreshFunc" @update:on-finish="onFinish" @update:reset="resetSearch">
    <template #extra>
      <a-button type="primary" @click="$router.push('/articles/new')"><PlusOutlined />新建文章</a-button>
    </template>
    <template #form-items>
      <a-form-item label="标题" name="articleTitle"><a-input v-model:value="formData.articleTitle" allow-clear placeholder="搜索文章标题" style="width: 240px" /></a-form-item>
      <a-form-item label="分类" name="categoryId"><a-select v-model:value="formData.categoryId" allow-clear placeholder="全部分类" style="width: 160px" :options="categoryList.map(item => ({ value: item.id, label: item.categoryName }))" /></a-form-item>
      <a-form-item label="状态" name="status"><a-select v-model:value="formData.status" allow-clear placeholder="全部状态" style="width: 120px" :options="[{value:1,label:'公开'},{value:2,label:'私密'},{value:3,label:'草稿'}]" /></a-form-item>
      <a-form-item><a-button type="text" :aria-expanded="showAdvanced" @click="showAdvanced = !showAdvanced">{{ showAdvanced ? '收起条件' : '更多条件' }}</a-button></a-form-item>
      <a-form-item v-show="showAdvanced" label="置顶" name="isTop"><a-select v-model:value="formData.isTop" allow-clear placeholder="全部文章" style="width: 140px" :options="[{value:1,label:'已置顶'},{value:0,label:'未置顶'}]" /></a-form-item>
    </template>
    <template #operate-btn>
      <span class="selection-note">{{ state.selectedRowKeys.length ? `已选 ${state.selectedRowKeys.length} 篇` : `共 ${pagination.total} 篇文章` }}</span>
      <a-button :disabled="state.selectedRowKeys.length !== 1" @click="$router.push(`/articles/${state.selectedRowKeys[0]}`)">编辑所选</a-button>
      <a-button danger :disabled="!state.selectedRowKeys.length" @click="onDelete()">批量删除</a-button>
    </template>
    <template #table-content>
      <a-table class="article-list-table" :columns="columns" :data-source="tabData" :loading="loading"
        :row-selection="{ selectedRowKeys: state.selectedRowKeys, onChange: onSelectChange }" :row-key="record => record.id"
        :scroll="{ x: 1000 }" :pagination="{current:pagination.current,pageSize:pagination.pageSize,total:pagination.total,showSizeChanger:true,pageSizeOptions:ARTICLE_PAGE_SIZE_OPTIONS,showTotal:(total:number) => `共 ${total} 篇文章`,showQuickJumper:true}" @change="handleTableChange">
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'articleTitle'">
            <div class="article-identity">
              <a-image :src="record.articleCover" :width="64" :height="40" :alt="`${record.articleTitle}的封面`" class="article-cover" />
              <div class="article-identity__text">
                <a class="article-title" :href="$router.resolve(`/articles/${record.id}`).href" @click.prevent="$router.push(`/articles/${record.id}`)">{{ record.articleTitle }}</a>
                <div class="article-meta">{{ record.userName || '未署名' }} · {{ record.articleType === 1 ? '原创' : record.articleType === 2 ? '转载' : '翻译' }}<span v-if="record.isTop" class="article-pinned"> · 已置顶</span></div>
              </div>
            </div>
          </template>
          <template v-else-if="column.dataIndex === 'categoryName'">{{ record.categoryName || '—' }}</template>
          <template v-else-if="column.dataIndex === 'status'"><a-tag :color="record.status === 1 ? 'success' : 'default'">{{ record.status === 1 ? '公开' : record.status === 2 ? '私密' : '草稿' }}</a-tag></template>
          <template v-else-if="column.dataIndex === 'visitCount'"><span class="article-count">{{ record.visitCount ?? 0 }}</span></template>
          <template v-else-if="column.dataIndex === 'createTime'"><a-tooltip :title="record.createTime">{{ record.createTime?.split(' ')[0] || '—' }}</a-tooltip></template>
          <template v-else-if="column.dataIndex === 'operation'">
            <a-space :size="4">
              <a-button type="text" size="small" @click="$router.push(`/articles/${record.id}`)">编辑</a-button>
              <a-popover trigger="click" placement="bottomRight">
                <template #content>
                  <div class="article-more">
                    <span class="article-meta">编号 {{ record.id }} · {{ record.userName || '未署名' }}</span>
                    <div class="article-tags"><a-tag v-for="tag in record.tagsName || []" :key="tag">{{ tag }}</a-tag><span v-if="!record.tagsName?.length">暂无标签</span></div>
                    <label :for="`status-${record.id}`">文章状态</label>
                    <a-select :id="`status-${record.id}`" :value="record.status" :loading="record.statusLoading" :disabled="record.statusLoading" :options="[{value:1,label:'公开'},{value:2,label:'私密'},{value:3,label:'草稿'}]" @change="value => updateArticleStatusFunc(record.id, Number(value), record)" />
                    <div class="article-top-control"><span>置顶文章</span><a-switch :checked="record.isTop" :loading="record.isToploading" aria-label="置顶文章" @change="value => updateIsTopFunc(record.id, Boolean(value), record)" /></div>
                    <a :href="`${domain}article/${record.id}`" target="_blank" rel="noopener noreferrer">查看前台 ↗</a>
                    <a-popconfirm title="确定删除这篇文章吗？" ok-text="确认删除" cancel-text="取消" @confirm="onDelete([record.id])"><a-button danger>删除文章</a-button></a-popconfirm>
                  </div>
                </template>
                <a-button type="text" size="small" :aria-label="`更多操作：${record.articleTitle}`">更多</a-button>
              </a-popover>
            </a-space>
          </template>
        </template>
      </a-table>
    </template>
  </layout>
</template>

<style scoped lang="scss">
.selection-note { color: var(--workspace-muted); font-size: 13px; margin-right: 8px; }
.article-list-table { width: 100%; max-width: 100%; min-width: 0; }
.article-identity { display: flex; gap: 12px; align-items: center; min-width: 0; }
.article-identity__text { min-width: 0; }
.article-title { display: block; font-weight: 600; color: var(--workspace-ink); overflow-wrap: anywhere; line-height: 1.5; }
.article-title:hover { color: var(--workspace-accent); }
.article-meta { color: var(--workspace-muted); font-size: 12px; margin-top: 4px; }
.article-pinned { color: var(--workspace-accent); }
.article-count { font-variant-numeric: tabular-nums; }
.article-more { display: grid; gap: 12px; width: 220px; max-width: calc(100vw - 64px); }
.article-top-control { display: flex; align-items: center; justify-content: space-between; }
.article-tags { display: flex; flex-wrap: wrap; gap: 4px; color: var(--workspace-muted); }
:deep(.article-cover) { object-fit: cover; border-radius: 4px; }
:deep(.article-list-table .ant-table-content), :deep(.article-list-table .ant-table-body) { overflow-x: auto !important; overscroll-behavior-x: contain; touch-action: pan-x pan-y; }
</style>
