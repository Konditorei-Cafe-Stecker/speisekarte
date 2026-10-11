
import { Link } from 'react-router-dom'
import './Featured.css'

import tortenImage from '../../assets/torten.jpg'
import kuchenImage from '../../assets/kuchen.jpg'
import gebaeckImage from '../../assets/gebaeck.jpg'

function Featured() {
  return (
    <section className="featured">
      <div className="featured-header">
        <p className="featured-eyebrow">AUS UNSERER KONDITOREI</p>

        <h2>Unsere Spezialitäten</h2>

        <p className="featured-description">
          Handgemachte Kuchen, Torten und Gebäck aus traditioneller
          Konditoreikunst.
        </p>
      </div>

      <div className="featured-grid">
        <Link
          to="/menu#torten"
          className="featured-card"
          aria-label="Unsere Torten entdecken"
        >
          <div className="featured-image">
            <img src={tortenImage} alt="" />
          </div>
          <h3>Torten</h3>
          <p>Individuelle Torten für besondere Momente.</p>
          <span className="featured-card-link">Entdecken ↗</span>
        </Link>

        <Link
          to="/menu#kuchen"
          className="featured-card"
          aria-label="Unsere Kuchen entdecken"
        >
          <div className="featured-image">
            <img src={kuchenImage} alt="" />
          </div>
          <h3>Kuchen</h3>
          <p>Frische Kuchen nach traditionellen Rezepten.</p>
          <span className="featured-card-link">Entdecken ↗</span>
        </Link>

        <Link
          to="/menu#gebaeck"
          className="featured-card"
          aria-label="Unser Gebäck entdecken"
        >
          <div className="featured-image">
            <img src={gebaeckImage} alt="" />
          </div>
          <h3>Gebäck</h3>
          <p>Feines Gebäck für den kleinen Genuss.</p>
          <span className="featured-card-link">Entdecken ↗</span>
        </Link>
      </div>
    </section>
  )
}

export default Featured
