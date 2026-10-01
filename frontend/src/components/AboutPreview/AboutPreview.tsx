import './AboutPreview.css'

function AboutPreview() {
  return (
    <section className="about-preview">
      <div className="about-preview-content">
        <p className="about-preview-eyebrow">TRADITION & HANDWERK</p>

        <h2>
          Handgemachte Spezialitäten
          <br />
          mit Leidenschaft.
        </h2>

        <p className="about-preview-description">
          Bei Konditorei Stecker verbinden wir traditionelles
          Konditorhandwerk mit hochwertigen Zutaten und viel Liebe zum Detail.
        </p>

        <a href="/about" className="about-preview-link">
          Mehr über uns →
        </a>
      </div>
    </section>
  )
}

export default AboutPreview