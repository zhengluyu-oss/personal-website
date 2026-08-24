<!-- 底部 -->
<script setup lang="ts">
import { computed } from 'vue'
import useWebsiteStore from "@/store/modules/website.ts";

const useWebsite = useWebsiteStore()
const env = import.meta.env

const secondaryLinks = [
  { label: '标签', path: '/blog/tags' },
  { label: '时间轴', path: '/blog/archive' },
  { label: '树洞', path: '/tree-hole' },
  { label: '留言板', path: '/messages' },
  { label: '友链', path: '/links' },
]

const visibleLinks = computed(() => {
  const links = [...secondaryLinks]
  if (env.VITE_MUSIC_FRONTEND_URL)
    links.push({ label: '音乐', path: '/music' })
  return links
})
</script>
<template>
  <div class="Footer">
    <div class="footer_container">
      <nav class="footer_links" aria-label="次要导航">
        <router-link
          v-for="(item, index) in visibleLinks"
          :key="item.path"
          :to="item.path"
          class="footer_link"
        >
          {{ item.label }}<span v-if="index < visibleLinks.length - 1" class="footer_sep" aria-hidden="true">·</span>
        </router-link>
      </nav>
      <div class="footer_text">
        <div>&copy;{{ useWebsite.webInfo?.websiteName }}</div>
        <div>备案号：<a href="https://beian.miit.gov.cn">{{ useWebsite.webInfo?.recordInfo }}</a></div>
      </div>
    </div>
  </div>
</template>
<style lang="scss" scoped>
.Footer {
  border-top: 1px solid var(--brand-line);
  width: 100%;
  min-height: 110px;
  background: var(--brand-canvas-soft);
}

.footer_container {
  width: 100%;
  min-height: 110px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: 0.85rem;
  padding: 1.25rem 1rem;
}

.footer_links {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  align-items: center;
  gap: 0.15rem 0;
  max-width: 40rem;
}

.footer_link {
  color: var(--brand-ink-soft);
  font-size: 0.82rem;
  text-decoration: none;
  transition: color 0.2s ease;
}

.footer_link:hover {
  color: var(--brand-accent-strong);
}

.footer_sep {
  margin: 0 0.55rem;
  color: var(--brand-ink-faint);
  pointer-events: none;
}

.footer_text {
  line-height: 20px;
  display: flex;
  justify-content: center;
  align-items: center;
  flex-direction: column;
  gap: .3rem;
  color: var(--brand-ink-soft);
  font-size: .86rem;
  a { color: var(--brand-accent-strong); }
}

@media (max-width: 480px) {
  .footer_links {
    gap: 0.35rem 0.5rem;
  }

  .footer_sep {
    display: none;
  }
}
</style>
