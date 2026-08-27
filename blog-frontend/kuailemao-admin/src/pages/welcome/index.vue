<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { articleList } from '~/api/blog/article'
import { commentList } from '~/api/blog/comment'
import { leaveMessageList } from '~/api/blog/leave-word'
import { experienceList } from '~/api/blog/experience'
import { ADMIN_PATHS } from '~/router/paths'

const router = useRouter()
const loading = ref(true)
const stats = ref({
  articles: 0,
  comments: 0,
  messages: 0,
  experiences: 0,
})

const shortcuts = [
  { title: '发布文章', desc: '撰写并发布新内容', path: ADMIN_PATHS.articleNew },
  { title: '文章列表', desc: '管理已发布与草稿', path: ADMIN_PATHS.articles },
  { title: '评论管理', desc: '审核与回复评论', path: ADMIN_PATHS.comments },
  { title: '留言管理', desc: '处理访客留言', path: ADMIN_PATHS.messages },
  { title: '工作经历', desc: '维护求职作品集', path: ADMIN_PATHS.experiences },
  { title: '站点信息', desc: '站长与网站配置', path: ADMIN_PATHS.siteInfo },
]

onMounted(async () => {
  try {
    const [articles, comments, messages, experiences] = await Promise.all([
      articleList().catch(() => null),
      commentList().catch(() => null),
      leaveMessageList().catch(() => null),
      experienceList().catch(() => null),
    ])
    stats.value = {
      articles: Number(articles?.data?.total || 0),
      comments: Array.isArray(comments?.data) ? comments.data.length : 0,
      messages: Array.isArray(messages?.data) ? messages.data.length : 0,
      experiences: Array.isArray(experiences?.data) ? experiences.data.length : 0,
    }
  }
  finally {
    loading.value = false
  }
})
</script>

<template>
  <page-container>
    <template #content>
      <div class="workspace">
        <header class="workspace__header">
          <div>
            <h1>工作台</h1>
            <p>高密度内容运营入口。默认登录后进入文章列表，本页供快捷跳转与概览。</p>
          </div>
        </header>

        <section class="workspace__stats" aria-label="内容概览">
          <div class="stat">
            <span>文章</span>
            <strong>{{ loading ? '—' : stats.articles }}</strong>
          </div>
          <div class="stat">
            <span>评论</span>
            <strong>{{ loading ? '—' : stats.comments }}</strong>
          </div>
          <div class="stat">
            <span>留言</span>
            <strong>{{ loading ? '—' : stats.messages }}</strong>
          </div>
          <div class="stat">
            <span>经历</span>
            <strong>{{ loading ? '—' : stats.experiences }}</strong>
          </div>
        </section>

        <section class="workspace__shortcuts" aria-label="快捷入口">
          <button
            v-for="item in shortcuts"
            :key="item.path"
            type="button"
            class="shortcut"
            @click="router.push(item.path)"
          >
            <h2>{{ item.title }}</h2>
            <p>{{ item.desc }}</p>
          </button>
        </section>
      </div>
    </template>
  </page-container>
</template>

<style scoped lang="scss">
.workspace {
  --accent: #d35f45;
  display: grid;
  gap: 1rem;
}

.workspace__header {
  h1 {
    margin: 0;
    font-size: 1.35rem;
    font-weight: 700;
    letter-spacing: -0.03em;
    color: #162033;
  }

  p {
    margin: 0.35rem 0 0;
    color: #647089;
    font-size: 0.85rem;
    line-height: 1.5;
  }
}

.workspace__stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0.65rem;
}

.stat {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 0.5rem;
  padding: 0.7rem 0.85rem;
  border: 1px solid #e5eaf2;
  border-radius: 8px;
  background: #fff;

  span {
    color: #647089;
    font-size: 0.75rem;
  }

  strong {
    color: #162033;
    font-size: 1.25rem;
    font-weight: 700;
    font-variant-numeric: tabular-nums;
  }
}

.workspace__shortcuts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.65rem;
}

.shortcut {
  display: grid;
  gap: 0.25rem;
  padding: 0.85rem 0.95rem;
  border: 1px solid #e5eaf2;
  border-radius: 8px;
  background: #fff;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.15s ease, background 0.15s ease;

  h2 {
    margin: 0;
    font-size: 0.92rem;
    font-weight: 650;
    color: #162033;
  }

  p {
    margin: 0;
    color: #647089;
    font-size: 0.75rem;
    line-height: 1.45;
  }

  &:hover {
    border-color: var(--accent);
    background: rgba(211, 95, 69, 0.06);
  }

  &:active {
    transform: translateY(1px);
  }
}

@media (max-width: 960px) {
  .workspace__stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .workspace__shortcuts {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .workspace__stats,
  .workspace__shortcuts {
    grid-template-columns: 1fr;
  }
}
</style>
