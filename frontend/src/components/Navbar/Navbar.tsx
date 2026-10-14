
import { useState } from 'react'
import { NavLink } from 'react-router-dom'
import './Navbar.css'
import steckerLogo from '../../assets/stecker-logo.svg'

function Navbar() {
  const [menuOpen, setMenuOpen] = useState(false)

  const closeMenu = () => {
    setMenuOpen(false)
  }

  return (
    <header className="site-header">
      <nav className="navbar" aria-label="Hauptnavigation">
        <NavLink
          to="/"
          className="navbar-logo"
          onClick={closeMenu}
          aria-label="Stecker Konditorei – Startseite"
        >
          <img src={steckerLogo} alt="Stecker Konditorei" />
        </NavLink>

        <button
          type="button"
          className="navbar-toggle"
          onClick={() => setMenuOpen((open) => !open)}
          aria-label={menuOpen ? 'Menü schließen' : 'Menü öffnen'}
          aria-expanded={menuOpen}
          aria-controls="navbar-links"
        >
          <span aria-hidden="true">{menuOpen ? '✕' : '☰'}</span>
        </button>

        <div
          id="navbar-links"
          className={`navbar-links ${menuOpen ? 'open' : ''}`}
        >
          <NavLink to="/" end onClick={closeMenu}>
            Home
          </NavLink>

          <NavLink to="/menu" onClick={closeMenu}>
            Speisekarte
          </NavLink>

          <NavLink to="/tortenservice" onClick={closeMenu}>
            Tortenservice
          </NavLink>

          <NavLink to="/about" onClick={closeMenu}>
            Über uns
          </NavLink>
        </div>
      </nav>
    </header>
  )
}

export default Navbar
