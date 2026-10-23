// 使用 vue-router 配置路由
import {createRouter, createWebHistory} from 'vue-router'
import {constantRouter} from '@/router/routers.ts'
import {GET_TOKEN} from "@/utils/auth.ts";
import {applyFixedSeo} from '@/utils/seo'
import {installAsyncChunkRecovery} from '@/utils/chunk-recovery'
import {scrollPositionForRoute} from '@/router/scroll-position'
import { refreshModuleAccess } from '@/composables/useSiteModuleAccess'
import { moduleForPath } from '@/utils/site-module-access'

let router = createRouter({
    // 路由模式 History
    history: createWebHistory(),
    routes: constantRouter,
    scrollBehavior(to, _from, savedPosition) {
        return scrollPositionForRoute(to.hash, savedPosition)
    }
})

installAsyncChunkRecovery(router)

router.beforeEach(async (to, from, next) => {
    const module = moduleForPath(to.path)
    if (module) {
        try {
            const access = await refreshModuleAccess()
            if (!access.find(item => item.key === module)?.allowed) {
                next({ name: 'moduleAccessDenied', query: { module, redirect: to.fullPath }, replace: true })
                return
            }
        } catch {
            next({ name: 'moduleAccessDenied', query: { module, redirect: to.fullPath, unavailable: '1' }, replace: true })
            return
        }
    }
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
