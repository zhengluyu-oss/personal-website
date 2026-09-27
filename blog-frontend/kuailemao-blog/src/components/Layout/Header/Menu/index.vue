<script setup lang="ts">

import {
  DocumentCopy,
  Files,
  HomeFilled,
  Link, Setting, Promotion, ArrowDownBold, Close, PictureFilled, UserFilled
} from "@element-plus/icons-vue";
import {logout} from "@/apis/user"
import {REMOVE_TOKEN} from "@/utils/auth.ts";
import useUserStore from "@/store/modules/user.ts"
import router from "@/router";
import useWebsiteStore from "@/store/modules/website.ts";
import SvgIcon from "@/components/SvgIcon/index.vue";
import {computed, ref, watch} from "vue";
import {useRoute} from "vue-router";
import {useBlogCategories} from "@/composables/useBlogCategories";

const userStore = useUserStore()
const useWebsite = useWebsiteStore()
const dialogVisible = ref(false)
const {categoryEntries, loadCategories} = useBlogCategories()
const blogCategoryEntries = computed(() => categoryEntries.value.filter(entry => entry.categoryId !== 21))
const route = useRoute()
const categoriesOpen = ref(false)
const categoryToggle = ref<HTMLButtonElement>()
function leaveCategories(event: MouseEvent) {
  if (!(event.currentTarget as HTMLElement).contains(document.activeElement)) categoriesOpen.value = false
}
function blurCategories(event: FocusEvent) {
  if (!(event.currentTarget as HTMLElement).contains(event.relatedTarget as Node | null)) categoriesOpen.value = false
}
const closeCategories = () => { categoriesOpen.value = false; categoryToggle.value?.focus() }
watch(() => route.fullPath, () => { categoriesOpen.value = false })

const logoutSub = () => {
  logout().then((res: any) => {
    if (res.code === 200) {
      REMOVE_TOKEN()
      userStore.userInfo = undefined
      ElMessage.success('退出登录成功')
      router.push('/')
    }
  })
}

onMounted(() => {
  loadCategories()
})
</script>

<template>
  <div class="search_dialog_container">
    <!-- 搜索内容 -->
    <el-dialog
        v-model="dialogVisible"
        :show-close="false"
        :close-on-click-modal="false"
        :lock-scroll="true"
    >
      <template #header>
        <div style="display: flex;justify-content: space-between;align-items: center">
          <span style="font-size: 1.2rem">搜索</span>
          <el-button :icon="Close" style="background: none;font-size: 1.5rem;width: 30px;border: none"
                     @click="dialogVisible = false"/>
        </div>
      </template>
      <Search @isShowSearch="dialogVisible = false"/>
    </el-dialog>
  </div>
  <nav aria-label="主导航">
    <div id="menu-left">
      <div id="menus">
        <span id="blog-info">
          <a href="/">{{ useWebsite.webInfo?.websiteName }}</a>
        </span>
        <div class="menus_items">
          <RouterLink class="menus_item" to="/">
            <span>
              <el-icon>
                <HomeFilled/>
              </el-icon>
              <span>首页</span>
            </span>
          </RouterLink>
          <RouterLink class="menus_item" :class="{ 'is-section': route.path.startsWith('/experience') }" to="/experience">
            <span>
              <el-icon>
                <Files/>
              </el-icon>
              <span>工作经历</span>
            </span>
          </RouterLink>
          <div class="menus_item blog-menu" :class="{ 'is-section': route.path.startsWith('/blog') }" @mouseenter="categoriesOpen = true" @mouseleave="leaveCategories" @keydown.esc.stop.prevent="closeCategories" @focusout="blurCategories">
            <RouterLink to="/blog" class="blog-destination">
              <el-icon><DocumentCopy/></el-icon>
              <span>个人博客</span>
            </RouterLink>
            <button ref="categoryToggle" type="button" class="category-toggle" aria-label="展开博客栏目" :aria-expanded="categoriesOpen" aria-controls="desktop-categories" @click="categoriesOpen = !categoriesOpen"><el-icon class="arrow"><ArrowDownBold/></el-icon></button>
            <ul v-show="categoriesOpen" id="desktop-categories" class="menus_item_child blog-menu-child">
              <li class="all-categories">
                <RouterLink to="/blog"><el-icon><DocumentCopy/></el-icon><span>全部栏目</span></RouterLink>
              </li>
              <li v-for="entry in blogCategoryEntries" :key="entry.categoryId">
                <RouterLink :to="entry.path" class="category-link"><span>{{ entry.category.categoryName }}</span><small v-if="entry.category.articleCount !== undefined">{{ entry.category.articleCount }}</small></RouterLink>
              </li>
            </ul>
          </div>
          <RouterLink class="menus_item" :class="{ 'is-section': route.path.startsWith('/website-shares') }" to="/website-shares">
            <span><el-icon><Link/></el-icon><span>网站分享</span></span>
          </RouterLink>
          <RouterLink class="menus_item" to="/photos">
            <span>
              <el-icon>
                <PictureFilled/>
              </el-icon>
              <span>相册</span>
            </span>
          </RouterLink>
          <RouterLink class="menus_item" to="/about">
            <span>
              <el-icon>
                <UserFilled/>
              </el-icon>
              <span>关于我</span>
            </span>
          </RouterLink>
        </div>
      </div>
    </div>
    <div id="menu-right">
      <div id="search-button">
        <!-- 搜索按钮 -->
        <button type="button" aria-label="搜索" class="search" @click="dialogVisible = true">
          <SvgIcon name="search" width="30" height="30" color="#409EFF" class="icon"/>
        </button>
      </div>
      <div class="user-info">
        <div v-if="!userStore.userInfo">
          <el-tooltip
              class="box-item"
              effect="light"
              content="点击去登录"
              placement="right"
          >
            <RouterLink to="/auth/login" aria-label="登录"><el-avatar style="margin-right: 1rem">登录</el-avatar></RouterLink>
          </el-tooltip>
        </div>
        <div v-else style="display: flex">
          <div class="profile">
            <div style="font-size: 15px;font-weight: bold;color: black">{{ userStore.userInfo?.username }}</div>
            <div style="font-size: 14px;color: #363636;margin-top: 3px"
                 v-if="userStore.userInfo?.registerType === 0">{{ userStore.userInfo?.email }}
            </div>
            <div style="font-size: 14px;color: #363636;margin-top: 3px" v-else>
              {{ userStore.userInfo?.registerType === 1 ? 'Gitee登录' : 'Github登录' }}
            </div>
          </div>
          <el-dropdown>
            <el-avatar style="margin-right: 3rem"
                       :src="userStore.userInfo?.avatar"></el-avatar>
            <template #dropdown>
              <el-dropdown-item @click="router.push('/account')">
                <template #default>
                  <el-icon>
                    <Setting/>
                  </el-icon>
                  个人设置
                </template>
              </el-dropdown-item>
              <!--                  <el-dropdown-item>-->
              <!--                    <template #default>-->
              <!--                      <el-icon>-->
              <!--                        <Collection/>-->
              <!--                      </el-icon>-->
              <!--                      我的收藏-->
              <!--                    </template>-->
              <!--                  </el-dropdown-item>-->
              <el-dropdown-item @click="logoutSub">
                <template #default>
                  <el-icon>
                    <Promotion/>
                  </el-icon>
                  退出登录
                </template>
              </el-dropdown-item>
            </template>
          </el-dropdown>
        </div>
      </div>
    </div>
  </nav>
</template>

<style scoped lang="scss">
nav {
  position: fixed;
  top: 0;
  display: flex;
  height: 64px;
  width: 100%;
  z-index: 999;
  border-bottom: 1px solid rgba(255,255,255,.09);
  background: rgba(14, 19, 28, .82);
  color: #f3f5f8;
  backdrop-filter: blur(18px) saturate(125%);
  -webkit-backdrop-filter: blur(18px) saturate(125%);

  #menu-left {
    flex: 1 1 auto;
    min-width: 0;

    #menus {
      display: flex;
      justify-content: flex-start;
      align-items: center;
      height: 100%;
      width: 100%;
      font-weight: bold;
      gap: 0.25rem;

      #blog-info {
        flex: 0 0 auto;
        width: auto;
        max-width: min(260px, 26vw);
        margin: 0 12px 0 16px;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        font-size: clamp(0.95rem, 1.05vw + 0.55rem, 1.15rem);
        line-height: 64px;
        text-shadow: 0 1px 2px rgba(0, 0, 0, 0.55);

        a {
          display: inline-block;
          max-width: 100%;
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
          vertical-align: middle;
          color: inherit;
          text-shadow: 0 1px 2px rgba(0, 0, 0, 0.55);
        }

        @media screen and (max-width: 1100px) {
          max-width: min(200px, 22vw);
          font-size: 0.95rem;
        }
      }

      .menus_items {
        flex: 1 1 auto;
        width: auto;
        min-width: 0;
        height: 100%;
        display: flex;
        justify-content: space-evenly;
        align-items: center;
        gap: clamp(0.5rem, 2.2vw, 2.75rem);
        padding: 0 clamp(0.5rem, 1.5vw, 1.5rem);

        .menus_item > span { transition: color .2s ease, transform .2s ease; }
        .menus_item:hover > span { color: #ffad99; transform: translateY(-1px); }

        .menus_item {
          position: relative;
          height: 100%;
          width: auto;
          flex: 0 1 auto;
          padding: 0 0.65rem;
          display: flex;
          justify-content: center;
          align-items: center;
          white-space: nowrap;
          text-shadow: 0 1px 2px rgba(0, 0, 0, 0.55);

          span .arrow {
            margin-left: 5px;
            transition: all 0.5s;
            transform: rotate(0deg);
            color: #409EFF;
          }

          &::before {
            content: '';
            position: absolute;
            bottom: 5px;
            left: 0;
            height: 2px;
            width: 0;
            background-color: #409EFF;
            transition: width 0.3s; // 添加过渡效果
          }

          &:hover {
            cursor: pointer;

            span .arrow {
              // 悬浮时旋转180度
              transition: all 0.5s;
              transform: rotate(180deg);
              color: #cc5de8;
            }

            .menus_item_child {
              display: block;
            }

            &::before {
              width: 100%; // 在悬浮时展开进度条
            }
          }

          span {
            display: flex;
            align-items: center;
            justify-content: center;
            span{
              margin-left: 5px;
            }
          }
        }

        .menus_item_child {
          display: none;
          position: absolute;
          top: 50px;
          left: 50%;
          z-index: 20;
          min-width: 7.5rem;
          background: var(--el-bg-color);
          // 阴影
          box-shadow: 0 2px 12px 0 var(--shadow-color);
          border-radius: 5px;
          transform: translateX(-50%);

          li {
            display: flex;
            justify-content: left;
            padding: 10px;
            border-radius: 5px;
            white-space: nowrap;

            &:hover {
              cursor: pointer;
              background: #91a7ff;
            }
          }

          // 子菜单出现动画（保留水平居中）
          @keyframes slide-down {
            0% {
              opacity: 0;
              transform: translate(-50%, -10px);
            }
            100% {
              opacity: 1;
              transform: translate(-50%, 0);
            }
          }

          animation: slide-down 0.3s ease-out;
        }

        .blog-menu-child {
          width: 12rem;
          max-height: min(26rem, calc(100vh - 70px));
          overflow-y: auto;
          padding: .4rem;

          li { padding: .65rem .75rem; }
          .all-categories { border-bottom: 1px solid var(--el-border-color-lighter); margin-bottom: .25rem; }
          .category-link { width: 100%; justify-content: space-between; gap: 1rem; }
          .category-link > span { max-width: 8rem; overflow: hidden; text-overflow: ellipsis; }
          small { color: var(--el-text-color-placeholder); font-size: .65rem; font-weight: 500; }
          li:hover small { color: inherit; }
        }
      }
    }
  }

  #menu-right {
    flex: 0 0 auto;
    width: auto;
    display: flex;
    justify-content: flex-end;
    align-items: center;
    padding-right: 8px;

    .search {
      display: flex;
      justify-content: center;
      align-items: center;
      margin-right: 20px;
      transition: transform 0.3s linear;
      cursor: pointer;

      &:hover {
        transform: scale(1.1);
      }
    }
  }
}

.search_dialog_container {
  :deep(.el-dialog) {
    overflow: auto;
    border-radius: 10px;
    height: 70%;
  }

  @media screen and (max-width: 650px) {
    :deep(.el-dialog) {
      border-radius: 0;
      margin-top: 0;
      margin-bottom: 0;
      width: 100vw;
      height: 100%;
    }
  }
}

:deep(.el-dialog) {
  // 过渡效果
  transition: all .3s;
  @media (max-width: 1400px) {
    width: 45%;
  }
  @media (max-width: 1000px) {
    width: 60%;
  }
  @media (max-width: 760px) {
    width: 70%;
  }
  @media (max-width: 600px) {
    width: 90%;
  }
}

nav a { color: inherit; text-decoration: none; }
nav { color: var(--brand-ink); }
nav #menu-left #menus .menus_items .menus_item { text-shadow: none; }
nav #menu-left #menus .menus_items .menus_item.is-section::before, nav #menu-left #menus .menus_items .menus_item.router-link-exact-active::before { width: 100%; background: var(--brand-accent-strong); }
nav .menus_item.router-link-exact-active, nav .menus_item.is-section { color: var(--brand-accent-strong); }
nav a:focus-visible, nav button:focus-visible { outline: 2px solid var(--brand-accent); outline-offset: -3px; }
.blog-destination { display: inline-flex; align-items: center; gap: .35rem; min-height: 44px; }
.category-toggle { display: inline-grid; place-items: center; min-width: 44px; min-height: 44px; border: 0; background: transparent; color: inherit; cursor: pointer; }
nav #menu-left #menus .menus_items .menus_item_child { display: block; animation: none; }
nav #menu-left #menus .menus_items .menus_item_child li { padding: 0; }
.menus_item_child a { display: flex; align-items: center; gap: .5rem; min-height: 44px; width: 100%; padding: .6rem .75rem; }
.menus_item_child .router-link-exact-active { color: var(--brand-accent-strong); background: var(--brand-accent-soft); }
#menu-right .search { min-width: 44px; min-height: 44px; border: 0; background: transparent; padding: 0; }
@media (max-width:1100px) { nav #menu-left #menus .menus_items { gap: .25rem; padding-inline: .25rem; } nav #menu-left #menus .menus_items .menus_item { padding-inline: .3rem; } }
</style>
