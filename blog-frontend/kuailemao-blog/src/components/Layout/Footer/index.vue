<!-- 底部 -->
<script setup lang="ts">
import useWebsiteStore from "@/store/modules/website.ts";

const useWebsite = useWebsiteStore()

</script>
<template>
  <div class="Footer">
    <!-- 文章页脚 -->
    <div class="footer_container">
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
  height: 100%;
  display: flex;
  justify-content: center;
  align-items: center;
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

</style>
