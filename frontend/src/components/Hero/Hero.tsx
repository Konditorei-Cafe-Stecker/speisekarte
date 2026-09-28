import './Hero.css'

function Hero() {
  return (
    <section className="hero">
      <div className="hero-content">
        <p className="hero-eyebrow">SEIT GENERATIONEN IN BREMEN</p>

        <h1>
          Tradition trifft
          <br />
          Konditorhandwerk
        </h1>

        <p className="hero-description">
          Frische Kuchen, Torten und Gebäck
          mit Liebe und Handwerk hergestellt.
        </p>

        <div className="hero-buttons">
          <a href="/menu" className="hero-button primary">
            Speisekarte entdecken
          </a>

          <a href="/tortenservice" className="hero-button secondary">
            Torte anfragen
          </a>
        </div>
      </div>
    </section>
  )
}

export default Hero