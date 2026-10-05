/** Prepare a text-only change set from public records. Does not write to any server. */
import { createHash } from 'node:crypto'
import { pathToFileURL } from 'node:url'
import { loadContent, mergeExperience, mergeProject } from './guangtuo-experience-content.mjs'

const companyFields = ['projectSummary', 'responsibilities', 'techStack', 'content']
const projectFields = ['summary', 'techStack', 'contributions', 'outcomes', 'content']
const select = (record, fields) => Object.fromEntries(fields.map(key => [key, record[key] ?? null]))
const digest = value => createHash('sha256').update(JSON.stringify(value)).digest('hex')

export function buildUpdatePlan(company, projects, manifest) {
  if (Number(company?.id) !== 2 || !company.company?.includes('广拓时代')) throw new Error('Company identity mismatch')
  if (projects.length !== 6 || new Set(projects.map(x => Number(x.id))).size !== 6) throw new Error('Expected six distinct project records')
  const changes = [{ kind: 'experience', id: 2, name: company.company, before: select(company, companyFields), after: select(mergeExperience(company, manifest), companyFields) }]
  for (const id of [4, 5, 6, 7, 8, 9]) {
    const original = projects.find(x => Number(x.id) === id)
    if (!original || Number(original.experienceId) !== 2) throw new Error(`Project ${id} identity mismatch`)
    const revised = mergeProject(original, manifest)
    if (revised.summary.length > 500 || !revised.content.trim()) throw new Error(`Project ${id} invalid content`)
    changes.push({ kind: 'project', id, experienceId: 2, name: original.projectName, before: select(original, projectFields), after: select(revised, projectFields) })
  }
  return {
    schemaVersion: 1,
    source: 'https://www.zhengluyu.com',
    mode: 'prepared-only',
    instructions: 'Before applying, back up authoritative records and compare each before value with current database values. Abort the entire update on any mismatch. Apply all changes transactionally and re-read for verification. Only fields explicitly present in after may be updated.',
    preservedFields: ['id', 'experienceId', 'projectName', 'company', 'roleTitle', 'startDate', 'endDate', 'isCurrent', 'coverImage', 'orderNum', 'status'],
    changes: changes.map(change => ({ ...change, beforeSha256: digest(change.before), afterSha256: digest(change.after) })),
  }
}

export async function prepareUpdate() {
  const read = async pathname => {
    const response = await fetch(`https://www.zhengluyu.com/api${pathname}`, { signal: AbortSignal.timeout(15000), redirect: 'error' })
    if (!response.ok) throw new Error(`Cannot read ${pathname}`)
    const body = await response.json()
    if (body.code !== 200 || !body.data) throw new Error(`Invalid record ${pathname}`)
    return body.data
  }
  const [manifest, company, ...projects] = await Promise.all([
    loadContent(), read('/experience/2'), ...[4, 5, 6, 7, 8, 9].map(id => read(`/experience/2/projects/${id}`)),
  ])
  return buildUpdatePlan(company, projects, manifest)
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try { process.stdout.write(JSON.stringify(await prepareUpdate(), null, 2) + '\n') }
  catch (error) { console.error(error.message); process.exitCode = 1 }
}
