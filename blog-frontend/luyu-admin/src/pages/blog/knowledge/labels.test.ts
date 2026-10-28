import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount } from '@vue/test-utils'
import { ref } from 'vue'
import KnowledgePage from './index.vue'

const api = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('vue-router', () => ({ onBeforeRouteLeave: vi.fn() }))
vi.mock('~/composables/authorization', () => ({ useAuthorization: () => ref('owner-token') }))
vi.mock('~/utils/knowledge-api', () => ({
  knowledgeRequest: (...args: unknown[]) => api.request(...args),
  knowledgeUploadFile: vi.fn(), knowledgeDownloadFile: vi.fn(), knowledgeUploadImage: vi.fn(),
}))

const first = { id: 'category-1', kind: 'CATEGORY', name: '基础知识复习', parentCategoryId: null, revision: 1 }
const second = { id: 'category-2', kind: 'CATEGORY', name: '其他', parentCategoryId: null, revision: 1 }
const tag = { id: 'tag-1', kind: 'TAG', name: '旧标签', parentCategoryId: first.id, revision: 1 }
let labels: Array<{ id: string; kind: string; name: string; parentCategoryId: string | null; revision: number }>
let wrapper: ReturnType<typeof shallowMount>

beforeEach(() => {
  vi.clearAllMocks()
  labels = [{ ...first }, { ...second }, { ...tag }]
  api.request.mockImplementation(async (method: string, path: string, body?: any) => {
    if (path === '/view/capabilities') return { canManage: true, available: true }
    if (path === '/view/search') return { records: [], total: 0 }
    if (path === '/view/labels') return labels.map(label => ({ ...label }))
    if (method === 'POST' && path === '/manage/labels') {
      labels = labels.map(label => label.id === body.id ? { ...label, name: body.name, parentCategoryId: body.parentCategoryId, revision: label.revision + 1 } : label)
      return labels.find(label => label.id === body.id)
    }
    throw new Error(`Unexpected request: ${method} ${path}`)
  })
})
afterEach(() => wrapper?.unmount())

async function openManager() {
  wrapper = shallowMount(KnowledgePage, { global: { stubs: { MdEditor: true, Connections: true, Draft: true, PrivateMarkdown: true } } })
  await flushPromises()
  await wrapper.findAll('button').find(button => button.text() === '分类与标签')!.trigger('click')
  return wrapper
}
async function startEdit(position: number) {
  await wrapper.findAll('button').filter(button => button.text() === '编辑名称')[position].trigger('click')
}

describe('知识库分类与标签全局改名', () => {
  it('预填名称，取消后不发送请求', async () => {
    await openManager()
    await startEdit(0)
    expect((wrapper.find('input[aria-label="编辑分类名称"]').element as HTMLInputElement).value).toBe(first.name)
    await wrapper.find('.label-edit button[type="button"]').trigger('click')
    expect(wrapper.find('.label-edit').exists()).toBe(false)
    expect(api.request.mock.calls.filter(call => call[1] === '/manage/labels')).toHaveLength(0)
  })

  it('分类改名保留原 ID，刷新列表显示新名称', async () => {
    await openManager()
    await startEdit(0)
    await wrapper.find('input[aria-label="编辑分类名称"]').setValue('基础知识')
    await wrapper.find('.label-edit').trigger('submit')
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('POST', '/manage/labels', { id: first.id, kind: 'CATEGORY', name: '基础知识', parentCategoryId: null, expectedRevision: 1 })
    expect(wrapper.text()).toContain('基础知识')
    expect(wrapper.find('[role="status"]').text()).toContain('改名')
  })

  it('标签改名使用已保存的父分类，不提交归属草稿', async () => {
    await openManager()
    await wrapper.find('select[aria-label="旧标签 所属分类"]').setValue(second.id)
    await startEdit(2)
    await wrapper.find('input[aria-label="编辑标签名称"]').setValue('新标签')
    await wrapper.find('.label-edit').trigger('submit')
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('POST', '/manage/labels', { id: tag.id, kind: 'TAG', name: '新标签', parentCategoryId: first.id, expectedRevision: 1 })
    expect(labels.find(label => label.id === tag.id)?.parentCategoryId).toBe(first.id)
  })

  it('空白、未变化及同类型重名在提交前提示', async () => {
    await openManager()
    await startEdit(0)
    const input = wrapper.find('input[aria-label="编辑分类名称"]')
    for (const [name, message] of [[' ', '请输入名称'], [first.name, '名称没有变化'], [second.name, '已有同名分类']]) {
      await input.setValue(name)
      await wrapper.find('.label-edit').trigger('submit')
      expect(wrapper.find('.label-edit [role="alert"]').text()).toContain(message)
    }
    expect(api.request.mock.calls.filter(call => call[1] === '/manage/labels')).toHaveLength(0)
  })

  it('版本冲突保留输入，提示重新打开', async () => {
    await openManager()
    await startEdit(0)
    await wrapper.find('input[aria-label="编辑分类名称"]').setValue('基础知识')
    api.request.mockRejectedValueOnce(Object.assign(new Error('conflict'), { status: 409 }))
    await wrapper.find('.label-edit').trigger('submit')
    await flushPromises()
    expect((wrapper.find('input[aria-label="编辑分类名称"]').element as HTMLInputElement).value).toBe('基础知识')
    expect(wrapper.find('.label-edit [role="alert"]').text()).toContain('记录已变化')
  })
})
