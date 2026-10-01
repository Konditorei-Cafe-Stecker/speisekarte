import { useState } from 'react'
import { NavLink } from 'react-router-dom'
import './Navbar.css'

function Navbar() {
  const [menuOpen, setMenuOpen] = useState(false)

  const closeMenu = () => {
    setMenuOpen(false)
  }

  return (
    <nav className="navbar">
      <div className="navbar-logo">
        Stecker Konditorei
      </div>

      <button
        className="navbar-toggle"
        onClick={() => setMenuOpen(!menuOpen)}
        aria-label="Menü öffnen"
      >
        {menuOpen ? '✕' : '☰'}
      </button>

      <div className={`navbar-links ${menuOpen ? 'open' : ''}`}>
        <NavLink to="/" onClick={closeMenu}>
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