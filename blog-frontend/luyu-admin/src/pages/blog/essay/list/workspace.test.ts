import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount } from '@vue/test-utils'
import ArticleList from './index.vue'

const api = vi.hoisted(() => ({ search: vi.fn(), remove: vi.fn(), status: vi.fn(), top: vi.fn() }))
vi.mock('~/components/layout/index.vue', () => ({ default: {
  name: 'ReviewListLayout',
  template: '<div><slot name="extra" /><slot name="form-items" /><slot name="operate-btn" /><slot name="table-content" /></div>',
} }))
vi.mock('ant-design-vue/es/message', () => ({ default: { success: vi.fn(), warn: vi.fn(), warning: vi.fn(), error: vi.fn() } }))
vi.mock('ant-design-vue', async (original) => ({ ...await original<any>(), message: { success: vi.fn(), warn: vi.fn(), warning: vi.fn(), error: vi.fn() } }))
vi.mock('~/api/blog/article', () => ({
  articleCategory: async () => ({ data: [] }), articleTag: async () => ({ data: [] }),
  articleSearch: (...args: any[]) => api.search(...args), deleteArticle: (...args: any[]) => api.remove(...args),
  updateArticleStatus: (...args: any[]) => api.status(...args), updateArticleTop: (...args: any[]) => api.top(...args),
}))
let wrapper: ReturnType<typeof shallowMount>
beforeEach(() => {
  vi.clearAllMocks()
  api.search.mockResolvedValue({ code: 200, data: { page: [{ id: '1', isTop: 1 }], total: 23 } })
  api.remove.mockResolvedValue({ code: 200 })
  api.status.mockResolvedValue({ code: 200 })
  api.top.mockResolvedValue({ code: 200 })
})
afterEach(() => wrapper?.unmount())
async function open() {
  wrapper = shallowMount(ArticleList, { global: { renderStubDefaultSlot: true, stubs: { ReviewListLayout: false, AInput: { template: '<input />' }, PlusOutlined: true } } })
  await flushPromises()
  return wrapper.vm as any
}
describe('文章列表布局调整后的管理契约', () => {
  it('初次请求、换页及刷新保留筛选，筛选与页大小变化回到第一页', async () => {
    const vm = await open()
    expect(api.search).toHaveBeenLastCalledWith(expect.objectContaining({ pageNum: 1, pageSize: 10 }))
    vm.formData.articleTitle = '测试'
    await vm.handleTableChange({ current: 2, pageSize: 10 })
    await vm.refreshFunc()
    expect(api.search).toHaveBeenLastCalledWith(expect.objectContaining({ articleTitle: '测试', pageNum: 2, pageSize: 10 }))
    await vm.onFinish()
    expect(api.search).toHaveBeenLastCalledWith(expect.objectContaining({ articleTitle: '测试', pageNum: 1 }))
    await vm.handleTableChange({ current: 2, pageSize: 20 })
    expect(api.search).toHaveBeenLastCalledWith(expect.objectContaining({ pageNum: 1, pageSize: 20 }))
  })
  it('删除末页唯一记录后重新请求有效页', async () => {
    const vm = await open()
    vm.pagination.current = 3
    api.search.mockResolvedValueOnce({ code: 200, data: { page: [], total: 20 } })
      .mockResolvedValueOnce({ code: 200, data: { page: [{ id: '20' }], total: 20 } })
    vm.onDelete(['21'])
    await flushPromises()
    expect(api.remove).toHaveBeenCalledWith(['21'])
    expect(vm.pagination.current).toBe(2)
    expect(api.search).toHaveBeenLastCalledWith(expect.objectContaining({ pageNum: 2 }))
  })
  it('更新成功后才展示新状态，失败后保留旧状态并结束加载', async () => {
    const vm = await open()
    const record = { status: 1, statusLoading: false, isTop: false, isToploading: false }
    await vm.updateArticleStatusFunc('1', 3, record)
    expect(api.status).toHaveBeenCalledWith({ id: '1', status: 3 })
    expect(record.status).toBe(3)
    api.status.mockResolvedValueOnce(undefined)
    await vm.updateArticleStatusFunc('1', 2, record)
    expect(record).toMatchObject({ status: 3, statusLoading: false })
    await vm.updateIsTopFunc('1', true, record)
    expect(api.top).toHaveBeenCalledWith({ id: '1', isTop: 1 })
    expect(record.isTop).toBe(true)
    api.top.mockResolvedValueOnce(undefined)
    await vm.updateIsTopFunc('1', false, record)
    expect(record).toMatchObject({ isTop: true, isToploading: false })
  })
  it('批量选择使单项编辑与批量删除按选中数量启用', async () => {
    const vm = await open()
    vm.onSelectChange(['1'])
    await wrapper.vm.$nextTick()
    const buttons = wrapper.findAllComponents({ name: 'AButton' })
    expect(buttons.find(button => button.text().includes('编辑所选'))?.props('disabled')).toBe(false)
    vm.onSelectChange(['1', '2'])
    await wrapper.vm.$nextTick()
    expect(buttons.find(button => button.text().includes('编辑所选'))?.props('disabled')).toBe(true)
    expect(buttons.find(button => button.text().includes('批量删除'))?.props('disabled')).toBe(false)
  })
})
