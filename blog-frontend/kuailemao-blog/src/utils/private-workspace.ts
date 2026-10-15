let beforeSessionEnd: (() => Promise<boolean>) | undefined
export function registerPrivateWorkspaceGuard(guard: () => Promise<boolean>) {
  beforeSessionEnd = guard
  return () => { if (beforeSessionEnd === guard) beforeSessionEnd = undefined }
}
export async function confirmPrivateEditsBeforeLogout() { return beforeSessionEnd ? beforeSessionEnd() : true }
