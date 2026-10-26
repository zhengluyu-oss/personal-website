export type HighlightSegment = { text: string; matched: boolean }

const escapeRegExp = (value: string) => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')

export function highlightTextSegments(value: string | undefined, query: string): HighlightSegment[] {
  const text = value || ''
  if (!query) return [{ text, matched: false }]
  const regex = new RegExp(`(${escapeRegExp(query)})`, 'gi')
  return text.split(regex).filter(Boolean).map(part => ({
    text: part,
    matched: part.toLocaleLowerCase() === query.toLocaleLowerCase(),
  }))
}
