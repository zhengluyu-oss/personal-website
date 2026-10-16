const beforeSessionEnd = new Set<() => Promise<boolean>>()
export function registerPrivateWorkspaceGuard(guard: () => Promise<boolean>) {
  beforeSessionEnd.add(guard)
  return () => { beforeSessionEnd.delete(guard) }
}
export async function confirmPrivateEditsBeforeLogout() {
  for (const guard of [...beforeSessionEnd]) if (!(await guard())) return false
  return true
}
