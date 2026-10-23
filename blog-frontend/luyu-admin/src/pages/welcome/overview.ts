export interface OverviewMetric {
  key: string
  label: string
  path: string
  status: 'idle' | 'loading' | 'ready' | 'error'
  value: number | null
}

export async function loadOverviewMetric(metric: OverviewMetric, request: () => Promise<any>) {
  if (metric.status === 'loading') return
  metric.status = 'loading'
  try {
    const response = await request()
    if (response?.code !== 200) throw new Error('获取失败')
    const data = response.data
    const count = metric.key === 'articles'
      ? (data?.total !== undefined && data.total !== null ? Number(data.total) : NaN)
      : (Array.isArray(data) ? data.length : NaN)
    if (!Number.isSafeInteger(count) || count < 0) throw new Error('数据不可用')
    metric.value = count
    metric.status = 'ready'
  }
  catch {
    metric.value = null
    metric.status = 'error'
  }
}
