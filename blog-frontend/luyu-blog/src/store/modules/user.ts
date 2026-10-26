import {defineStore} from 'pinia'
import {GET_TOKEN, REMOVE_TOKEN} from '@/utils/auth.ts';
import {getUserInfo, UserInfo} from "@/apis/user";
import {ref, shallowRef, watch} from 'vue'
import {isAuthenticationFailure} from '../../utils/session-boundary'

const useUserStore = defineStore('user', () => {
    const token = ref(GET_TOKEN())
    const userInfo = shallowRef<UserInfo>()
    const sessionState = ref<'anonymous' | 'loading' | 'ready' | 'unavailable'>(token.value ? 'unavailable' : 'anonymous')
    let requestSequence = 0

    watch(token, () => {
        requestSequence++
        userInfo.value = undefined
        sessionState.value = token.value ? 'unavailable' : 'anonymous'
    }, {flush: 'sync'})

    const clearSession = () => {
        REMOVE_TOKEN()
        token.value = null
        userInfo.value = undefined
        sessionState.value = 'anonymous'
        requestSequence++
    }

    // 获取用户信息
    const getInfo = async () => {
        const requestToken = GET_TOKEN()
        if (!requestToken) {
            clearSession()
            return
        }
        token.value = requestToken
        const sequence = ++requestSequence
        userInfo.value = undefined
        sessionState.value = 'loading'
        try {
            const res: any = await getUserInfo()
            if (sequence !== requestSequence || GET_TOKEN() !== requestToken) {
                throw new Error('登录会话已变更，请重新确认')
            }
            if (res.code === 200 && res.data) {
                userInfo.value = res.data
                sessionState.value = 'ready'
                return res.data
            }
            throw Object.assign(new Error(res.msg || '获取登录用户信息失败'), {code: res.code})
        } catch (error) {
            if (sequence === requestSequence) {
                if (isAuthenticationFailure(error) || !GET_TOKEN()) clearSession()
                else {
                    token.value = GET_TOKEN()
                    userInfo.value = undefined
                    sessionState.value = 'unavailable'
                }
            }
            throw error
        }
    }

    return {
        token,
        userInfo,
        sessionState,
        getInfo,
        clearSession
    }
})

export default useUserStore;
