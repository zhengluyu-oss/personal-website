<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { experienceOverview, type ExperienceOverview } from '@/apis/experience'
import { moduleForPath, SITE_MODULES, type SiteModuleKey } from '@/utils/site-module-access'
import { GET_TOKEN } from '@/utils/auth'

const route = useRoute()
const router = useRouter()
const unavailable = computed(() => route.query.unavailable === '1')
const signedIn = computed(() => Boolean(GET_TOKEN()))
const target = computed(() => {
  const value = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
  return value.startsWith('/') && !value.startsWith('//') && !/[\\\u0000-\u001f]/.test(value) && moduleForPath(value.split(/[?#]/)[0]) ? value : '/'
})
const moduleKey = computed<SiteModuleKey>(() => {
  const requested = route.query.module
  return typeof requested === 'string' && SITE_MODULES.includes(requested as SiteModuleKey)
    ? requested as SiteModuleKey
    : moduleForPath(target.value.split(/[?#]/)[0]) || 'home'
})
const names: Record<SiteModuleKey, string> = {
  home: '首页', experience: '工作经历', blog: '个人博客',
  shares: '网站分享', photos: '相册', about: '关于我', diary: '我的日记',
}
const moduleName = computed(() => names[moduleKey.value])
const overview = ref<ExperienceOverview | null>(null)
const overviewFailed = ref(false)
watch(moduleKey, async (key, _previous, onCleanup) => {
  overview.value = null
  overviewFailed.value = false
  if (key !== 'experience') return
  let active = true
  onCleanup(() => { active = false })
  try {
    const response: any = await experienceOverview()
    const data = response.data
    if (response.code !== 200 || !Number.isInteger(data?.total) || data.total < 0
      || !Array.isArray(data.projectCounts) || data.projectCounts.length !== data.total
      || data.projectCounts.some((count: unknown) => !Number.isInteger(count) || (count as number) < 0)) throw new Error('Invalid overview')
    if (active) overview.value = data
  } catch { if (active) overviewFailed.value = true }
}, { immediate: true })
const retry = () => router.replace(target.value)
</script>

<template>
  <main class="access-preview" :class="`access-preview--${moduleKey}`">
    <section v-if="moduleKey === 'home'" class="home-hero" aria-labelledby="access-title">
      <div class="hero-picture" aria-hidden="true" />
      <div class="hero-shade" aria-hidden="true" />
      <div class="home-hero__inner">
        <p class="eyebrow">PERSONAL JOURNAL · 首页预览</p>
        <div class="hero-lines" aria-hidden="true"><span /><span /><span /></div>
        <div class="access-message access-message--hero" role="status">
          <h1 id="access-title">{{ unavailable ? '暂时无法确认访问权限' : '首页内容暂未开放' }}</h1>
          <p>{{ unavailable ? '权限服务暂不可用，请稍后重新检查。' : signedIn ? '当前账号尚未获准查看首页内容。' : '登录获授权的账号，即可查看完整首页。' }}</p>
          <div class="access-actions">
            <RouterLink v-if="!signedIn" to="/auth/login">登录账号</RouterLink>
            <button type="button" @click="retry">重新检查权限</button>
          </div>
        </div>
      </div>
    </section>

    <div v-if="moduleKey === 'home'" class="home-outline page-shell" aria-hidden="true">
      <div class="overview-row"><span /><span /><span /></div>
      <section class="outline-section"><div class="outline-heading"><span /><i /></div><div class="outline-card outline-card--work"><span /><div><i /><i /><i /></div></div></section>
      <section class="outline-section"><div class="outline-heading"><span /><i /></div><div class="outline-card outline-card--featured"><span /><div><i /><i /><i /></div></div><div class="outline-grid"><div v-for="index in 2" :key="index" class="outline-tile"><i /><i /></div></div></section>
    </div>

    <div v-else class="page-shell module-preview">
      <header class="module-heading">
        <p class="eyebrow">{{ moduleName }} · 页面预览</p>
        <h1 id="access-title">{{ moduleName }}</h1>
        <p>页面布局已保留，具体内容仅向获授权的账号开放。</p>
      </header>
      <div class="access-message" role="status">
        <h2>{{ unavailable ? '暂时无法确认访问权限' : '暂无查看权限' }}</h2>
        <p>{{ unavailable ? '权限服务暂不可用，请稍后重试。' : signedIn ? '当前账号尚未获准查看此模块，请联系管理员分配角色。' : '此模块仅向获授权的角色开放，请先登录。' }}</p>
        <div class="access-actions">
          <RouterLink v-if="!signedIn" to="/auth/login">登录账号</RouterLink>
          <button type="button" @click="retry">重新检查权限</button>
        </div>
      </div>
      <div class="module-outline" :class="`module-outline--${moduleKey}`" :aria-hidden="moduleKey !== 'experience'">
        <template v-if="moduleKey === 'experience'">
          <div class="count-heading"><span>经历目录</span><strong>{{ overview ? `${overview.total} 段经历` : overviewFailed ? '数量暂不可用' : '正在读取数量' }}</strong></div>
          <ol v-if="overview?.projectCounts.length" class="count-list">
            <li v-for="(count, index) in overview.projectCounts" :key="index"><span>{{ String(index + 1).padStart(2, '0') }}</span><b>第 {{ index + 1 }} 段经历<small>详情需授权</small></b><strong>{{ count }} <small>个项目</small></strong></li>
          </ol>
          <p v-else-if="overview" class="count-empty">目前还没有已发布的经历。</p>
          <p v-else-if="overviewFailed" class="count-empty">数量暂时无法读取。</p>
          <div v-else class="outline-card outline-card--work" aria-hidden="true"><span /><div><i /><i /><i /></div></div>
        </template>
        <template v-else-if="moduleKey === 'blog'">
          <div class="outline-heading"><span /><i /></div>
          <div class="outline-card outline-card--featured"><span /><div><i /><i /><i /></div></div>
          <div class="outline-grid"><div v-for="index in 3" :key="index" class="outline-tile"><span /><i /><i /></div></div>
        </template>
        <template v-else-if="moduleKey === 'shares'">
          <div v-for="index in 2" :key="index" class="outline-card outline-card--share"><span /><div><i /><i /><i /></div></div>
        </template>
        <template v-else-if="moduleKey === 'photos'">
          <div class="outline-gallery"><aside><i v-for="index in 5" :key="index" /></aside><div><span v-for="index in 6" :key="index" /></div></div>
        </template>
        <template v-else>
          <div class="outline-card outline-card--about"><span /><div><i /><i /><i /><i /></div></div>
        </template>
      </div>
    </div>
  </main>
</template>

<style scoped>
.access-preview{min-height:100svh;background:var(--brand-canvas);color:var(--brand-ink)}
.page-shell{width:min(calc(100% - 4rem),76rem);margin:auto}
.eyebrow{margin:0;color:var(--brand-accent-strong);font-family:"Share TechMono",Consolas,monospace;font-size:.8rem;font-weight:700;letter-spacing:.12em}
.access-message{position:relative;z-index:1;max-width:38rem;padding:1.5rem 1.75rem;border:1px solid var(--brand-line);border-radius:var(--brand-radius-md);background:var(--brand-surface)}
.access-message h1,.access-message h2{margin:0;font-size:clamp(1.35rem,2.5vw,1.9rem);line-height:1.35}
.access-message p{margin:.65rem 0 0;color:var(--brand-ink-soft);line-height:1.75}
.access-actions{display:flex;flex-wrap:wrap;gap:.75rem;margin-top:1.2rem}
.access-actions a,.access-actions button{display:inline-flex;align-items:center;justify-content:center;min-height:2.7rem;padding:.55rem 1rem;border:1px solid var(--brand-line);border-radius:var(--brand-radius-sm);background:var(--brand-surface);color:var(--brand-accent-strong);font:inherit;font-weight:600;text-decoration:none;cursor:pointer}
.access-actions a{border-color:var(--brand-accent-strong);background:var(--brand-accent-strong);color:#fff}
.access-actions :is(a,button):focus-visible{outline:2px solid var(--brand-accent);outline-offset:3px}
.home-hero{position:relative;display:flex;align-items:center;min-height:100svh;padding:6rem 0 3rem;overflow:hidden;isolation:isolate;background:#10243b;color:#fff}
.hero-picture,.hero-shade{position:absolute;inset:0;z-index:-2}
.hero-picture{background:#1a344d url('@/assets/images/home-access-placeholder.svg') center/cover no-repeat}
.hero-shade{z-index:-1;background:linear-gradient(90deg,rgba(4,16,33,.83),rgba(7,22,39,.5) 55%,rgba(7,22,39,.2))}
.home-hero__inner{width:min(calc(100% - 4rem),76rem);margin:auto}.home-hero .eyebrow{color:#c7e0ff}
.hero-lines{display:grid;gap:1rem;margin-top:1.8rem}.hero-lines span{display:block;width:min(48vw,28rem);height:.95rem;border-radius:.25rem;background:rgba(255,255,255,.22)}
.hero-lines span:first-child{width:min(40vw,23rem);height:clamp(3rem,6vw,5rem)}.hero-lines span:last-child{width:min(35vw,18rem)}
.access-message--hero{margin-top:2rem;border-color:rgba(255,255,255,.24);background:rgba(4,16,33,.78);color:#fff;backdrop-filter:blur(8px)}
.access-message--hero p{color:#dbe8f7}.access-message--hero .access-actions button{background:transparent;color:#fff;border-color:rgba(255,255,255,.6)}
.overview-row{display:flex;gap:2rem;padding:1.5rem 0;border-bottom:1px solid var(--brand-line)}.overview-row span{width:8rem;height:1.4rem;background:var(--brand-canvas-soft);border-radius:.25rem}.overview-row span:last-child{margin-left:auto}
.outline-section{padding:2.5rem 0;border-bottom:1px solid var(--brand-line)}.outline-heading{display:flex;justify-content:space-between;align-items:center;margin-bottom:1.5rem}
.outline-heading span{width:10rem;height:2rem;background:var(--brand-canvas-soft);border-radius:.25rem}.outline-heading i{width:7rem;height:1rem;background:var(--brand-canvas-soft);border-radius:.25rem}
.outline-card{display:grid;grid-template-columns:1fr 1.3fr;gap:2rem;min-height:12rem;margin-bottom:1.25rem;padding:1.7rem;border:1px solid var(--brand-line);border-radius:var(--brand-radius-md);background:var(--brand-surface)}
.outline-card>span{display:block;min-height:8rem;border-radius:var(--brand-radius-sm);background:var(--brand-canvas-soft)}
.outline-card>div{display:flex;flex-direction:column;gap:1rem;justify-content:center}
.outline-card i,.outline-tile i{display:block;width:90%;height:1rem;border-radius:.25rem;background:var(--brand-canvas-soft)}
.outline-card i:first-child{width:65%;height:1.7rem}.outline-card i:last-child{width:75%}
.outline-card--featured{grid-template-columns:1.2fr 1fr;min-height:20rem}.outline-card--featured>span{min-height:16rem}
.outline-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:1.25rem}.outline-tile{display:flex;flex-direction:column;gap:1rem;padding:1rem;border:1px solid var(--brand-line);border-radius:var(--brand-radius-md);background:var(--brand-surface)}
.outline-tile span{display:block;aspect-ratio:16/10;border-radius:var(--brand-radius-sm);background:var(--brand-canvas-soft)}.outline-tile i:last-child{width:60%}
.module-preview{padding-top:8.5rem;padding-bottom:5rem}.module-heading{padding-bottom:2rem;border-bottom:1px solid var(--brand-line)}
.module-heading h1{margin:.65rem 0;font-size:clamp(2.5rem,5vw,4.25rem);line-height:1.1;letter-spacing:-.04em}.module-heading>p:last-child{margin:0;color:var(--brand-ink-soft);line-height:1.7}
.module-preview>.access-message{margin:2rem 0}.module-outline{padding-top:1.5rem}
.count-heading{display:flex;align-items:baseline;justify-content:space-between;gap:1rem;padding-bottom:1rem;border-bottom:1px solid var(--brand-line)}
.count-heading span{color:var(--brand-accent-strong);font-size:.8rem;font-weight:700;letter-spacing:.12em}.count-heading strong{font-size:1.25rem}
.count-list{margin:0;padding:0;list-style:none}.count-list li{display:grid;grid-template-columns:3.5rem minmax(0,1fr) auto;align-items:center;gap:1rem;min-height:5.75rem;border-bottom:1px solid var(--brand-line)}
.count-list li>span{color:var(--brand-accent-strong);font-family:Consolas,monospace;font-size:1.2rem}.count-list b{font-weight:650}.count-list b small{display:block;margin-top:.3rem;color:var(--brand-ink-soft);font-size:.8rem;font-weight:400}.count-list strong{color:var(--brand-accent-strong);white-space:nowrap}.count-list strong small{color:var(--brand-ink-soft);font-size:.8rem;font-weight:400}.count-empty{padding:1.5rem 0;color:var(--brand-ink-soft)}
.outline-card--work{grid-template-columns:minmax(8rem,.35fr) 1fr}.outline-card--share{grid-template-columns:1fr 1fr;border:0;border-bottom:1px solid var(--brand-line);border-radius:0;background:transparent}
.outline-card--about{grid-template-columns:minmax(12rem,.65fr) 1.35fr;min-height:24rem}.outline-card--about>span{aspect-ratio:1;max-height:20rem}
.outline-gallery{display:grid;grid-template-columns:14rem 1fr;gap:1.5rem}.outline-gallery aside{display:grid;align-content:start;gap:1rem;padding:1.5rem;border:1px solid var(--brand-line);border-radius:var(--brand-radius-md);background:var(--brand-surface)}
.outline-gallery aside i{height:1.25rem;border-radius:.25rem;background:var(--brand-canvas-soft)}.outline-gallery>div{display:grid;grid-template-columns:repeat(3,1fr);gap:1rem}
.outline-gallery>div span{aspect-ratio:4/3;border-radius:var(--brand-radius-sm);background:var(--brand-canvas-soft)}
@media(max-width:767px){.page-shell,.home-hero__inner{width:calc(100% - 2rem)}.home-hero{min-height:calc(100svh - 4rem);padding:6rem 0 2rem}.hero-shade{background:linear-gradient(0deg,rgba(4,16,33,.82),rgba(7,22,39,.4))}.hero-lines span:first-child{width:65%;height:3rem}.hero-lines span:nth-child(2){width:80%}.hero-lines span:last-child{width:55%}.access-message{padding:1.25rem}.overview-row{gap:.75rem}.overview-row span{width:28%}.module-preview{padding-top:6.5rem}.outline-card,.outline-gallery{grid-template-columns:1fr}.outline-card--featured>span{min-height:10rem}.outline-grid{grid-template-columns:1fr 1fr}.outline-gallery aside{display:none}.outline-gallery>div{grid-template-columns:1fr 1fr}}
@media(max-width:390px){.outline-grid{grid-template-columns:1fr}}
</style>
