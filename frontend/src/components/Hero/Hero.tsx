import { useRef } from 'react'
import { Link } from 'react-router-dom'
import './Hero.css'
import ArrowIcon from '../ArrowIcon/ArrowIcon'
import { site } from '../../content/site'
import heroAvif800 from '../../assets/photos/hero-800.avif'
import heroAvif1180 from '../../assets/photos/hero-1180.avif'
import heroFallback from '../../assets/photos/hero-800.jpg'
import { MOTION_OK, SCRUB, gsap, useGSAP } from '../../utils/motion'

function Hero() {
  const heroRef = useRef<HTMLElement>(null)

  useGSAP(
    () => {
      const mm = gsap.matchMedia(heroRef)

      mm.add(MOTION_OK, () => {
        // The headline fades and lifts away while the hero leaves the screen.
        gsap.to('.hero-title-wrap', {
          opacity: 0.15,
          y: -70,
          scale: 0.96,
          ease: 'none',
          scrollTrigger: {
            trigger: heroRef.current,
            start: 'top top',
            end: 'bottom top',
            scrub: SCRUB,
          },
        })

        // Parallax: the photo is slightly enlarged and drifts in its frame.
        gsap.fromTo(
          '.hero-photo img',
          { yPercent: -7, scale: 1.14 },
          {
            yPercent: 7,
            scale: 1.14,
            ease: 'none',
            scrollTrigger: {
              trigger: '.hero-photo',
              start: 'top bottom',
              end: 'bottom top',
              scrub: SCRUB,
            },
          },
        )
      })
    },
    { scope: heroRef },
  )

  return (
    <section
      ref={heroRef}
      className="container hero"
      aria-labelledby="hero-title"
    >
      <p className="eyebrow hero-eyebrow">
        Handwerk · Tradition · Bremer Innenstadt
      </p>

      <div className="hero-title-wrap">
        <h1 id="hero-title">
          Tradition, die
          <br />
          man <em>schmeckt.</em>
        </h1>
      </div>

      <div className="hero-grid">
        <div className="hero-copy">
          <p className="hero-description">
            Mit Liebe gebacken, mit Sorgfalt verziert und seit Generationen
            ein Stück Zuhause – mitten in Bremen.
          </p>

          <div className="hero-actions">
            <Link to="/menu" className="btn btn--accent">
              Zur Speisekarte
              <ArrowIcon />
            </Link>

            <Link to="/tortenservice" className="hero-secondary-link">
              Torten individuell anfragen
            </Link>
          </div>

          <div className="hero-info">
            <span>Heute geöffnet · {site.hoursToday}</span>
            <span>
              {site.street}, {site.city}
            </span>
          </div>
        </div>

        <div className="hero-visual">
          <div className="hero-photo">
            {/* Above the fold on desktop: loaded eagerly with high priority. */}
            <picture>
              <source
                type="image/avif"
                srcSet={`${heroAvif800} 800w, ${heroAvif1180} 1180w`}
                sizes="(min-width: 1296px) 600px, (min-width: 700px) 46vw, 92vw"
              />
              <img
                src={heroFallback}
                alt="Tortentheke im Café Stecker"
                width={1180}
                height={1086}
                fetchPriority="high"
              />
            </picture>
          </div>

          <div className="hero-badge">
            <svg
              className="hero-badge-ring"
              aria-hidden="true"
              viewBox="0 0 132 132"
              width="132"
              height="132"
            >
              <defs>
                <path
                  id="hero-badge-path"
                  d="M66 66 m-52 0 a52 52 0 1 1 104 0 a52 52 0 1 1 -104 0"
                />
              </defs>
              <text>
                {/* textLength spreads the words evenly around the full circle. */}
                <textPath href="#hero-badge-path" textLength="322">
                  KONDITOREI · CAFÉ · STECKER · BREMEN ·
                </textPath>
              </text>
            </svg>
            <span className="hero-badge-year">
              <span className="visually-hidden">seit </span>1908
            </span>
          </div>
        </div>
      </div>
    </section>
  )
}

export default Hero
