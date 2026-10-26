import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { flushPromises, shallowMount } from '@vue/test-utils'
import Editor from './index.vue'

const mocks = vi.hoisted(() => ({
  publish: vi.fn(), upload: vi.fn(), removeCover: vi.fn(), echo: vi.fn(), push: vi.fn(), close: vi.fn(),
  route: { params: { id: '7' }, fullPath: '/articles/7' },
}))
vi.mock('~/components/page-container/index.vue', () => ({ default: { name: 'ReviewContainer', template: '<div><slot name="extra" /><slot /></div>' } }))
vi.mock('vue-router', async (original) => ({ ...await original<any>(), useRoute: () => mocks.route, useRouter: () => ({ push: mocks.push }) }))
vi.mock('~/stores/multi-tab.ts', () => ({ useMultiTab: () => ({ close: mocks.close }) }))
vi.mock('~/utils/CompressedImage.ts', () => ({ compressImage: async (file: File) => file }))
vi.mock('ant-design-vue/es/message', () => ({ default: { success: vi.fn(), warn: vi.fn(), warning: vi.fn(), error: vi.fn() } }))
vi.mock('ant-design-vue', async (original) => ({ ...await original<any>(), message: { success: vi.fn(), warn: vi.fn(), warning: vi.fn(), error: vi.fn() } }))
vi.mock('~/api/blog/article', () => ({
  articleCategory: async () => ({ data: [{ id: 1, categoryName: '测试分类' }] }),
  articleTag: async () => ({ data: [{ id: 1, tagName: '测试标签' }] }),
  getArticle: (...args: any[]) => mocks.echo(...args),
  publishArticle: (...args: any[]) => mocks.publish(...args),
  uploadCover: (...args: any[]) => mocks.upload(...args),
  deleteCover: (...args: any[]) => mocks.removeCover(...args),
  addCategory: vi.fn(), addTag: vi.fn(), uploadArticleImage: vi.fn(),
}))
const Button = defineComponent({ props: ['disabled', 'loading'], template: '<button :disabled="disabled"><slot /></button>' })
const Container = defineComponent({ template: '<div><slot name="extra" /><slot /></div>' })
let wrapper: ReturnType<typeof shallowMount>
const originalArticle = (status = 1) => ({ id: '7', articleTitle: '保留标题', articleContent: '# 保留正文', articleCover: '/saved.jpg', categoryId: 1, tagId: [1], articleType: 1, status, isTop: 0, seoTitle: 'SEO 标题' })
async function open(status = 1) {
  mocks.echo.mockResolvedValue({ code: 200, data: originalArticle(status) })
  wrapper = shallowMount(Editor, { global: { renderStubDefaultSlot: true, stubs: { PageContainer: Container, ReviewContainer: false, AButton: Button, AInput: { template: '<input />' }, PlusOutlined: true, PictureOutlined: true } } })
  await flushPromises()
  const submit = wrapper.findAll('button').find(button => /保存修改|保存草稿|发布文章/.test(button.text()))
  if (!submit) throw new Error(wrapper.html().slice(0, 2500))
  return submit
}
beforeEach(() => {
  vi.clearAllMocks()
  mocks.push.mockResolvedValue(undefined)
  mocks.publish.mockResolvedValue({ code: 200 })
  mocks.upload.mockResolvedValue({ code: 200, data: '/temporary.jpg' })
  mocks.removeCover.mockResolvedValue({ code: 200 })
})
afterEach(() => wrapper?.unmount())

describe('编辑页重新布局后的提交契约', () => {
  it.each([1, 2, 3])('保留状态 %i 的提交数据并成功返回列表', async (status) => {
    const submit = await open(status)
    await submit.trigger('click')
    await flushPromises()
    expect(mocks.publish).toHaveBeenCalledWith(expect.objectContaining(originalArticle(status)))
    expect(mocks.push).toHaveBeenCalledWith('/articles')
    expect(mocks.close).toHaveBeenCalledWith('/articles/7')
    expect(mocks.upload).not.toHaveBeenCalled()
  })
  it('发布失败保留标题正文和当前页', async () => {
    mocks.publish.mockResolvedValue({ code: 500 })
    const submit = await open()
    await submit.trigger('click')
    await flushPromises()
    expect((wrapper.vm as any).formData).toMatchObject(originalArticle())
    expect(mocks.push).not.toHaveBeenCalled()
    expect(submit.attributes('disabled')).toBeUndefined()
  })
  it('提交进行中禁用按钮并阻止重复提交', async () => {
    let finish!: (value: any) => void
    mocks.publish.mockImplementation(() => new Promise(resolve => { finish = resolve }))
    const submit = await open()
    await submit.trigger('click')
    await submit.trigger('click')
    expect(mocks.publish).toHaveBeenCalledTimes(1)
    expect(submit.attributes('disabled')).toBeDefined()
    finish({ code: 200 })
    await flushPromises()
    expect(mocks.push).toHaveBeenCalledTimes(1)
  })
  it('缺少必填内容时不上传或提交', async () => {
    const submit = await open()
    ;(wrapper.vm as any).formData.articleTitle = ''
    await submit.trigger('click')
    expect(mocks.publish).not.toHaveBeenCalled()
    expect(mocks.upload).not.toHaveBeenCalled()
  })
  it('新封面上传后发布失败，仅清理临时封面并保留正文', async () => {
    mocks.publish.mockResolvedValue({ code: 500 })
    const submit = await open()
    ;(wrapper.vm as any).fileList = [{ uid: 'test', name: 'cover.png', originFileObj: new File(['image'], 'cover.png', { type: 'image/png' }) }]
    await submit.trigger('click')
    await flushPromises()
    expect(mocks.upload).toHaveBeenCalledTimes(1)
    expect(mocks.removeCover).toHaveBeenCalledWith('/temporary.jpg')
    expect((wrapper.vm as any).formData.articleContent).toBe('# 保留正文')
    expect(mocks.push).not.toHaveBeenCalled()
  })
  it('封面上传失败不提交文章', async () => {
    mocks.upload.mockResolvedValue({ code: 500 })
    const submit = await open()
    ;(wrapper.vm as any).fileList = [{ uid: 'test', originFileObj: new File(['image'], 'cover.png') }]
    await submit.trigger('click')
    await flushPromises()
    expect(mocks.publish).not.toHaveBeenCalled()
    expect(mocks.removeCover).not.toHaveBeenCalled()
    expect((wrapper.vm as any).formData.articleTitle).toBe('保留标题')
  })
})
