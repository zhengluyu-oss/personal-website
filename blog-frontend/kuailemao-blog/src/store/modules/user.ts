import {defineStore} from 'pinia'
import {GET_TOKEN, REMOVE_TOKEN} from '@/utils/auth.ts';
import {getUserInfo, UserInfo} from "@/apis/user";
import {ref, shallowRef} from 'vue'

const useUserStore = defineStore('user', () => {
    const token = ref(GET_TOKEN())
    const userInfo = shallowRef<UserInfo>()

    // 获取用户信息
    const getInfo = async () => {
        try {
            const res: any = await getUserInfo()
            if (res.code === 200 && res.data) {
                userInfo.value = res.data
                return res.data
            }
            throw new Error(res.msg || '获取登录用户信息失败')
        } catch (error) {
            REMOVE_TOKEN()
            token.value = null
            userInfo.value = undefined
            throw error
        }
    }

    return {
        token,
        userInfo,
        getInfo
    }
})

export default useUserStore;
