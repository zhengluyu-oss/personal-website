export type DeliveryStatus = { status: string; message?: string }

export class EmailDeliveryError extends Error {
  constructor(message: string, public status: string) { super(message) }
}

/** Enqueue acceptance never counts as delivery success. */
export async function waitForEmailDelivery(
  taskId: string | undefined,
  fetchStatus: (taskId: string, signal: AbortSignal) => Promise<DeliveryStatus>,
  options: { signal?: AbortSignal; timeoutMs?: number; intervalMs?: number } = {},
): Promise<void> {
  if (!taskId || !/^[0-9a-f]{64}$/.test(taskId))
    throw new EmailDeliveryError('未获得发送结果，请检查邮箱或稍后重试', 'UNKNOWN')
  const controller = new AbortController()
  const cancel = () => controller.abort()
  options.signal?.addEventListener('abort', cancel, { once: true })
  if (options.signal?.aborted) cancel()
  const deadline = setTimeout(cancel, options.timeoutMs ?? 45_000)
  try {
    while (!controller.signal.aborted) {
      const result = await fetchStatus(taskId, controller.signal)
      if (controller.signal.aborted) break
      if (result.status === 'SENT') return
      if (result.status === 'FAILED' || result.status === 'EXPIRED')
        throw new EmailDeliveryError(result.message || '验证码发送失败，请稍后重试', result.status)
      if (!['PENDING', 'PROCESSING'].includes(result.status))
        throw new EmailDeliveryError('暂未确认发送结果，请检查邮箱或稍后重试', 'UNKNOWN')
      await new Promise<void>(resolve => {
        const done = () => { clearTimeout(timer); controller.signal.removeEventListener('abort', done); resolve() }
        const timer = setTimeout(done, options.intervalMs ?? 2000)
        controller.signal.addEventListener('abort', done, { once: true })
        if (controller.signal.aborted) done()
      })
    }
    throw new EmailDeliveryError('暂未确认发送结果，请检查邮箱或稍后重试', options.signal?.aborted ? 'CANCELLED' : 'UNKNOWN')
  } catch (error) {
    if (error instanceof EmailDeliveryError) throw error
    throw new EmailDeliveryError('暂未确认发送结果，请检查邮箱或稍后重试', options.signal?.aborted ? 'CANCELLED' : 'UNKNOWN')
  } finally {
    clearTimeout(deadline)
    options.signal?.removeEventListener('abort', cancel)
  }
}
