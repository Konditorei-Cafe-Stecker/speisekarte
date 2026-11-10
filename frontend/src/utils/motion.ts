import { useRef } from 'react'
import { gsap } from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'
import { useGSAP } from '@gsap/react'

// The only place where GSAP plugins are registered. Components import
// gsap, ScrollTrigger and useGSAP from here, never from the packages.
gsap.registerPlugin(ScrollTrigger, useGSAP)

// On phones the address bar showing and hiding changes the window height.
// That is not worth re-measuring every animation for.
ScrollTrigger.config({ ignoreMobileResize: true })

/** Animations are created only when the visitor has not asked for less
 *  motion. Otherwise the page simply shows its final, static state. */
export const MOTION_OK = '(prefers-reduced-motion: no-preference)'

/** Scroll-linked animations follow the scroll position with a short
 *  catch-up, so they play forwards and backwards every time. */
export const SCRUB = 0.6

/**
 * Generic entrance for headers and blocks: fade in while moving up.
 * `data-rise-delay="1"` (or 2) lets neighbours start slightly later.
 * Uses opacity only, so links inside stay reachable by keyboard.
 */
export function addRise(elements: Iterable<Element>) {
  for (const element of elements) {
    const delay = Number((element as HTMLElement).dataset.riseDelay ?? 0)

    gsap.fromTo(
      element,
      { opacity: 0, y: 64 },
      {
        opacity: 1,
        y: 0,
        ease: 'none',
        scrollTrigger: {
          trigger: element,
          start: `top ${100 - delay * 6}%`,
          end: `top ${60 - delay * 6}%`,
          scrub: SCRUB,
        },
      },
    )
  }
}

/**
 * For a whole page or section: every element marked `data-rise` inside
 * the returned ref gets the entrance above. Triggers are removed again
 * when the component leaves the page.
 */
export function useRise<T extends HTMLElement>() {
  const scopeRef = useRef<T>(null)

  useGSAP(
    () => {
      const mm = gsap.matchMedia(scopeRef)

      mm.add(MOTION_OK, () => {
        const scope = scopeRef.current
        if (scope) addRise(scope.querySelectorAll('[data-rise]'))
      })
    },
    { scope: scopeRef },
  )

  return scopeRef
}

export { gsap, ScrollTrigger, useGSAP }
