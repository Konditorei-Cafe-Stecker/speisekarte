import { useState } from 'react'
import { Link, NavLink, Outlet } from 'react-router-dom'
import steckerLogo from '../assets/stecker-logo.svg'
import { useStaffAuth } from './StaffAuth'

/** Header with the staff navigation and sign-out, then the page. */
function StaffLayout() {
  const auth = useStaffAuth()
  const [signingOut, setSigningOut] = useState(false)

  if (auth.status !== 'signedIn') return null

  const { me } = auth
  const temporary = me.mustChangePassword

  async function signOut() {
    setSigningOut(true)
    await auth.signOut()
  }

  return (
    <>
      <a className="skip-link" href="#inhalt">
        Zum Inhalt springen
      </a>

      <header className="staff-header">
        <div className="staff-header-inner">
          <Link to="/personal" className="staff-brand">
            <img src={steckerLogo} alt="" width="92" height="40" />
            <span>Personal</span>
          </Link>

          {!temporary && (
            <nav className="staff-nav" aria-label="Personalbereich">
              <NavLink to="/personal" end>
                Übersicht
              </NavLink>
              {me.role === 'ADMIN' && <NavLink to="/personal/planung">Planung</NavLink>}
              {me.role === 'ADMIN' && <NavLink to="/personal/team">Team</NavLink>}
              <NavLink to="/personal/passwort">Passwort</NavLink>
            </nav>
          )}

          <div className="staff-user">
            <span className="staff-user-name">{me.displayName}</span>
            <button
              type="button"
              className="staff-button staff-button--quiet"
              onClick={signOut}
              disabled={signingOut}
            >
              Abmelden
            </button>
          </div>
        </div>
      </header>

      <main id="inhalt" className="staff-main" tabIndex={-1}>
        <Outlet />
      </main>
    </>
  )
}

export default StaffLayout
