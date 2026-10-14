
import { Link } from 'react-router-dom'
import './AboutPreview.css'
import storyImage from '../../assets/story-interior.jpg'

function AboutPreview() {
  return (
    <section className="about-preview">
      <div className="about-preview-image">
        <img
          src={storyImage}
          alt="Einblick in das gemütliche Café der Konditorei"
        />
      </div>

      <div className="about-preview-content">
        <p className="about-preview-eyebrow">
          UNSERE GESCHICHTE
        </p>

        <h2>Tradition, die verbindet.</h2>

        <p className="about-preview-description">
          Entdecke die Leidenschaft hinter unserer Konditorei – von
          frisch gebackenen Kuchen und feinem Gebäck bis zu gutem Kaffee.
        </p>

        <Link to="/about" className="about-preview-link">
          Mehr über uns →
        </Link>
      </div>
    </section>
  )
}

export default AboutPreview
