import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'
import { highlightTextSegments } from './safe-highlight.ts'

test('搜索标题和摘要的恶意标记始终保持为文本片段', () => {
  const payload = '<img src=x onerror=alert(1)>needle<script>alert(2)</script>'
  const segments = highlightTextSegments(payload, 'needle')
  assert.equal(segments.map(segment => segment.text).join(''), payload)
  assert.deepEqual(segments.filter(segment => segment.matched).map(segment => segment.text), ['needle'])
  assert.equal(segments.some(segment => segment.text.includes('<img')), true)
})

test('含正则元字符的搜索词不会改变文本或抛出异常', () => {
  const payload = 'before [a+b] after'
  assert.deepEqual(highlightTextSegments(payload, '[a+b]'), [
    { text: 'before ', matched: false },
    { text: '[a+b]', matched: true },
    { text: ' after', matched: false },
  ])
})

test('评论与留言模板不使用 v-html 或 Markdown 解释用户内容', () => {
  const files = [
    '../components/Comment/index.vue',
    '../components/Comment/ChildComment.vue',
    '../views/Amusement/Message/MessageDetail/index.vue',
  ]
  for (const file of files) {
    const source = readFileSync(new URL(file, import.meta.url), 'utf8')
    assert.equal(source.includes('v-html'), false, file)
    assert.equal(source.includes('<MdPreview'), false, file)
  }
})
