import { Link } from 'react-router-dom'
import './Footer.css'
import steckerLogo from '../../assets/stecker-logo.svg'

function Footer() {
  return (
    <footer className="site-footer">
      <div className="container site-footer-inner">
        <img
          className="site-footer-logo"
          src={steckerLogo}
          alt="Stecker Konditorei seit 1908"
          width={268}
          height={88}
          loading="lazy"
        />

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
