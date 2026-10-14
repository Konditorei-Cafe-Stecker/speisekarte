
import { useState } from 'react'
import './Hero.css'
import heroImage from '../../assets/hero-bakery.jpg'

type DoodleType = 'coffee' | 'cake' | 'wheat'

function Hero() {
  const [activeDoodle, setActiveDoodle] = useState<DoodleType | null>(null)

  function toggleDoodle(doodle: DoodleType) {
    setActiveDoodle((current) => (current === doodle ? null : doodle))
  }

  return (
    <section className="hero" aria-labelledby="hero-title">
      <div className="hero-main">
        <div className="hero-content">
          <p className="hero-eyebrow">
            HANDWERK · TRADITION · SEIT 1908
          </p>

          <h1 id="hero-title">
            Tradition, die
            <br />
            man schmeckt.
          </h1>

          <p className="hero-description">
            Mit Liebe gebacken, mit Sorgfalt verziert und
            <br className="hero-description-break" />
            seit Generationen ein Stück Zuhause.
          </p>

          <div className="hero-buttons">
            <a href="#spezialitaeten" className="hero-button primary">
              Unsere Spezialitäten
            </a>

            <a href="/tortenservice" className="hero-secondary-link">
              Torten individuell anfragen ↗
            </a>
          </div>
        </div>

        <div className="hero-visual">
          <img
            src={heroImage}
            alt="Kuchen und feines Gebäck in einer Konditorei"
          />

          {/* Interactive coffee doodle */}
          <button
            type="button"
            className="hero-doodle hero-doodle--coffee"
            aria-label="Kaffee-Doodle: Details anzeigen"
            aria-expanded={activeDoodle === 'coffee'}
            onClick={() => toggleDoodle('coffee')}
          >
            <svg viewBox="0 0 64 64" aria-hidden="true">
              <path d="M13 24h32v14a12 12 0 0 1-12 12h-8a12 12 0 0 1-12-12Z" />
              <path d="M45 29h4a7 7 0 0 1 0 14h-5" />
              <path d="M20 17c-5-5 4-7 0-12" />
              <path d="M31 17c-5-5 4-7 0-12" />
              <path d="M10 53h40" />
              <path d="M20 29c3 4 7 4 10 0s7-4 10 0" />
            </svg>

            {activeDoodle === 'coffee' && (
              <span className="hero-doodle-note" role="status">
                Eine kleine Pause mit gutem Kaffee ☕
              </span>
            )}
          </button>

          {/* Interactive cake doodle */}
          <button
            type="button"
            className="hero-doodle hero-doodle--cake"
            aria-label="Kuchen-Doodle: Details anzeigen"
            aria-expanded={activeDoodle === 'cake'}
            onClick={() => toggleDoodle('cake')}
          >
            <svg viewBox="0 0 64 64" aria-hidden="true">
              <path d="m10 36 35-19 9 29-35 10Z" />
              <path d="m10 36 35-19-3-7-35 19Z" />
              <path d="m19 56-2-7 35-19" />
              <path d="M16 29c4-5 7 3 11-2s7 2 11-3" />
              <path d="M25 23v5M36 17v5M44 30l4 3" />
              <path d="M18 59h36" />
            </svg>

            {activeDoodle === 'cake' && (
              <span className="hero-doodle-note" role="status">
                Mit Liebe gebacken, Stück für Stück.
              </span>
            )}
          </button>

          {/* Interactive wheat doodle */}
          <button
            type="button"
            className="hero-doodle hero-doodle--wheat"
            aria-label="Handwerk-Doodle: Details anzeigen"
            aria-expanded={activeDoodle === 'wheat'}
            onClick={() => toggleDoodle('wheat')}
          >
            <svg viewBox="0 0 64 64" aria-hidden="true">
              <path d="M15 54C28 42 36 27 42 9" />
              <path d="M24 43c-9 0-14-5-14-12 9 0 14 5 14 12Z" />
              <path d="M30 33c-8-2-12-8-10-15 9 3 13 8 10 15Z" />
              <path d="M36 22c-7-4-9-10-5-16 8 5 10 10 5 16Z" />
              <path d="M25 43c7-1 12-5 13-12-8 1-12 5-13 12Z" />
              <path d="M33 28c7-3 10-9 8-15-8 3-10 8-8 15Z" />
              <path d="M11 56h31" />
            </svg>

            {activeDoodle === 'wheat' && (
              <span className="hero-doodle-note" role="status">
                Tradition und echtes Konditorhandwerk.
              </span>
            )}
          </button>

          {/* Feature card to fill the whitespace on the right */}
          <a
            href="#spezialitaeten"
            className="hero-side-note"
            aria-label="Unsere Spezialitäten entdecken"
          >
            <span className="hero-side-note-mark" aria-hidden="true">
              ✳
            </span>

            <span className="hero-side-note-eyebrow">
              AUS DER BACKSTUBE
            </span>

            <strong>Mit Liebe gemacht.</strong>

            <span className="hero-side-note-bottom">
              Kuchen, Kaffee und Gebäck <span aria-hidden="true">↗</span>
            </span>
          </a>
        </div>
      </div>

      <div className="hero-footer">
        <p className="hero-trust">
          FEINE KÖSTLICHKEITEN&nbsp; · &nbsp;AROMATISCHER KAFFEE&nbsp; ·
          &nbsp;FRISCHE BACKWAREN
        </p>
      </div>
    </section>
  )
}

export default Hero
