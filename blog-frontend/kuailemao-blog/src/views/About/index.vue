<script setup lang="ts">
import useWebsiteStore from '@/store/modules/website.ts'
import {
  ABOUT_BIO,
  ABOUT_HEADLINE,
  ABOUT_TAGLINE,
  GITHUB_REPO_URL,
  GITHUB_URL,
} from '@/config/site'

const websiteStore = useWebsiteStore()
</script>

<template>
  <div class="flex justify-center items-center h-[100%] w-[99%]">
    <div class="flex justify-center items-center max-lg:flex-col h-full w-full xl:w-[90%]">
      <div class="w-[40%] max-lg:w-full h-full flex flex-col justify-center items-center">
        <div class="h-[20rem] w-[20rem] rounded-full overflow-hidden mt-[5rem] drop-shadow-lg">
          <div
            class="bg-cover bg-no-repeat bg-center w-full h-full"
            :style="{ 'background-image': 'url(' + websiteStore.webInfo?.webmasterAvatar + ')' }"
          />
        </div>
        <div>
          <h1 :data-shadow="websiteStore.webInfo?.webmasterName">
            {{ websiteStore.webInfo?.webmasterName }}
          </h1>
        </div>
        <div class="text-gray-600 w-full font-bold dark:text-gray-300 text-center px-4">
          {{ ABOUT_TAGLINE }}
        </div>
      </div>
      <div class="flex flex-col justify-center items-center xl:w-[40%] lg:w-[60%] h-full">
        <div class="w-full h-[60vh] flex flex-col justify-center items-center px-4">
          <div
            class="w-full text-[2.5rem] text-center text-gray-600 dark:text-gray-300 max-lg:text-[1.75rem] max-lg:pb-6"
          >
            {{ ABOUT_HEADLINE }}
          </div>
          <div class="mt-4 text-center text-gray-500 dark:text-gray-300 max-lg:px-3 leading-relaxed">
            {{ ABOUT_BIO }}
          </div>
        </div>
        <div class="w-full h-[40vh] flex flex-col justify-center items-center gap-6">
          <div class="text-gray-600 dark:text-gray-300">个人导航</div>
          <a :href="GITHUB_URL" target="_blank" rel="noopener noreferrer">
            <div
              class="bg-white dark:bg-slate-800 w-[120px] h-[120px] flex flex-col justify-center items-center rounded-2xl drop-shadow-lg gap-2"
            >
              <SvgIcon name="github_icon" width="72px" height="100px" />
              <span class="text-xs text-gray-500 dark:text-gray-400">GitHub</span>
            </div>
          </a>
          <a
            :href="GITHUB_REPO_URL"
            target="_blank"
            rel="noopener noreferrer"
            class="text-sm text-blue-500 hover:underline"
          >
            本站源码仓库 →
          </a>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
@import url(https://fonts.googleapis.com/css?family=Righteous);

*,
*:before,
*:after {
  box-sizing: border-box;
  position: relative;
}

h1 {
  display: inline-block;
  color: white;
  font-family: 'Righteous', serif;
  font-size: 10em;
  text-shadow: 0.03em 0.03em 0 hsla(230, 40%, 50%, 1);
}
h1:after {
  content: attr(data-shadow);
  position: absolute;
  top: 0.06em;
  left: 0.06em;
  z-index: -1;
  text-shadow: none;
  background-image: linear-gradient(
    45deg,
    transparent 45%,
    hsla(48, 20%, 90%, 1) 45%,
    hsla(48, 20%, 90%, 1) 55%,
    transparent 0
  );
  background-size: 0.05em 0.05em;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;

  animation: shad-anim 15s linear infinite;
}

@keyframes shad-anim {
  0% {
    background-position: 0 0;
  }
  0% {
    background-position: 100% -100%;
  }
}
</style>
