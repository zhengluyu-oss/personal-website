import { sanitizeRenderedHtml } from './sanitize-html'
export function sanitizePrivateDiary(html: string): string {
  // template内容是惰性的，先移除所有媒体，再交给既有HTML净化器，不触发远程资源请求。
  const template = document.createElement('template')
  template.innerHTML = html
  template.content.querySelectorAll('img,picture,source,video,audio,svg,math,iframe,object,embed,style,link,script').forEach(element => element.remove())
  return sanitizeRenderedHtml(template.innerHTML)
}
