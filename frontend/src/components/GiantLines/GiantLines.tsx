import { useRef } from 'react'
import './GiantLines.css'
import { MOTION_OK, SCRUB, gsap, useGSAP } from '../../utils/motion'

/** Two oversized decorative lines of type between the sections. */
function GiantLines() {
  const linesRef = useRef<HTMLDivElement>(null)

  useGSAP(
    () => {
      const mm = gsap.matchMedia(linesRef)

      mm.add(MOTION_OK, () => {
        // The two lines travel in opposite directions while in view.
        const scrollTrigger = {
          trigger: linesRef.current,
          start: 'top bottom',
          end: 'bottom top',
          scrub: SCRUB,
        }

        gsap.fromTo(
          '.giant-line--italic',
          { xPercent: 6 },
          { xPercent: -22, ease: 'none', scrollTrigger },
        )
        gsap.fromTo(
          '.giant-line--blue',
          { xPercent: -24 },
          { xPercent: 2, ease: 'none', scrollTrigger: { ...scrollTrigger } },
        )
      })
    },
    { scope: linesRef },
  )

  return (
    <div ref={linesRef} className="giant-lines" aria-hidden="true">
      {/* The words are drawn by CSS (::before) because they are decoration,
          not content: the same words appear as real text elsewhere. */}
      <div
        className="giant-line giant-line--italic"
        data-text="Konditorei Café Stecker — seit 1908 — Konditorei Café Stecker"
      />
      <div
        className="giant-line giant-line--blue"
        data-text="Torten — Kuchen — Gebäck — Kaffee — Eis — Torten — Kuchen — Gebäck — Kaffee"
      />
    </div>
  )
}

export default GiantLines
