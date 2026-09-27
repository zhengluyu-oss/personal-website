<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import useWebsiteStore from '@/store/modules/website.ts'
import { ABOUT_BIO, ABOUT_HEADLINE, ABOUT_TAGLINE, GITHUB_REPO_URL, GITHUB_URL, PUBLIC_CONTACT_LINKS, PUBLIC_RESUME_URL, SITE_AUTHOR } from '@/config/site'
import { publicContacts, publicProfileUrl } from '@/utils/public-profile'

const website = useWebsiteStore()
const contacts = publicContacts(PUBLIC_CONTACT_LINKS)
const resume = publicProfileUrl(PUBLIC_RESUME_URL, 'resume')
const github = computed(() => publicProfileUrl(website.webInfo?.githubLink || '') || GITHUB_URL)
const name = computed(() => website.webInfo?.webmasterName?.trim() || SITE_AUTHOR)
const avatarFailed = ref(false)
watch(() => website.webInfo?.webmasterAvatar, () => { avatarFailed.value = false })
</script>

<template>
  <main class="about-page">
    <div class="about-shell">
      <header class="about-heading"><p>关于我</p><h1>{{ name }}</h1><p class="direction">{{ ABOUT_HEADLINE }}</p></header>
      <div class="about-layout">
        <div class="portrait">
          <img v-if="website.webInfo?.webmasterAvatar && !avatarFailed" :src="website.webInfo.webmasterAvatar" :alt="name + '的头像'" @error="avatarFailed = true">
          <span v-else class="portrait-fallback" aria-hidden="true">{{ name.slice(0, 1) }}</span>
        </div>
        <div class="about-body">
          <section aria-labelledby="intro-heading"><h2 id="intro-heading">{{ ABOUT_TAGLINE }}</h2><p class="bio">{{ ABOUT_BIO }}</p></section>
          <section class="profile-links" aria-labelledby="links-heading">
            <h2 id="links-heading">在这里继续了解我</h2>
            <nav aria-label="个人公开链接">
              <a :href="github" target="_blank" rel="noopener noreferrer">GitHub 个人主页 <span aria-hidden="true">↗</span></a>
              <RouterLink to="/experience">工作经历 <span aria-hidden="true">→</span></RouterLink>
              <RouterLink to="/blog">阅读博客 <span aria-hidden="true">→</span></RouterLink>
              <a v-for="contact in contacts" :key="contact.href" :href="contact.href" :target="contact.href.startsWith('https:') ? '_blank' : undefined" rel="noopener noreferrer">{{ contact.label }} <span aria-hidden="true">↗</span></a>
              <a v-if="resume" :href="resume" target="_blank" rel="noopener noreferrer">查看简历 <span aria-hidden="true">↗</span></a>
            </nav>
            <a class="source-link" :href="GITHUB_REPO_URL" target="_blank" rel="noopener noreferrer">本站源码仓库 <span aria-hidden="true">↗</span></a>
          </section>
        </div>
      </div>
    </div>
  </main>
</template>

<style scoped lang="scss">
.about-page { min-height: 100dvh; padding: 7rem 0 4rem; background: var(--brand-canvas); color: var(--brand-ink); }
.about-shell { width: min(calc(100% - 4rem), 68rem); margin: auto; }
.about-heading { padding-bottom: 2.5rem; border-bottom: 1px solid var(--brand-line); }
.about-heading > p { margin: 0; color: var(--brand-accent-strong); }
.about-heading h1 { margin: .65rem 0 1rem; font-size: clamp(2.8rem, 5vw, 4.5rem); line-height: 1.15; letter-spacing: -.04em; overflow-wrap: anywhere; }
.about-heading .direction { color: var(--brand-ink-soft); font-size: 1.1rem; }
.about-layout { display: grid; grid-template-columns: minmax(0, .65fr) minmax(0, 1.35fr); gap: clamp(2rem, 6vw, 5rem); padding-top: 3rem; align-items: start; }
.portrait { aspect-ratio: 1; overflow: hidden; border-radius: var(--brand-radius-md); background: var(--brand-accent-soft); }
.portrait img { width: 100%; height: 100%; object-fit: contain; display: block; }
.portrait-fallback { display: grid; height: 100%; place-items: center; color: var(--brand-accent-strong); font-size: 4rem; }
.about-body { min-width: 0; }
.about-body h2 { margin: 0; font-size: clamp(1.25rem, 2vw, 1.55rem); font-weight: 650; line-height: 1.65; overflow-wrap: anywhere; }
.bio { margin: 1.25rem 0 0; color: var(--brand-ink-soft); font-size: 1.05rem; line-height: 1.95; white-space: pre-line; overflow-wrap: anywhere; }
.profile-links { margin-top: 2.5rem; padding-top: 2rem; border-top: 1px solid var(--brand-line); }
.profile-links h2 { font-size: 1rem; }
.profile-links nav { display: flex; flex-wrap: wrap; gap: .75rem 1.5rem; margin: 1rem 0; }
.profile-links a { display: inline-flex; align-items: center; gap: .6rem; min-height: 44px; color: var(--brand-accent-strong); text-decoration: none; overflow-wrap: anywhere; }
.profile-links a:hover { text-decoration: underline; text-underline-offset: .3em; }
.profile-links a:focus-visible { outline: 2px solid var(--brand-accent); outline-offset: 4px; }
.profile-links .source-link { color: var(--brand-ink-soft); font-size: .9rem; }
@media (max-width: 767px) { .about-page { padding-top: 6rem; } .about-shell { width: calc(100% - 2rem); } .about-layout { grid-template-columns: 1fr; gap: 2rem; padding-top: 2rem; } .portrait { width: min(100%, 14rem); } .about-heading { padding-bottom: 1.75rem; } }
</style>
