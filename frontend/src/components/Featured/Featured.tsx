import { Link } from 'react-router-dom'
import './Featured.css'

import kuchenImage from '../../assets/kuchen-photo.jpg'
import cafeImage from '../../assets/cafe-coffee.jpg'
import gebaeckImage from '../../assets/gebaeck-photo.jpg'

function Featured() {
  return (
    <section className="featured" id="spezialitaeten">
      <div className="featured-header">
        <p className="featured-eyebrow">
          AUS UNSERER BACKSTUBE & UNSEREM CAFÉ
        </p>

        <h2>Unsere Spezialitäten</h2>

        <p className="featured-description">
          Mit Sorgfalt gemacht und für besondere Momente gedacht.
        </p>
      </div>

      <div className="featured-grid">
        {/* Kuchen card */}
        <Link
          to="/menu#kuchen"
          className="featured-card"
          aria-label="Unsere Kuchen entdecken"
        >
          <div className="featured-image">
            <img
              src={kuchenImage}
              alt="Frisch zubereiteter Kuchen"
            />
          </div>

          <h3>Kuchen</h3>
          <p>Klassische Lieblingsstücke zum Genießen.</p>
          <span className="featured-card-link">
            Mehr entdecken →
          </span>
        </Link>

        {/* Café card */}
        <Link
          to="/about"
          className="featured-card"
          aria-label="Mehr über unser Café erfahren"
        >
          <div className="featured-image">
            <img
              src={cafeImage}
              alt="Kaffee in unserem Café"
            />
          </div>

          <h3>Café</h3>
          <p>Aromatische Kaffeespezialitäten zum Verweilen.</p>
          <span className="featured-card-link">
            Mehr entdecken →
          </span>
        </Link>

        {/* Gebäck card */}
        <Link
          to="/menu#gebaeck"
          className="featured-card"
          aria-label="Unser Gebäck entdecken"
        >
          <div className="featured-image">
            <img
              src={gebaeckImage}
              alt="Frisches Gebäck aus unserer Backstube"
            />
          </div>

          <h3>Gebäck</h3>
          <p>Kleine Köstlichkeiten aus unserer Backstube.</p>
          <span className="featured-card-link">
            Mehr entdecken →
          </span>
        </Link>
      </div>
    </section>
  )
}

export default Featured