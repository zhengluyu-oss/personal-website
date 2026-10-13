import {defineStore} from 'pinia'
import {GET_TOKEN} from '@/utils/auth.ts';
import {getUserInfo, UserInfo} from "@/apis/user";

const useUserStore = defineStore('user', () => {
    const token = GET_TOKEN()
    const userInfo = shallowRef<UserInfo>()

    // 获取用户信息
    const getInfo = async () => {
        const res: any = await getUserInfo()
        if (res.code === 200 && res.data) {
            userInfo.value = res.data
            return res.data
        }
        throw new Error(res.msg || '获取登录用户信息失败')
    }

    return {
        token,
        userInfo,
        getInfo
    }
})

export default useUserStore;
