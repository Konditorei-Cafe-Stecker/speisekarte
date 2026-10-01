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
    <article className="featured-card">
      <div className="featured-image">
        <img src={tortenImage} alt="Torten" />
      </div>
      <h3>Torten</h3>
      <p>Individuelle Torten für besondere Momente.</p>
    </article>

    <article className="featured-card">
      <div className="featured-image">
        <img src={kuchenImage} alt="Kuchen" />
      </div>
      <h3>Kuchen</h3>
      <p>Frische Kuchen nach traditionellen Rezepten.</p>
    </article>

    <article className="featured-card">
      <div className="featured-image">
        <img src={gebaeckImage} alt="Gebäck" />
      </div>
      <h3>Gebäck</h3>
      <p>Feines Gebäck für den kleinen Genuss.</p>
    </article>
  </div>
</section>
  )
}

export default Featured