import {
  useEffect,
  useRef,
  useState,
  type CSSProperties,
  type FormEvent,
} from 'react'
import { Link } from 'react-router-dom'
import './Tortenservice.css'
import ArrowIcon from '../components/ArrowIcon/ArrowIcon'
import NumberedList from '../components/NumberedList/NumberedList'
import cake420 from '../assets/photos/torte-420.avif'
import cake648 from '../assets/photos/torte-648.avif'
import cakeFallback from '../assets/photos/torte-648.jpg'
import { useScrollToHashOnMount } from '../utils/scroll'
import { useRise } from '../utils/motion'
import { useSmoothScroll } from '../utils/smoothScroll'

const steps = [
  {
    title: 'Wünsche teilen',
    text: 'Erzähl uns von deinem Anlass, deiner Idee und den Menschen, mit denen du feiern möchtest.',
  },
  {
    title: 'Gemeinsam planen',
    text: 'Wir besprechen Geschmack, Größe und Gestaltung ganz in Ruhe mit dir.',
  },
  {
    title: 'Genuss abholen',
    text: 'Deine Torte wartet frisch für deinen besonderen Moment auf dich.',
  },
]

/** Order of the short load-in on the first screen. */
function step(index: number) {
  return { '--load-step': index } as CSSProperties
}

function Tortenservice() {
  useSmoothScroll()
  const [submitted, setSubmitted] = useState(false)
  const pageRef = useRise<HTMLDivElement>()
  const successRef = useRef<HTMLParagraphElement>(null)

  useScrollToHashOnMount()

  // The submit button is replaced by the message; keep keyboard focus there.
  useEffect(() => {
    if (submitted) successRef.current?.focus()
  }, [submitted])

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitted(true)
  }

  return (
    <div ref={pageRef} className="tortenservice">
      <section
        className="container tortenservice-hero"
        aria-labelledby="tortenservice-title"
      >
        <div className="tortenservice-header">
          <p className="eyebrow load-in">Tortenservice · Nach deinen Wünschen</p>

          <h1 id="tortenservice-title" className="load-in" style={step(1)}>
            Deine Torte.
            <br />
            Dein besonderer <em>Moment.</em>
          </h1>

          <p className="tortenservice-description load-in" style={step(2)}>
            Wir fertigen individuelle Torten für Geburtstage, Hochzeiten und
            andere besondere Anlässe.
          </p>

          <Link
            className="btn btn--accent tortenservice-down load-in"
            style={step(3)}
            to="#tortenanfrage"
          >
            Anfrage starten
            <ArrowIcon />
          </Link>
        </div>

        <div className="tortenservice-visual load-in-move" style={step(3)}>
          <div className="tortenservice-photo">
            {/* First screen on desktop: loaded eagerly with high priority. */}
            <picture>
              <source
                type="image/avif"
                srcSet={`${cake420} 420w, ${cake648} 648w`}
                sizes="(min-width: 800px) 40vw, (min-width: 520px) 480px, 92vw"
              />
              <img
                src={cakeFallback}
                alt="Individuell dekorierte Torte aus der Konditorei"
                width={648}
                height={736}
                fetchPriority="high"
              />
            </picture>
          </div>

          <p className="tortenservice-stamp" aria-hidden="true">
            Mit Liebe
            <br />
            gemacht
          </p>
        </div>
      </section>

      <NumberedList label="So entsteht deine Torte" items={steps} ordered />

      <section
        className="tortenservice-offer"
        aria-labelledby="tortenservice-offer-title"
      >
        <div className="container tortenservice-offer-inner" data-rise>
          <p className="eyebrow">Deine Idee, unser Handwerk</p>
          <h2 id="tortenservice-offer-title" className="section-title">
            Individuelle Torten nach deinen <em>Wünschen</em>
          </h2>
          <p>
            Gemeinsam gestalten wir eine Torte, die zu deinem Anlass passt –
            von der Größe über die Geschmacksrichtung bis zur Dekoration.
          </p>
          <Link className="btn btn--dark tortenservice-down" to="#tortenanfrage">
            Torte anfragen
            <ArrowIcon />
          </Link>
        </div>
      </section>

      <section
        id="tortenanfrage"
        className="tortenanfrage"
        aria-labelledby="tortenanfrage-title"
      >
        <div className="tortenanfrage-header">
          <p className="eyebrow">Deine Anfrage</p>
          <h2 id="tortenanfrage-title" className="section-title">
            Erzähl uns von deiner <em>Wunschtorte.</em>
          </h2>
          <p>
            Teile uns deine Wünsche mit. Wir besprechen anschließend die
            Details mit dir.
          </p>
        </div>

        <form className="tortenanfrage-form" onSubmit={handleSubmit}>
          <div className="form-group">
            <label htmlFor="name">Dein Name *</label>
            <input
              type="text"
              id="name"
              name="name"
              placeholder="Vor- und Nachname"
              autoComplete="name"
              required
            />
          </div>

          <div className="form-row">
            <div className="form-group">
              <label htmlFor="email">E-Mail-Adresse *</label>
              <input
                type="email"
                id="email"
                name="email"
                placeholder="name@beispiel.de"
                autoComplete="email"
                required
              />
            </div>

            <div className="form-group">
              <label htmlFor="phone">Telefonnummer</label>
              <input
                type="tel"
                id="phone"
                name="phone"
                placeholder="Deine Telefonnummer"
                autoComplete="tel"
              />
            </div>
          </div>

          <div className="form-row">
            <div className="form-group">
              <label htmlFor="occasion">Anlass *</label>
              <select id="occasion" name="occasion" required defaultValue="">
                <option value="" disabled>
                  Bitte auswählen
                </option>
                <option value="geburtstag">Geburtstag</option>
                <option value="hochzeit">Hochzeit</option>
                <option value="taufe">Taufe</option>
                <option value="kommunion">Kommunion / Konfirmation</option>
                <option value="jubilaeum">Jubiläum</option>
                <option value="sonstiges">Sonstiger Anlass</option>
              </select>
            </div>

            <div className="form-group">
              <label htmlFor="pickupDate">Wunsch-Abholdatum *</label>
              <input
                type="date"
                id="pickupDate"
                name="pickupDate"
                min={new Date().toLocaleDateString('en-CA')}
                required
              />
            </div>
          </div>

          <div className="form-group">
            <label htmlFor="people">Für wie viele Personen? *</label>
            <input
              type="number"
              id="people"
              name="people"
              min="1"
              max="500"
              inputMode="numeric"
              placeholder="z. B. 12"
              required
            />
          </div>

          <div className="form-row">
            <div className="form-group">
              <label htmlFor="cakeType">Tortenart</label>
              <select id="cakeType" name="cakeType" defaultValue="">
                <option value="">Noch offen</option>
                <option value="sahnetorte">Sahnetorte</option>
                <option value="cremetorte">Cremetorte</option>
                <option value="motivorte">Motivtorte</option>
                <option value="hochzeitstorte">Hochzeitstorte</option>
                <option value="sonstige">Sonstige</option>
              </select>
            </div>

            <div className="form-group">
              <label htmlFor="flavour">Geschmacksrichtung</label>
              <input
                type="text"
                id="flavour"
                name="flavour"
                placeholder="z. B. Schokolade, Erdbeere"
              />
            </div>
          </div>

          <div className="form-group">
            <label htmlFor="decoration">Dekoration und Design</label>
            <textarea
              id="decoration"
              name="decoration"
              rows={4}
              placeholder="Beschreibe deine Vorstellungen, Farben oder ein gewünschtes Motiv."
            />
          </div>

          <div className="form-group">
            <label htmlFor="message">Weitere Wünsche oder Hinweise</label>
            <textarea
              id="message"
              name="message"
              rows={4}
              placeholder="Gibt es noch etwas, das wir wissen sollten?"
            />
          </div>

          <p className="form-note">
            * Pflichtfelder. Deine Anfrage ist zunächst unverbindlich.
          </p>

          {!submitted ? (
            <button type="submit" className="btn btn--accent tortenanfrage-submit">
              Anfrage vorbereiten
              <ArrowIcon />
            </button>
          ) : (
            <p
              ref={successRef}
              className="form-success"
              role="status"
              tabIndex={-1}
            >
              Vielen Dank für deine Anfrage! Deine Angaben wurden vorläufig im
              Browser verarbeitet. Es wurde noch keine Anfrage an die
              Konditorei gesendet.
            </p>
          )}
        </form>
      </section>
    </div>
  )
}

export default Tortenservice
