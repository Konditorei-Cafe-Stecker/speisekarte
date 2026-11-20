import { useEffect, useRef } from 'react'

/**
 * Calls `task` now and then every `intervalMs`, paused while the tab is in
 * the background and run at once when it comes back.
 */
export function usePolling(task: () => Promise<unknown>, intervalMs: number) {
  const taskRef = useRef(task)

  useEffect(() => {
    taskRef.current = task
  })

  useEffect(() => {
    let timer = 0
    let running = false
    let stopped = false

    async function run() {
      if (running || stopped) return
      if (document.visibilityState === 'visible') {
        running = true
        try {
          await taskRef.current()
        } catch {
          // The task shows its own errors; keep polling.
        } finally {
          running = false
        }
      }
      if (!stopped) timer = window.setTimeout(run, intervalMs)
    }

    function handleVisibility() {
      if (document.visibilityState === 'visible') {
        window.clearTimeout(timer)
        void run()
      }
    }

    void run()
    document.addEventListener('visibilitychange', handleVisibility)

    return () => {
      stopped = true
      window.clearTimeout(timer)
      document.removeEventListener('visibilitychange', handleVisibility)
    }
  }, [intervalMs])
}
