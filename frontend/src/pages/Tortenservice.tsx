
import { useState, type FormEvent } from 'react'
import './Tortenservice.css'

function Tortenservice() {
  const [submitted, setSubmitted] = useState(false)

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitted(true)
  }

  return (
    <main>
      <section className="tortenservice">
        <div className="tortenservice-header">
          <p className="tortenservice-eyebrow">TORTENSERVICE</p>

          <h1>
            Deine Torte.
            <br />
            Dein besonderer Moment.
          </h1>

          <p className="tortenservice-description">
            Wir fertigen individuelle Torten für Geburtstage,
            Hochzeiten und andere besondere Anlässe.
          </p>
        </div>

        <div className="tortenservice-content">
          <h2>Individuelle Torten nach deinen Wünschen</h2>

          <p>
            Gemeinsam gestalten wir eine Torte, die zu deinem Anlass
            passt – von der Größe über die Geschmacksrichtung bis zur
            Dekoration.
          </p>

          <button
            type="button"
            onClick={() =>
              document
                .getElementById('tortenanfrage')
                ?.scrollIntoView({ behavior: 'smooth' })
            }
          >
            Torte anfragen →
          </button>
        </div>

        <section id="tortenanfrage" className="tortenanfrage">
          <div className="tortenanfrage-header">
            <p className="tortenservice-eyebrow">DEINE ANFRAGE</p>

            <h2>Erzähl uns von deiner Wunschtorte.</h2>

            <p>
              Teile uns deine Wünsche mit. Wir besprechen anschließend
              die Details mit dir.
            </p>
          </div>

          <form
            className="tortenanfrage-form"
            onSubmit={handleSubmit}
          >
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

                <select
                  id="occasion"
                  name="occasion"
                  required
                  defaultValue=""
                >
                  <option value="" disabled>
                    Bitte auswählen
                  </option>
                  <option value="geburtstag">Geburtstag</option>
                  <option value="hochzeit">Hochzeit</option>
                  <option value="taufe">Taufe</option>
                  <option value="kommunion">
                    Kommunion / Konfirmation
                  </option>
                  <option value="jubilaeum">Jubiläum</option>
                  <option value="sonstiges">Sonstiger Anlass</option>
                </select>
              </div>

              <div className="form-group">
                <label htmlFor="pickupDate">
                  Wunsch-Abholdatum *
                </label>

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
              <label htmlFor="people">
                Für wie viele Personen? *
              </label>

              <input
                type="number"
                id="people"
                name="people"
                min="1"
                max="500"
                placeholder="z. B. 12"
                required
              />
            </div>

            <div className="form-row">
              <div className="form-group">
                <label htmlFor="cakeType">Tortenart</label>

                <select
                  id="cakeType"
                  name="cakeType"
                  defaultValue=""
                >
                  <option value="">Noch offen</option>
                  <option value="sahnetorte">Sahnetorte</option>
                  <option value="cremetorte">Cremetorte</option>
                  <option value="motivorte">Motivtorte</option>
                  <option value="hochzeitstorte">
                    Hochzeitstorte
                  </option>
                  <option value="sonstige">Sonstige</option>
                </select>
              </div>

              <div className="form-group">
                <label htmlFor="flavour">
                  Geschmacksrichtung
                </label>

                <input
                  type="text"
                  id="flavour"
                  name="flavour"
                  placeholder="z. B. Schokolade, Erdbeere"
                />
              </div>
            </div>

            <div className="form-group">
              <label htmlFor="decoration">
                Dekoration und Design
              </label>

              <textarea
                id="decoration"
                name="decoration"
                rows={4}
                placeholder="Beschreibe deine Vorstellungen, Farben oder ein gewünschtes Motiv."
              />
            </div>

            <div className="form-group">
              <label htmlFor="message">
                Weitere Wünsche oder Hinweise
              </label>

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
              <button
                type="submit"
                className="tortenanfrage-submit"
              >
                Anfrage vorbereiten →
              </button>
            ) : (
              <p className="form-success" role="status">
                Vielen Dank für deine Anfrage! Deine Angaben wurden
                vorläufig im Browser verarbeitet. Es wurde noch keine
                Anfrage an die Konditorei gesendet.
              </p>
            )}
          </form>
        </section>
      </section>
    </main>
  )
}

export default Tortenservice
