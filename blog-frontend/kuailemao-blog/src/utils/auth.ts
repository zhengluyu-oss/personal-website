// 获取token
import {TOKEN_KEY} from "@/const/Jwt";
import {ElMessage} from "element-plus";
import useUserStore from "@/store/modules/user.ts";

// 获取token
export const GET_TOKEN = () => {
    const persistentToken = localStorage.getItem(TOKEN_KEY)
    const sessionToken = sessionStorage.getItem(TOKEN_KEY)
    const str = persistentToken || sessionToken
    if (!str) return null

    try {
        const authObject = JSON.parse(str)
        const expireTime = new Date(authObject?.expire).getTime()
        if (typeof authObject?.token !== 'string' || !authObject.token || !Number.isFinite(expireTime) || expireTime <= Date.now()) {
            REMOVE_TOKEN()
            if (authObject?.token && Number.isFinite(expireTime) && expireTime <= Date.now()) {
                ElMessage.warning('登录状态已过期，请重新登录')
            }
            return null
        }

        return authObject.token
    } catch {
        REMOVE_TOKEN()
        return null
    }
}

// 设置token
export const SET_TOKEN = (token: string, expire: string, remember: boolean) => {
    const userStore = useUserStore()
    const authObject = {token, expire}
    const str = JSON.stringify(authObject);
    // 清除旧会话，避免另一种存储方式中的旧 token 被 GET_TOKEN 优先读取。
    REMOVE_TOKEN()
    // 记住我时持久保存，否则仅保留在当前标签页会话中。
    remember ? localStorage.setItem(TOKEN_KEY, str) : sessionStorage.setItem(TOKEN_KEY, str)
    userStore.token = token
}

// 移除token
export const REMOVE_TOKEN = () => {
    localStorage.removeItem(TOKEN_KEY)
    sessionStorage.removeItem(TOKEN_KEY)
}
