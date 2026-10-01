import './Menu.css'

function Menu() {
  return (
    <main>
      <section className="menu">
        <div className="menu-header">
          <p className="menu-eyebrow">UNSERE AUSWAHL</p>

          <h1>Speisekarte</h1>

          <p className="menu-description">
            Entdecke unsere handgemachten Kuchen, Torten und
            feinen Gebäckspezialitäten.
          </p>
        </div>

        <div className="menu-category">
          <h2>Torten</h2>
          <p>
            Individuelle Torten für Geburtstage, Hochzeiten und
            besondere Momente.
          </p>
        </div>

        <div className="menu-category">
          <h2>Kuchen</h2>
          <p>
            Frische Kuchen nach traditionellen Rezepten und mit
            ausgewählten Zutaten.
          </p>
        </div>

        <div className="menu-category">
          <h2>Gebäck</h2>
          <p>
            Feines Gebäck für den kleinen Genuss zwischendurch.
          </p>
        </div>
      </section>
    </main>
  )
}

export default Menu