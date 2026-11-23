import { Link } from 'react-router-dom'
import './Footer.css'
import logo700 from '../../assets/brand/footer-logo-700.avif'
import logo1400 from '../../assets/brand/footer-logo-1400.avif'
import logoFallback from '../../assets/brand/footer-logo-700.webp'
import background960 from '../../assets/brand/footer-bg-960.avif'
import background1920 from '../../assets/brand/footer-bg-1920.avif'
import backgroundFallback from '../../assets/brand/footer-bg-960.jpg'

function Footer() {
  return (
    <footer className="site-footer">
      {/* Café interior, blurred and darkened in the file itself, so the
          text on top keeps its contrast. Decoration only. */}
      <picture>
        <source
          type="image/avif"
          srcSet={`${background960} 960w, ${background1920} 1920w`}
          sizes="100vw"
        />
        <img
          className="site-footer-bg"
          src={backgroundFallback}
          alt=""
          width={1920}
          height={711}
          loading="lazy"
          decoding="async"
        />
      </picture>

      <div className="container site-footer-inner">
        {/* The logo with the café's house, in cream for the dark footer. */}
        <picture>
          <source
            type="image/avif"
            srcSet={`${logo700} 700w, ${logo1400} 1400w`}
            sizes="(min-width: 780px) 700px, calc(100vw - 40px)"
          />
          <img
            className="site-footer-logo"
            src={logoFallback}
            alt="Konditorei-Café Stecker seit 1908"
            width={700}
            height={406}
            loading="lazy"
            decoding="async"
          />
        </picture>

        <div className="site-footer-row">
          <span>Konditorei Café Stecker · seit 1908</span>

          <nav className="site-footer-links" aria-label="Fußzeile">
            <Link to="/menu">Speisekarte</Link>
            <Link to="/impressum">Impressum</Link>
            <Link to="/datenschutz">Datenschutz</Link>
          </nav>
        </div>
      </div>
    </footer>
  )
}

export default Footer
