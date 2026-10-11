
import { useState } from 'react'
import { NavLink } from 'react-router-dom'
import './Navbar.css'

function Navbar() {
  const [menuOpen, setMenuOpen] = useState(false)

  const closeMenu = () => {
    setMenuOpen(false)
  }

  return (
    <nav className="navbar" aria-label="Hauptnavigation">
      <NavLink to="/" className="navbar-logo" onClick={closeMenu}>
        Stecker <span>Konditorei</span>
      </NavLink>

      <button
        type="button"
        className="navbar-toggle"
        onClick={() => setMenuOpen(!menuOpen)}
        aria-label={menuOpen ? 'Menü schließen' : 'Menü öffnen'}
        aria-expanded={menuOpen}
        aria-controls="navbar-links"
      >
        {menuOpen ? '✕' : '☰'}
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
  )
}

export default Navbar
