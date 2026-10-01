export function scrollPositionForRoute(hash: string, savedPosition: { left: number; top: number } | null) {
  if (savedPosition) return savedPosition
  if (hash) return { el: hash }
  return { left: 0, top: 0 }
}
