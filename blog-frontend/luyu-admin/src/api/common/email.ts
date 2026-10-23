import { waitForEmailDelivery } from '../../../../shared/email-delivery'
import { useGet } from '~/utils/request'

export { EmailDeliveryError } from '../../../../shared/email-delivery'

export function confirmEmailDelivery(taskId: string | undefined, signal?: AbortSignal) {
  return waitForEmailDelivery(taskId, async (id, requestSignal) => {
    const result = await useGet('/public/email-delivery', { taskId: id }, { token: false, loading: false, signal: requestSignal, timeout: 10000 })
    if (result.code !== 200 || !result.data) throw new Error('发送状态暂时不可用')
    return result.data
  }, { signal })
}
