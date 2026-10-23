export interface RequestToken {
  id: number
  blocking: boolean
  finalized: boolean
}

interface RequestTrackerHooks {
  onProgressStart: () => void
  onProgressDone: () => void
  onBlockingStart: () => void
  onBlockingDone: () => void
}

export function createRequestTracker(hooks: RequestTrackerHooks) {
  let nextId = 0
  let activeRequests = 0
  let blockingRequests = 0

  function begin(blocking = false): RequestToken {
    if (activeRequests === 0) hooks.onProgressStart()
    activeRequests += 1
    if (blocking) {
      if (blockingRequests === 0) hooks.onBlockingStart()
      blockingRequests += 1
    }
    return { id: nextId += 1, blocking, finalized: false }
  }

  function finalize(token?: RequestToken) {
    if (!token || token.finalized) return
    token.finalized = true
    activeRequests = Math.max(0, activeRequests - 1)
    if (token.blocking) {
      blockingRequests = Math.max(0, blockingRequests - 1)
      if (blockingRequests === 0) hooks.onBlockingDone()
    }
    if (activeRequests === 0) hooks.onProgressDone()
  }

  const snapshot = () => ({ activeRequests, blockingRequests })
  return { begin, finalize, snapshot }
}

