import { useEffect, useRef } from 'react'
import { useLocation } from 'react-router-dom'
import { scrollToTarget } from '../../utils/scroll'

type Visit = { key: string; pathname: string }

/**
 * Keeps the scroll position sensible when the route changes:
 * - a new page starts at the top,
 * - a link with a hash (for example /#besuch) scrolls to that section,
 * - on the same page the movement is smooth unless reduced motion is set.
 */
function ScrollToTop() {
  const { pathname, hash, key } = useLocation()
  const current = useRef<Visit | null>(null)
  const previous = useRef<Visit | null>(null)

  useEffect(() => {
    if (current.current?.key !== key) {
      previous.current = current.current
      current.current = { key, pathname }
    }

    const firstVisit = previous.current === null
    const samePage = previous.current?.pathname === pathname
    const reducedMotion = window.matchMedia(
      '(prefers-reduced-motion: reduce)',
    ).matches
    const smooth = samePage && !reducedMotion

    if (!hash) {
      // On the first load the browser may restore the old position itself.
      if (!firstVisit) scrollToTarget(0, smooth)
      return
    }

    let id: string
    try {
      id = decodeURIComponent(hash.slice(1))
    } catch {
      return
    }

    // The new page is already rendered when this effect runs. A timer is
    // not used on purpose: browsers pause those in background tabs.
    const target = document.getElementById(id)
    if (target) scrollToTarget(target, smooth)
  }, [pathname, hash, key])

  return null
}

export default ScrollToTop
