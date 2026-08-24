<script setup lang="ts">
import {
  DocumentCopy,
  Files,
  HomeFilled,
  PictureFilled,
  UserFilled,
} from "@element-plus/icons-vue";
import {useBlogCategories} from "@/composables/useBlogCategories";

const emit = defineEmits("update:closeDrawer")

function isClose(){
  emit("update:closeDrawer")
}

const {categoryEntries, loadCategories} = useBlogCategories()
onMounted(loadCategories)
</script>
<template>
<div>
  <el-menu
      router
      style="width: 100%;border: none"
      class="menu"
  >
    <el-menu-item index="/" class="index" @click="isClose">
      <el-icon>
        <HomeFilled/>
      </el-icon>
      首页
    </el-menu-item>
    <el-menu-item index="/experience" @click="isClose">
      <el-icon>
        <Files/>
      </el-icon>
      工作经历
    </el-menu-item>
    <el-sub-menu index="blog-categories">
      <template #title>
        <el-icon><DocumentCopy/></el-icon>
        个人博客
      </template>
      <el-menu-item index="/blog" @click="isClose">
        <el-icon><DocumentCopy/></el-icon>
        全部栏目
      </el-menu-item>
      <el-menu-item v-for="entry in categoryEntries" :key="entry.categoryId" :index="entry.path" @click="isClose">
        <span class="mobile-category-name">{{ entry.category.categoryName }}</span>
        <small v-if="entry.category.articleCount !== undefined">{{ entry.category.articleCount }}</small>
      </el-menu-item>
    </el-sub-menu>
    <el-menu-item index="/photos" @click="isClose">
      <el-icon>
        <PictureFilled/>
      </el-icon>
      相册
    </el-menu-item>
    <el-menu-item index="/about" @click="isClose">
      <el-icon>
        <UserFilled/>
      </el-icon>
      关于我
    </el-menu-item>
  </el-menu>
</div>
</template>

<style scoped lang="scss">
.mobile-category-name{min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.el-menu-item small{margin-left:auto;color:var(--el-text-color-placeholder)}
</style>
