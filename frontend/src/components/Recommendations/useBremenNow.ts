import { useEffect, useState } from 'react'
import { bremenNow } from '../../api/recommendations'

/** Date and time in Bremen ("2026-11-23 14:05"), updated every minute. */
export function useBremenNow() {
  const [now, setNow] = useState(() => bremenNow())

  useEffect(() => {
    const timer = window.setInterval(() => setNow(bremenNow()), 60_000)
    return () => window.clearInterval(timer)
  }, [])

  return now
}
