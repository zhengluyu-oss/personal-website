/** Normalize compressed image metadata without changing the shared compressor contract. */
export function toImageUploadFile(result: File | Blob, originalName: string): File {
  const name = (result instanceof File ? result.name : originalName) || 'image'
  const extension = { 'image/jpeg': 'jpg', 'image/png': 'png', 'image/webp': 'webp' }[result.type]
  const currentExtension = name.split('.').pop()?.toLowerCase()
  const matches = extension === currentExtension || (extension === 'jpg' && currentExtension === 'jpeg')
  const fileName = extension && !matches ? `${name.replace(/\.[^.]*$/, '') || 'image'}.${extension}` : name
  if (result instanceof File && result.name === fileName)
    return result
  return new File([result], fileName, { type: result.type })
}
