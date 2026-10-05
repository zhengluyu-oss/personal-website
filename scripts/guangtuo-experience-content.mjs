import { readFile } from 'node:fs/promises'

const directory = new URL('../docs/experience/guangtuo/', import.meta.url)
const projectFields = ['summary', 'techStack', 'contributions', 'outcomes']
const experienceFields = ['projectSummary', 'responsibilities', 'techStack', 'content']

export async function loadContent() {
  const manifest = JSON.parse(await readFile(new URL('content.json', directory), 'utf8'))
  if (manifest.experienceId !== 2 || manifest.projects.map(x => x.id).join(',') !== '4,5,6,7,8,9') {
    throw new Error('Unexpected experience/project scope')
  }
  for (const project of manifest.projects) {
    project.content = await readFile(new URL(`project-${project.id}.md`, directory), 'utf8')
  }
  return manifest
}

function selectedFields(source, fields) {
  return Object.fromEntries(fields.map(key => [key, source[key]]))
}

export function mergeProject(original, manifest) {
  const revision = manifest.projects.find(x => x.id === Number(original.id))
  if (!revision || Number(original.experienceId) !== manifest.experienceId) return original
  const gallery = original.content?.match(/^## 功能示意图\s*$/m)
  // Preserve the original gallery exactly; do not guess image URLs or replace existing images.
  const suffix = gallery ? original.content.slice(gallery.index) : ''
  return {
    ...original,
    ...selectedFields(revision, projectFields),
    content: revision.content.trim() + (suffix ? '\n\n' + suffix : ''),
  }
}

export function mergeExperience(original, manifest) {
  if (Number(original.id) !== manifest.experienceId) return original
  return { ...original, ...selectedFields(manifest.experience, experienceFields) }
}
