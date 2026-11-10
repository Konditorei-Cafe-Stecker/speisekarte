import type { CSSProperties } from 'react'
import { Link } from 'react-router-dom'
import './About.css'
import ArrowIcon from '../components/ArrowIcon/ArrowIcon'
import DeferredPicture from '../components/DeferredPicture/DeferredPicture'
import ExternalEmbed from '../components/ExternalEmbed/ExternalEmbed'
import NumberedList from '../components/NumberedList/NumberedList'
import interior480 from '../assets/photos/geschichte-480.avif'
import interior694 from '../assets/photos/geschichte-694.avif'
import interiorFallback from '../assets/photos/geschichte-480.jpg'
import cafe640 from '../assets/photos/cafe-640.avif'
import cafe1268 from '../assets/photos/cafe-1268.avif'
import cafeFallback from '../assets/photos/cafe-640.jpg'
import { site } from '../content/site'
import { useRise } from '../utils/motion'
import { useSmoothScroll } from '../utils/smoothScroll'

const historyMilestones = [
  {
    year: '1908',
    text: 'Wilhelm Stecker gründet mit seiner Ehefrau Karoline eine Konditorei mit Café in der Hansastraße 60/62. Besonders der Butterkuchen wird in Bremen schnell bekannt.',
  },
  {
    year: '1944–48',
    text: 'Der Betrieb wird beim Bombenangriff auf den Bremer Westen zerstört. Nach der Rückkehr aus der Kriegsgefangenschaft eröffnen Hans Wilhelm Stecker und seine Frau Ruth die Konditorei neu.',
  },
  {
    year: '1953',
    text: 'Die Familie kauft das Rokoko-Haus an der Knochenhauerstraße 14 und baut es im alten Stil aus – bis heute unser Hauptgeschäft.',
  },
  {
    year: '1970',
    text: 'Das Raths-Café am Markt wird Teil von Stecker und nach einem Umbau zu einem Boulevard-Café im französischen Stil.',
  },
  {
    year: '1985–97',
    text: 'Ein drittes Café am Wall ergänzt das Haus Stecker für mehr als ein Jahrzehnt.',
  },
  {
    year: '2000',
    text: 'Bernard Timphus übernimmt die Geschäfte. Die Raths-Konditorei und die Backstube am Hauptgeschäft werden weiterentwickelt und modernisiert.',
  },
  {
    year: '2006–08',
    text: 'Stecker wird Mitglied der Partnerschaft Umwelt Unternehmen und als Klimaschutzbetrieb ausgezeichnet.',
  },
]

const values = [
  {
    title: 'Jeden Tag frisch',
    text: 'Vom ersten Teig bis zur letzten Verzierung entsteht alles mit Sorgfalt vor Ort.',
  },
  {
    title: 'Mit Gefühl für Geschmack',
    text: 'Vertraute Klassiker treffen auf feine, saisonale Ideen aus unserer Backstube.',
  },
  {
    title: 'Für deine Momente',
    text: 'Ob Kaffeepause oder Festtagstorte: Wir machen Genuss persönlich.',
  },
]

/** Order of the short load-in on the first screen. */
function step(index: number) {
  return { '--load-step': index } as CSSProperties
}

function About() {
  useSmoothScroll()
  const pageRef = useRise<HTMLDivElement>()

  return (
    <div ref={pageRef} className="about">
      <header className="container about-intro">
        <p className="eyebrow load-in">Seit 1908 · Bremen</p>
        <h1 className="about-title load-in" style={step(1)}>
          Traditionelles
          <br />
          <em>Konditorhandwerk.</em>
        </h1>
        <p className="about-lead load-in" style={step(2)}>
          Seit vielen Jahren steht die Konditorei Stecker für handgemachte
          Kuchen, Torten und Gebäck.
        </p>
      </header>

      <section
        className="container about-split about-story"
        aria-labelledby="about-story-title"
      >
        <div
          className="about-photo about-photo--arch load-in-move"
          style={step(3)}
        >
          {/* Close to the first screen, so it loads right away. */}
          <picture>
            <source
              type="image/avif"
              srcSet={`${interior480} 480w, ${interior694} 694w`}
              sizes="(min-width: 800px) 42vw, (min-width: 480px) 440px, 92vw"
            />
            <img
              src={interiorFallback}
              alt="Einblick in das Café der Konditorei Stecker"
              width={694}
              height={763}
            />
          </picture>
        </div>

        <div className="about-copy load-in" style={step(3)}>
          <p className="eyebrow">Aus Leidenschaft</p>
          <h2 id="about-story-title" className="section-title">
            Das Gute braucht <em>Zeit.</em>
          </h2>
          <p>
            Mit hochwertigen Zutaten, traditionellen Rezepten und viel Liebe
            zum Detail entstehen unsere Spezialitäten jeden Tag aufs Neue.
          </p>
          <p>
            Was bei uns auf den Tisch kommt, wird mit ruhiger Hand und einem
            Blick für das Besondere gemacht – zum Mitnehmen oder für eine
            kleine Auszeit bei uns im Café.
          </p>
          <Link className="text-link" to="/menu">
            Unsere Auswahl entdecken
            <ArrowIcon />
          </Link>
        </div>
      </section>

      <section className="about-history" aria-labelledby="about-history-title">
        <div className="container about-history-inner">
          <div className="about-history-head" data-rise>
            <div>
              <p className="eyebrow">Unsere Geschichte</p>
              <h2 id="about-history-title" className="section-title">
                Mehr als ein Jahrhundert <em>Stecker.</em>
              </h2>
            </div>
            <p>
              Seit 1908 prägt die Konditorei Stecker Bremen – mit Mut,
              handwerklicher Sorgfalt und dem festen Willen, Tradition in die
              Zukunft zu tragen.
            </p>
          </div>

          <ol className="about-timeline" role="list">
            {historyMilestones.map((milestone) => (
              <li key={milestone.year} data-rise>
                <p className="about-timeline-year">{milestone.year}</p>
                <p>{milestone.text}</p>
              </li>
            ))}
          </ol>
        </div>
      </section>

      <NumberedList label="Wofür wir stehen" items={values} />

      <section
        className="container about-split about-closing"
        aria-labelledby="about-closing-title"
      >
        <div className="about-copy" data-rise>
          <p className="eyebrow">Komm vorbei</p>
          <h2 id="about-closing-title" className="section-title">
            Ein Platz für kleine <em>Genussmomente.</em>
          </h2>
          <p>
            Wir freuen uns darauf, dich mit Kaffee, Kuchen und ehrlichem
            Handwerk willkommen zu heißen.
          </p>
        </div>

        <div className="about-photo about-photo--corner" data-rise data-rise-delay={1}>
          <DeferredPicture
            avif={`${cafe640} 640w, ${cafe1268} 1268w`}
            sizes="(min-width: 800px) 50vw, 92vw"
            fallback={cafeFallback}
            alt="Kaffee und Gebäck im Café"
            width={1268}
            height={940}
          />
        </div>
      </section>

      <section
        className="container about-split about-film"
        aria-labelledby="about-film-title"
      >
        <div className="about-copy" data-rise>
          <p className="eyebrow">Einblick</p>
          <h2 id="about-film-title" className="section-title">
            Stecker in <em>Bewegung.</em>
          </h2>
          <p>
            Ein kleiner Blick hinter die Kulissen unseres Hauses, unserer
            Backstube und der Menschen, die Stecker jeden Tag lebendig machen.
          </p>
        </div>

        <div className="about-video" data-rise data-rise-delay={1}>
          <ExternalEmbed
            src="https://www.youtube-nocookie.com/embed/Eb9kB0WqO_k?rel=0&autoplay=1"
            title="Konditorei Café Stecker im Video"
            note="Das Video wird erst nach deinem Klick von YouTube geladen. Dabei werden Daten an YouTube übertragen."
            buttonLabel="Video laden"
            allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
            allowFullScreen
          />
        </div>
      </section>

      <section className="about-visit" aria-labelledby="about-visit-title">
        <div className="container about-split about-visit-inner">
          <div className="about-copy" data-rise>
            <p className="eyebrow">Besuch uns</p>
            <h2 id="about-visit-title" className="section-title">
              Mitten in <em>Bremen.</em>
            </h2>
            <address>
              {site.street}
              <br />
              {site.postcode} {site.city}
            </address>
            <p>
              Komm für eine kleine Pause vorbei, entdecke unsere Theke oder
              hole deine Bestellung direkt bei uns ab.
            </p>
            <a
              className="text-link"
              href={site.routeUrl}
              target="_blank"
              rel="noopener noreferrer"
            >
              Route planen
              <span className="visually-hidden"> (öffnet Google Maps)</span>
              <ArrowIcon />
            </a>
          </div>

          <div className="about-map" data-rise data-rise-delay={1}>
            <ExternalEmbed
              src={site.mapEmbedUrl}
              title="Karte zur Konditorei Café Stecker in Bremen"
              note="Die Karte wird erst nach deinem Klick von Google Maps geladen. Dabei werden Daten an Google übertragen."
              buttonLabel="Karte laden"
              referrerPolicy="no-referrer-when-downgrade"
            />
          </div>
        </div>
      </section>
    </div>
  )
}

export default About
