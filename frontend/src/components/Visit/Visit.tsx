import { Link } from 'react-router-dom'
import './Visit.css'
import { site } from '../../content/site'
import { useRise } from '../../utils/motion'

function Visit() {
  const sectionRef = useRise<HTMLElement>()

  return (
    <section
      ref={sectionRef}
      className="visit"
      id="besuch"
      aria-labelledby="visit-title"
    >
      <div className="container visit-inner">
        <h2 id="visit-title" className="section-title" data-rise>
          Besuch uns in <em>Bremen.</em>
        </h2>

        <div className="visit-grid">
          <div className="visit-column" data-rise>
            <h3 className="eyebrow">Öffnungszeiten</h3>
            <p>
              {site.hours.map((entry) => (
                <span key={entry.days}>
                  {entry.days} · {entry.time}
                </span>
              ))}
            </p>
          </div>

          <div className="visit-column" data-rise data-rise-delay={1}>
            <h3 className="eyebrow">Adresse</h3>
            <p>
              <span>{site.name}</span>
              <span>{site.street}</span>
              <span>
                {site.postcode} {site.city}
              </span>
            </p>
            <a
              className="text-link"
              href={site.routeUrl}
              target="_blank"
              rel="noopener noreferrer"
            >
              Route planen
              <span className="visually-hidden"> (öffnet Google Maps)</span>
            </a>
          </div>

          <div className="visit-column" data-rise data-rise-delay={2}>
            <h3 className="eyebrow">Tortenservice</h3>
            <p>Individuelle Torten für Hochzeit, Geburtstag und Feier.</p>
            <Link className="text-link" to="/tortenservice">
              Torte anfragen
            </Link>
            <a className="text-link" href={site.phoneHref}>
              Telefon · {site.phone}
            </a>
          </div>
        </div>
      </div>
    </section>
  )
}

export default Visit
