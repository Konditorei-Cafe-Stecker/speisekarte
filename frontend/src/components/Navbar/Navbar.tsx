import { useEffect, useRef, useState } from 'react'
import { Link, NavLink, useLocation } from 'react-router-dom'
import './Navbar.css'
import steckerLogo from '../../assets/stecker-logo.svg'

function Navbar() {
  const [menuOpen, setMenuOpen] = useState(false)
  const { pathname } = useLocation()
  const headerRef = useRef<HTMLElement>(null)
  const toggleRef = useRef<HTMLButtonElement>(null)
  const linksRef = useRef<HTMLDivElement>(null)

  const closeMenu = () => setMenuOpen(false)

  // Mobile menu: Escape closes it and returns focus to the toggle,
  // a click or tap outside the header closes it as well.
  useEffect(() => {
    if (!menuOpen) return

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key !== 'Escape') return

      setMenuOpen(false)
      toggleRef.current?.focus()
    }

    function handlePointerDown(event: PointerEvent) {
      if (headerRef.current?.contains(event.target as Node)) return

      setMenuOpen(false)
    }

    document.addEventListener('keydown', handleKeyDown)
    document.addEventListener('pointerdown', handlePointerDown)

    return () => {
      document.removeEventListener('keydown', handleKeyDown)
      document.removeEventListener('pointerdown', handlePointerDown)
    }
  }, [menuOpen])

  // The links come before the toggle in the page, so move focus into the
  // opened menu instead of leaving it behind on the button.
  useEffect(() => {
    if (!menuOpen) return

    linksRef.current?.querySelector('a')?.focus()
  }, [menuOpen])

  // The Speisekarte has its own sticky search bar, so the header scrolls
  // away there instead of stacking two sticky bars on a phone.
  const isStatic = pathname === '/menu'

  return (
    <header
      ref={headerRef}
      className={`site-header ${isStatic ? 'is-static' : ''}`}
    >
      <nav className="container navbar" aria-label="Hauptnavigation">
        <Link
          to="/"
          className="navbar-logo"
          onClick={closeMenu}
          aria-label="Stecker Konditorei – Startseite"
        >
          <img
            src={steckerLogo}
            alt="Stecker Konditorei seit 1908"
            width={268}
            height={88}
          />
        </Link>

        <div
          ref={linksRef}
          id="navbar-links"
          className={`navbar-links ${menuOpen ? 'open' : ''}`}
        >
          <NavLink to="/menu" onClick={closeMenu}>
            Speisekarte
          </NavLink>

          <Link to="/#spezialitaeten" onClick={closeMenu}>
            Spezialitäten
          </Link>

          <NavLink to="/about" onClick={closeMenu}>
            Über uns
          </NavLink>

          <Link to="/#besuch" onClick={closeMenu}>
            Besuch
          </Link>
        </div>

        <Link to="/menu" className="navbar-order-link" onClick={closeMenu}>
          Bestellen
        </Link>

        <button
          ref={toggleRef}
          type="button"
          className="navbar-toggle"
          onClick={() => setMenuOpen((open) => !open)}
          aria-label={menuOpen ? 'Menü schließen' : 'Menü öffnen'}
          aria-expanded={menuOpen}
          aria-controls="navbar-links"
        >
          <svg
            aria-hidden="true"
            width="22"
            height="22"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
          >
            {menuOpen ? (
              <path d="M6 6l12 12M18 6L6 18" />
            ) : (
              <path d="M4 7h16M4 12h16M4 17h16" />
            )}
          </svg>
        </button>
      </nav>

      {pathname === '/' && (
        <div className="navbar-progress" aria-hidden="true" />
      )}
    </header>
  )
}

export default Navbar
