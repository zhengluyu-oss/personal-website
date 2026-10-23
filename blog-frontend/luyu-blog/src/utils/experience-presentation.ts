/** A short leading label is optional; legacy paragraphs remain intact. */
export function contributionParts(value: string): { title: string; detail: string } {
  const text = value.trim()
  const separator = text.search(/[：:]/)
  if (separator > 0 && separator <= 24 && text.slice(separator + 1).trim()) {
    return { title: text.slice(0, separator), detail: text.slice(separator + 1).trim() }
  }
  return { title: '', detail: text }
}
