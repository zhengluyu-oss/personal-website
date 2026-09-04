const BLOCKED_ELEMENTS = 'script,style,iframe,object,embed,link,meta,base,form,input,button,textarea,select'
const URL_ATTRIBUTES = new Set(['href', 'src', 'xlink:href', 'formaction'])

export function sanitizeRenderedHtml(html: string): string {
  if (typeof DOMParser === 'undefined') return ''
  const document = new DOMParser().parseFromString(html, 'text/html')
  document.querySelectorAll(BLOCKED_ELEMENTS).forEach(element => element.remove())
  document.body.querySelectorAll('*').forEach((element) => {
    for (const attribute of Array.from(element.attributes)) {
      const name = attribute.name.toLowerCase()
      const value = attribute.value.trim().replace(/[\u0000-\u001f\u007f\s]+/g, '')
      if (name.startsWith('on') || name === 'srcdoc') element.removeAttribute(attribute.name)
      if (URL_ATTRIBUTES.has(name) && /^(?:javascript|vbscript|data):/i.test(value)) {
        element.removeAttribute(attribute.name)
      }
    }
  })
  return document.body.innerHTML
}
