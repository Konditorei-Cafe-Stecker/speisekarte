import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import './MenuTeaser.css'
import ArrowIcon from '../ArrowIcon/ArrowIcon'
import { fetchMenuItems } from '../../api/menu'
import type { MenuItem } from '../../api/menu'
import { menuTeaser } from '../../content/site'
import { formatCents, toCents } from '../../utils/format'
import {
  MOTION_OK,
  SCRUB,
  ScrollTrigger,
  addRise,
  gsap,
  useGSAP,
} from '../../utils/motion'

type TeaserState =
  | { status: 'loading' }
  | { status: 'error' }
  | { status: 'ready'; items: MenuItem[] }

function MenuTeaser() {
  const [state, setState] = useState<TeaserState>({ status: 'loading' })
  const sectionRef = useRef<HTMLElement>(null)

  // Names and prices come from the real menu, so they are never out of date.
  useEffect(() => {
    const controller = new AbortController()

    fetchMenuItems(controller.signal)
      .then((items) => {
        if (controller.signal.aborted) return

        setState({ status: 'ready', items })
      })
      .catch(() => {
        if (controller.signal.aborted) return

        setState({ status: 'error' })
      })

    return () => controller.abort()
  }, [])

  const rows =
    state.status === 'ready'
      ? menuTeaser.flatMap((entry) => {
          const item = state.items.find((menuItem) => menuItem.id === entry.id)
          if (!item) return []

          return [
            {
              id: item.id,
              title: entry.title ?? item.name,
              detail: entry.detail ?? item.description,
              price: formatCents(toCents(item.price)),
            },
          ]
        })
      : []

  // Runs again when the rows arrive, because they only exist then.
  useGSAP(
    () => {
      const mm = gsap.matchMedia(sectionRef)

      mm.add(MOTION_OK, () => {
        const section = sectionRef.current
        if (!section) return

        addRise(section.querySelectorAll('[data-rise]'))

        // Rows slide in from alternating sides.
        section
          .querySelectorAll('.menu-teaser-row:not(.menu-teaser-row--loading)')
          .forEach((row, index) => {
            gsap.fromTo(
              row,
              { opacity: 0, x: index % 2 === 0 ? 120 : -120 },
              {
                opacity: 1,
                x: 0,
                ease: 'none',
                scrollTrigger: {
                  trigger: row,
                  start: 'top bottom',
                  end: 'top 65%',
                  scrub: SCRUB,
                },
              },
            )
          })
      })

      // The loaded rows change the height of the page; every scroll
      // animation below has to measure its position again.
      if (rows.length > 0) ScrollTrigger.refresh()
    },
    { scope: sectionRef, dependencies: [rows.length], revertOnUpdate: true },
  )

  return (
    <section
      ref={sectionRef}
      className="container menu-teaser"
      aria-labelledby="teaser-title"
    >
      <div className="menu-teaser-grid">
        <div className="menu-teaser-intro" data-rise>
          <p className="eyebrow">Aus der Karte</p>
          <h2 id="teaser-title" className="section-title">
            Ein Blick in die Speisekarte
          </h2>
          <p className="menu-teaser-description">
            Am Tisch auswählen, Bestellung zeigen – oder schon zu Hause
            stöbern.
          </p>
          <Link to="/menu" className="btn btn--dark">
            Ganze Speisekarte
            <ArrowIcon />
          </Link>
        </div>

        <div className="menu-teaser-list" aria-busy={state.status === 'loading'}>
          {state.status === 'loading' &&
            menuTeaser.map((entry) => (
              <div
                className="menu-teaser-row menu-teaser-row--loading"
                key={entry.id}
                aria-hidden="true"
              >
                <span />
                <span />
              </div>
            ))}

          {rows.map((row) => (
            <div className="menu-teaser-row" key={row.id}>
              <div className="menu-teaser-row-top">
                <h3>{row.title}</h3>
                <span className="menu-teaser-leader" aria-hidden="true" />
                <span className="menu-teaser-price">{row.price}</span>
              </div>
              {row.detail && <p>{row.detail}</p>}
            </div>
          ))}

          {state.status !== 'loading' && rows.length === 0 && (
            <p className="menu-teaser-fallback">
              Die Auswahl kann gerade nicht geladen werden. Die ganze Karte
              findest du auf der Seite Speisekarte.
            </p>
          )}
        </div>
      </div>
    </section>
  )
}

export default MenuTeaser
