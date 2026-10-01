export function createLatestRequest() {
  let version = 0
  return {
    start: () => ++version,
    isCurrent: (candidate: number) => candidate === version,
    invalidate: () => { version++ },
  }
}
