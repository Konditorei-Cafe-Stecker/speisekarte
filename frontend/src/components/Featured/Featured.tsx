import { useRef } from 'react'
import { Link } from 'react-router-dom'
import './Featured.css'
import ArrowIcon from '../ArrowIcon/ArrowIcon'
import DeferredPicture from '../DeferredPicture/DeferredPicture'
import { MOTION_OK, SCRUB, addRise, gsap, useGSAP } from '../../utils/motion'
import { scrollToTarget } from '../../utils/scroll'

import kuchen480 from '../../assets/photos/kuchen-480.avif'
import kuchen800 from '../../assets/photos/kuchen-800.avif'
import kuchen1254 from '../../assets/photos/kuchen-1254.avif'
import kuchenFallback from '../../assets/photos/kuchen-480.jpg'
import kaffee480 from '../../assets/photos/kaffee-480.avif'
import kaffee800 from '../../assets/photos/kaffee-800.avif'
import kaffee1254 from '../../assets/photos/kaffee-1254.avif'
import kaffeeFallback from '../../assets/photos/kaffee-480.jpg'
import gebaeck480 from '../../assets/photos/gebaeck-480.avif'
import gebaeck800 from '../../assets/photos/gebaeck-800.avif'
import gebaeck1254 from '../../assets/photos/gebaeck-1254.avif'
import gebaeckFallback from '../../assets/photos/gebaeck-480.jpg'

/** The sideways gallery needs room: a wide and reasonably tall window,
 *  and a visitor who has not asked for less motion. */
const GALLERY = `(min-width: 1024px) and (min-height: 620px) and ${MOTION_OK}`

// The links use category ids that really exist on the Speisekarte
// (getranke, speisen, eis-desserts) or the Speisekarte itself.
const cards = [
  {
    title: 'Kuchen & Torten',
    text: 'Klassische Lieblingsstücke zum Genießen.',
    to: '/menu',
    avif: `${kuchen480} 480w, ${kuchen800} 800w, ${kuchen1254} 1254w`,
    fallback: kuchenFallback,
    alt: 'Frisch zubereiteter Kuchen',
  },
  {
    title: 'Kaffee & Tee',
    text: 'Aromatische Kaffeespezialitäten zum Verweilen.',
    to: '/menu#getranke',
    avif: `${kaffee480} 480w, ${kaffee800} 800w, ${kaffee1254} 1254w`,
    fallback: kaffeeFallback,
    alt: 'Kaffee im Café Stecker',
  },
  {
    title: 'Feines Gebäck',
    text: 'Kleine Köstlichkeiten aus unserer Backstube.',
    to: '/menu#speisen',
    avif: `${gebaeck480} 480w, ${gebaeck800} 800w, ${gebaeck1254} 1254w`,
    fallback: gebaeckFallback,
    alt: 'Frisches Gebäck aus der Backstube',
  },
]

function Featured() {
  const sectionRef = useRef<HTMLElement>(null)

  useGSAP(
    () => {
      const mm = gsap.matchMedia(sectionRef)

      // Desktop: the section stays in place while the headline and the
      // cards travel sideways with the scroll, as one long strip.
      // Everywhere else it is a plain grid.
      mm.add(GALLERY, () => {
        const section = sectionRef.current
        const found = section?.querySelector<HTMLElement>('.featured-strip')
        const parent = found?.parentElement
        if (!section || !found || !parent) return

        const strip: HTMLElement = found
        const frame: HTMLElement = parent

        section.classList.add('is-gallery')

        // Width the strip is shown in: the container without its padding.
        const room = () => {
          const style = getComputedStyle(frame)
          return (
            frame.clientWidth -
            parseFloat(style.paddingLeft) -
            parseFloat(style.paddingRight)
          )
        }
        // How much of the strip does not fit, which is how far it travels.
        const distance = () => Math.max(0, strip.scrollWidth - room())
        const headerHeight = () =>
          document.querySelector<HTMLElement>('.site-header')?.offsetHeight ??
          0

        const tween = gsap.to(strip, {
          x: () => -distance(),
          ease: 'none',
          scrollTrigger: {
            trigger: section,
            start: () => `top ${headerHeight()}px`,
            end: () => `+=${distance()}`,
            pin: true,
            scrub: SCRUB,
            invalidateOnRefresh: true,
          },
        })

        // Keyboard: a card reached with Tab may sit beside the screen.
        // Scroll the page to the point where that card is in view.
        function showFocusedCard(event: FocusEvent) {
          const card = (event.target as Element).closest<HTMLElement>(
            '.featured-card',
          )
          const trigger = tween.scrollTrigger
          const total = distance()
          if (!card || !trigger || total === 0) return
          if (!card.matches(':focus-visible')) return

          // The card is fully visible while the strip has moved between
          // these two amounts.
          const shown = -Number(gsap.getProperty(strip, 'x'))
          const most = Math.min(card.offsetLeft, total)
          const least = Math.max(
            0,
            card.offsetLeft + card.offsetWidth - room(),
          )
          if (shown >= least && shown <= most) return

          const wanted = shown < least ? least : most
          scrollToTarget(
            trigger.start + (wanted / total) * (trigger.end - trigger.start),
            false,
          )
        }

        strip.addEventListener('focusin', showFocusedCard)

        return () => {
          strip.removeEventListener('focusin', showFocusedCard)
          section.classList.remove('is-gallery')
        }
      })

      mm.add(MOTION_OK, () => {
        const section = sectionRef.current
        if (!section) return

        addRise(section.querySelectorAll('[data-rise]'))

        // Photos are unveiled from the bottom up; cards 2 and 3 start a
        // little later than the first one.
        section
          .querySelectorAll('.featured-image')
          .forEach((frame, index) => {
            gsap.fromTo(
              frame,
              { clipPath: 'inset(100% 0% 0% 0%)' },
              {
                clipPath: 'inset(0% 0% 0% 0%)',
                ease: 'none',
                scrollTrigger: {
                  trigger: frame,
                  start: `${index * 20}% bottom`,
                  end: index < 2 ? `${90 + index * 5}% bottom` : 'bottom 85%',
                  scrub: SCRUB,
                },
              },
            )
          })
      })
    },
    { scope: sectionRef },
  )

  return (
    // The link target is this wrapper, not the section: while the gallery
    // holds the section in place, the wrapper still marks where it starts.
    <div id="spezialitaeten">
      <section
        ref={sectionRef}
        className="featured"
        aria-labelledby="featured-title"
      >
        <div className="container featured-inner">
          <div className="featured-strip">
            <div className="featured-header" data-rise>
              <div>
                <p className="eyebrow">Aus Backstube &amp; Café</p>
                <h2 id="featured-title" className="section-title">
                  Unsere Spezialitäten
                </h2>
              </div>

              <p className="featured-description">
                Mit Sorgfalt gemacht und für besondere Momente gedacht.
              </p>
            </div>

            <div className="featured-grid">
              {cards.map((card, index) => (
                <Link
                  to={card.to}
                  className="featured-card"
                  key={card.title}
                  data-rise
                  data-rise-delay={index}
                >
                  <div className="featured-image">
                    <DeferredPicture
                      avif={card.avif}
                      sizes="(min-width: 1024px) 600px, (min-width: 600px) 46vw, 92vw"
                      fallback={card.fallback}
                      alt={card.alt}
                      width={800}
                      height={600}
                      lazy={false}
                    />
                  </div>

                  <div className="featured-card-head">
                    <h3>{card.title}</h3>
                    <span className="featured-card-number" aria-hidden="true">
                      {String(index + 1).padStart(2, '0')}
                    </span>
                  </div>

                  <p>{card.text}</p>

                  <span className="featured-card-link">
                    Entdecken
                    <ArrowIcon />
                  </span>
                </Link>
              ))}
            </div>
          </div>
        </div>
      </section>
    </div>
  )
}

export default Featured
