export interface PublicContact { label: string; href: string }

export function publicProfileUrl(value: string, kind: 'contact' | 'resume' = 'contact'): string {
  if (/[\u0000-\u001f\u007f\\]/.test(value)) return ''
  const href = value.trim()
  if (!href || /\s/.test(href) || /%0[ad]/i.test(href)) return ''
  if (kind === 'resume' && /^\/(?!\/)/.test(href)) return href
  if (kind === 'contact' && /^mailto:[^?@]+@[^?@]+$/i.test(href)) return href
  try {
    const url = new URL(href)
    return url.protocol === 'https:' && !url.username && !url.password ? href : ''
  } catch { return '' }
}

export function publicContacts(links: readonly PublicContact[]): PublicContact[] {
  return links.flatMap(link => {
    const label = link.label.trim()
    const href = publicProfileUrl(link.href)
    return label && href ? [{ label, href }] : []
  })
}
