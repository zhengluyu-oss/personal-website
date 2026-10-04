import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import ts from 'typescript'
import { parse } from 'vue/compiler-sfc'
import { transform } from 'esbuild'

const root = new URL('../', import.meta.url)
const read = path => readFile(new URL(path, root), 'utf8')
const uploadSource = await transform(await read('src/utils/upload-file.ts'), { loader: 'ts', format: 'esm' })
const { toImageUploadFile } = await import(`data:text/javascript;base64,${Buffer.from(uploadSource.code).toString('base64')}`)

// Execute the page's actual handler body with boundary doubles; do not copy the implementation.
async function pageHandler(path, name, dependencies) {
  const { descriptor, errors } = parse(await read(path))
  assert.deepEqual(errors, [])
  const source = ts.createSourceFile(path + '.ts', descriptor.scriptSetup.content, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS)
  const declaration = source.statements.find(node => ts.isFunctionDeclaration(node) && node.name?.text === name)
  assert.ok(declaration, `${name} exists in the page`)
  const compiled = await transform(declaration.getText(source), { loader: 'ts', format: 'cjs' })
  return new Function(...Object.keys(dependencies), `${compiled.code}\nreturn ${name}`)(...Object.values(dependencies))
}

test('compressed Blob gets a real filename matching its MIME without changing bytes', async () => {
  for (const [type, originalName, expected] of [
    ['image/jpeg', 'original.png', 'original.jpg'],
    ['image/png', '', 'image.png'],
    ['image/webp', 'photo', 'photo.webp'],
  ]) {
    const blob = new Blob(['synthetic-image-data'], { type })
    const file = toImageUploadFile(blob, originalName)
    assert.ok(file instanceof File)
    assert.equal(file.name, expected)
    assert.equal(file.type, type)
    assert.equal(await file.text(), await blob.text())
    const form = new FormData()
    form.append('image', file, file.name)
    assert.equal(form.get('image').name, expected)
  }
})

test('valid named File is retained, mismatched extension is corrected', () => {
  const original = new File(['synthetic'], 'photo.jpeg', { type: 'image/jpeg' })
  assert.equal(toImageUploadFile(original, 'ignored.png'), original)
  assert.equal(toImageUploadFile(new File(['synthetic'], 'photo.png', { type: 'image/jpeg' }), 'original.png').name, 'photo.jpg')
})

const rolePath = 'src/pages/system/role/index.vue'
for (const failure of ['rejection', 'business-error']) {
  test(`role ${failure} restores the switch and never reports success`, async () => {
    for (const nextStatus of [true, false]) {
      const row = { id: 'qa-role', status: nextStatus }
      const other = { id: 'untouched', status: true }
      const messages = []
      const handler = await pageHandler(rolePath, 'statusBtn', {
        roleUpdateStatus: async (id, status) => {
          assert.equal(id, row.id)
          assert.equal(status, nextStatus ? 0 : 1)
          if (failure === 'rejection') throw new Error('synthetic failure')
          return { code: 500 }
        },
        tabData: { value: [row, other] },
        message: Object.fromEntries(['warn', 'success', 'info'].map(key => [key, text => messages.push([key, text])])),
      })
      await handler(row.id, nextStatus)
      assert.equal(row.status, !nextStatus)
      assert.equal(other.status, true)
      assert.equal(Object.hasOwn(row, 'isDisable'), false)
      assert.deepEqual(messages.map(([kind]) => kind), ['warn'])
    }
  })
}

test('successful role enable and disable retain the requested state', async () => {
  for (const nextStatus of [true, false]) {
    const row = { id: 'qa-role', status: nextStatus }
    const messages = []
    const handler = await pageHandler(rolePath, 'statusBtn', {
      roleUpdateStatus: async () => ({ code: 200 }),
      tabData: { value: [row] },
      message: Object.fromEntries(['warn', 'success', 'info'].map(key => [key, text => messages.push([key, text])])),
    })
    await handler(row.id, nextStatus)
    assert.equal(row.status, nextStatus)
    assert.deepEqual(messages.map(([kind]) => kind), [nextStatus ? 'success' : 'info'])
  }
})

test('stored banner preview uses its server path, not an absent browser upload object', async () => {
  const tempImage = { value: undefined }
  const previewVisible = { value: false }
  const previewTitle = { value: '' }
  const handler = await pageHandler('src/pages/blog/info/web-info/banners/index.vue', 'handlePreview', {
    tempImage, previewVisible, previewTitle,
  })
  const image = { id: 'qa-banner', path: 'https://example.invalid/banners/qa.webp' }
  handler(image)
  assert.equal(tempImage.value, image)
  assert.equal(previewVisible.value, true)
  assert.equal(previewTitle.value, 'qa.webp')
})

test('banner upload sends the compressed Blob as a named file and preserves the multipart field', async () => {
  const uploads = []
  const uploading = { value: false }
  let refreshCount = 0
  const handler = await pageHandler('src/pages/blog/info/web-info/banners/index.vue', 'beforeUpload', {
    compressImage: async () => new Blob(['synthetic-image'], { type: 'image/jpeg' }),
    toImageUploadFile,
    uploadBanner: async form => { uploads.push(form); return { code: 200 } },
    getFileList: () => { refreshCount++ },
    handleProgress: () => {}, uploading,
    message: { error: assert.fail, warn: assert.fail, success: () => {} },
  })
  assert.equal(await handler(new File(['original'], 'qa.png', { type: 'image/png' })), false)
  await new Promise(resolve => setImmediate(resolve))
  assert.equal(uploads.length, 1)
  assert.deepEqual([...uploads[0].keys()], ['bannerImage'])
  assert.equal(uploads[0].get('bannerImage').name, 'qa.jpg')
  assert.equal(uploading.value, false)
  assert.equal(refreshCount, 1)
})

test('failed banner request resets loading without announcing success', async () => {
  const messages = []
  const uploading = { value: false }
  const handler = await pageHandler('src/pages/blog/info/web-info/banners/index.vue', 'beforeUpload', {
    compressImage: async () => new Blob(['synthetic-image'], { type: 'image/webp' }),
    toImageUploadFile,
    uploadBanner: async () => { throw new Error('synthetic failure') },
    getFileList: assert.fail, handleProgress: () => {}, uploading,
    message: { error: assert.fail, success: assert.fail, warn: () => messages.push('warn') },
  })
  await handler(new File(['original'], 'qa.webp', { type: 'image/webp' }))
  await new Promise(resolve => setImmediate(resolve))
  assert.equal(uploading.value, false)
  assert.deepEqual(messages, ['warn'])
})
