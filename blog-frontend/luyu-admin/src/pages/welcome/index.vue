<script setup lang="ts">
import { onMounted, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { articleList } from '~/api/blog/article'
import { commentList } from '~/api/blog/comment'
import { leaveMessageList } from '~/api/blog/leave-word'
import { experienceList } from '~/api/blog/experience'
import { ADMIN_PATHS } from '~/router/paths'
import { loadOverviewMetric, type OverviewMetric } from './overview'

const router = useRouter()
const metrics = reactive<OverviewMetric[]>([
  { key: 'articles', label: '文章', path: ADMIN_PATHS.articles, status: 'idle', value: null },
  { key: 'comments', label: '评论', path: ADMIN_PATHS.comments, status: 'idle', value: null },
  { key: 'messages', label: '留言', path: ADMIN_PATHS.messages, status: 'idle', value: null },
  { key: 'experiences', label: '经历', path: ADMIN_PATHS.experiences, status: 'idle', value: null },
])
const requests: Record<string, () => Promise<any>> = { articles: () => articleList(), comments: commentList, messages: leaveMessageList, experiences: experienceList }
const shortcuts = [
  { title: '管理文章', desc: '整理已发布内容与草稿', path: ADMIN_PATHS.articles },
  { title: '处理评论', desc: '查看与审核读者评论', path: ADMIN_PATHS.comments },
  { title: '访客留言', desc: '查看读者留下的消息', path: ADMIN_PATHS.messages },
  { title: '工作经历', desc: '维护个人经历与作品', path: ADMIN_PATHS.experiences },
  { title: '站点信息', desc: '更新网站与站长资料', path: ADMIN_PATHS.siteInfo },
]
onMounted(() => Promise.all(metrics.map(metric => loadOverviewMetric(metric, requests[metric.key]))))
</script>

<template>
  <page-container title="工作台">
    <template #extra><a-button type="primary" @click="router.push(ADMIN_PATHS.articleNew)"><PlusOutlined />新建文章</a-button></template>
    <div class="workspace">
      <p class="workspace__intro">从一篇新文章开始，或整理已有内容与读者互动。</p>
      <section class="workspace__stats" aria-label="内容概览">
        <div v-for="metric in metrics" :key="metric.key" class="stat" :aria-busy="metric.status === 'loading'">
          <span>{{ metric.label }}</span>
          <div aria-live="polite">
            <strong v-if="metric.status === 'ready'">{{ metric.value }}</strong>
            <span v-else-if="metric.status === 'error'" class="stat__error">暂不可用</span>
            <span v-else>加载中…</span>
          </div>
          <a-button v-if="metric.status === 'error'" size="small" :aria-label="`重试获取${metric.label}数量`" @click="loadOverviewMetric(metric, requests[metric.key])">重试</a-button>
          <a v-else :href="router.resolve(metric.path).href" @click.prevent="router.push(metric.path)">管理{{ metric.label }} →</a>
        </div>
      </section>
      <section aria-label="快捷入口">
        <h2>常用操作</h2>
        <div class="workspace__shortcuts">
          <a v-for="item in shortcuts" :key="item.path" :href="router.resolve(item.path).href" class="shortcut" @click.prevent="router.push(item.path)">
            <span class="shortcut__title">{{ item.title }} <span aria-hidden="true">↗</span></span>
            <span class="shortcut__description">{{ item.desc }}</span>
          </a>
        </div>
      </section>
    </div>
  </page-container>
</template>

<style scoped lang="scss">
.workspace { display: grid; gap: 24px; }
.workspace__intro { color: var(--workspace-muted); margin: -8px 0 0; line-height: 1.7; }
.workspace__stats { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 16px; }
.stat { display: grid; gap: 16px; padding: 20px; background: var(--workspace-surface); border: 1px solid var(--workspace-line); border-radius: 8px; }
.stat > span { color: var(--workspace-muted); font-size: 13px; }
.stat strong { font-size: 30px; color: var(--workspace-ink); font-weight: 600; font-variant-numeric: tabular-nums; }
.stat a { font-size: 12px; color: var(--workspace-muted); }
.stat a:hover { color: var(--workspace-accent); }
.stat__error { color: var(--workspace-muted); }
h2 { font-size: 16px; font-weight: 600; margin: 0 0 16px; }
.workspace__shortcuts { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
.shortcut { display: grid; gap: 8px; padding: 20px; border: 1px solid var(--workspace-line); border-radius: 8px; background: var(--workspace-surface); color: var(--workspace-ink); transition: border-color .15s; }
.shortcut:hover { border-color: var(--workspace-accent); }
.shortcut__title { display: flex; justify-content: space-between; font-weight: 600; }
.shortcut__title > span { color: var(--workspace-muted); }
.shortcut__description { color: var(--workspace-muted); font-size: 13px; }
@media (max-width: 960px) { .workspace__stats, .workspace__shortcuts { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 480px) { .workspace__shortcuts { grid-template-columns: minmax(0, 1fr); } .stat { padding: 16px; } }
</style>
