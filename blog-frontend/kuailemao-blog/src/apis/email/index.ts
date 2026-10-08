import http from "@/utils/http.ts";
import { waitForEmailDelivery } from '../../../../shared/email-delivery'

export { EmailDeliveryError } from '../../../../shared/email-delivery'

export function confirmEmailDelivery(taskId: string | undefined, signal?: AbortSignal) {
    return waitForEmailDelivery(taskId, async (id, requestSignal) => {
        const result: any = await http.get('/public/email-delivery', { params: { taskId: id }, signal: requestSignal, timeout: 10000 })
        if (result.code !== 200 || !result.data) throw new Error('发送状态暂时不可用')
        return result.data
    }, { signal })
}

/**
 * 发送邮件
 * @param email 邮件地址
 * @param type 类型
 */
export function sendEmail(email: any, type: any) {
    return http({
        url: '/public/ask-code',
        params: {
            email: email,
            type: type
        },
        method: 'get'
    })
}
