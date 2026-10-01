// 使用 vue-router 配置路由
import {createRouter, createWebHistory} from 'vue-router'
import {constantRouter} from '@/router/routers.ts'
import {GET_TOKEN} from "@/utils/auth.ts";
import {applyFixedSeo} from '@/utils/seo'
import {installAsyncChunkRecovery} from '@/utils/chunk-recovery'
import {scrollPositionForRoute} from '@/router/scroll-position'

let router = createRouter({
    // 路由模式 History
    history: createWebHistory(),
    routes: constantRouter,
    scrollBehavior(to, _from, savedPosition) {
        return scrollPositionForRoute(to.hash, savedPosition)
    }
})

installAsyncChunkRecovery(router)

router.beforeEach((to, from, next) => {
    // 用户是否登录
    const isLogin = GET_TOKEN()
    // 用户登录了，跳转到登录页，直接跳转到首页
    if (to.name?.startsWith(('welcome-')) && isLogin) {
        next('/')
    } else {
        next()
    }
})

router.afterEach((to, _from, failure) => {
    if (!failure) applyFixedSeo(to.name, to.meta.title as string, to.path)
})

export default router
