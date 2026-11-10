import { useEffect } from 'react'

/**
 * One place for "scroll the page to here".
 *
 * Normally the browser does it. On pages with smooth scrolling (see
 * smoothScroll.ts) that library has to do it instead, otherwise the two
 * would fight over the scroll position. This file knows nothing about the
 * library, so pages without it do not load it.
 */
type ScrollTarget = number | HTMLElement
type Scroller = (target: ScrollTarget, smooth: boolean) => void

let smoothScroller: Scroller | null = null

/** Called by the smooth-scrolling hook while it is active. */
export function setSmoothScroller(scroller: Scroller | null) {
  smoothScroller = scroller
}

/** Scroll to a position in pixels or to the top of an element. */
export function scrollToTarget(target: ScrollTarget, smooth: boolean) {
  if (smoothScroller) {
    smoothScroller(target, smooth)
    return
  }

  const behavior: ScrollBehavior = smooth ? 'smooth' : 'instant'

  if (typeof target === 'number') {
    window.scrollTo({ top: target, left: 0, behavior })
  } else {
    target.scrollIntoView({ behavior, block: 'start' })
  }
}

/**
 * Opened with a section link such as /#besuch: pages load as their own
 * chunk, so the section may not have existed yet when the router looked.
 * Call this in a page that has link targets inside it.
 */
export function useScrollToHashOnMount() {
  useEffect(() => {
    let id: string
    try {
      id = decodeURIComponent(window.location.hash.slice(1))
    } catch {
      return
    }

    const target = id ? document.getElementById(id) : null
    target?.scrollIntoView({ behavior: 'instant', block: 'start' })
  }, [])
}
