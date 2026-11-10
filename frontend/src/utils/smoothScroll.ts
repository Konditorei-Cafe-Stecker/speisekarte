import { useEffect } from 'react'
import { MOTION_OK, ScrollTrigger, gsap } from './motion'
import { setSmoothScroller } from './scroll'

/** Mouse or trackpad, and the visitor has not asked for less motion.
 *  Phones and tablets keep their own native scrolling. */
const SMOOTH_OK = `(hover: hover) and (pointer: fine) and ${MOTION_OK}`

/**
 * Smooth wheel scrolling (Lenis) for a page with scroll animations.
 * Call it once in the page component. The library is downloaded only
 * when it will actually be used, so phones never load it.
 */
export function useSmoothScroll() {
  useEffect(() => {
    if (!window.matchMedia(SMOOTH_OK).matches) return

    let cleanUp: (() => void) | undefined
    let active = true

    import('lenis')
      .then(({ default: Lenis }) => {
        if (!active) return

        // Text areas, the mobile menu and other scrollable boxes keep
        // scrolling by themselves.
        const lenis = new Lenis({ allowNestedScroll: true })

        // Scroll animations follow the smoothed position, and both run on
        // the same clock.
        lenis.on('scroll', ScrollTrigger.update)
        const tick = (time: number) => lenis.raf(time * 1000)
        gsap.ticker.add(tick)
        gsap.ticker.lagSmoothing(0)

        setSmoothScroller((target, smooth) => {
          lenis.scrollTo(target, { immediate: !smooth })
        })

        cleanUp = () => {
          setSmoothScroller(null)
          gsap.ticker.remove(tick)
          gsap.ticker.lagSmoothing(500, 33)
          lenis.destroy()
        }
      })
      .catch(() => {
        // Without the library the page simply scrolls natively.
      })

    return () => {
      active = false
      cleanUp?.()
    }
  }, [])
}
