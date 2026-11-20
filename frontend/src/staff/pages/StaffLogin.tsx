import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import steckerLogo from '../../assets/stecker-logo.svg'
import { ApiError } from '../api'
import { useStaffAuth } from '../StaffAuth'
import { usePageTitle } from '../usePageTitle'

type LocationState = { from?: string; expired?: boolean } | null

function StaffLogin() {
  usePageTitle('Anmelden')

  const auth = useStaffAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const state = location.state as LocationState
  const target = state?.from?.startsWith('/personal') ? state.from : '/personal'

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [sending, setSending] = useState(false)
  const errorRef = useRef<HTMLParagraphElement>(null)

  useEffect(() => {
    if (error) errorRef.current?.focus()
  }, [error])

  if (auth.status === 'signedIn') {
    return <Navigate to={target} replace />
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setSending(true)

    try {
      const me = await auth.signIn(username, password)
      navigate(me.mustChangePassword ? '/personal/passwort' : target, { replace: true })
    } catch (requestError) {
      setPassword('')
      setError(
        requestError instanceof ApiError
          ? requestError.message
          : 'Die Anmeldung hat nicht geklappt. Bitte prüf deine Verbindung.',
      )
    } finally {
      setSending(false)
    }
  }

  return (
    <main id="inhalt" className="staff-login" tabIndex={-1}>
      <div className="staff-login-card">
        <img src={steckerLogo} alt="Café Stecker" width="138" height="60" />
        <h1>Personal-Anmeldung</h1>

        {state?.expired && !error && (
          <p className="staff-note" role="status">
            Deine Sitzung ist abgelaufen. Bitte melde dich neu an.
          </p>
        )}

        <form className="staff-form" onSubmit={handleSubmit}>
          <div className="staff-field">
            <label htmlFor="staff-username">Benutzername</label>
            <input
              id="staff-username"
              name="username"
              autoComplete="username"
              autoCapitalize="none"
              spellCheck={false}
              required
              value={username}
              onChange={(event) => setUsername(event.target.value)}
            />
          </div>

          <div className="staff-field">
            <label htmlFor="staff-password">Passwort</label>
            <input
              id="staff-password"
              name="password"
              type="password"
              autoComplete="current-password"
              required
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
          </div>

          {error && (
            <p ref={errorRef} className="staff-error" role="alert" tabIndex={-1}>
              {error}
            </p>
          )}

          <button type="submit" className="btn btn--dark staff-submit" disabled={sending}>
            {sending ? 'Wird angemeldet …' : 'Anmelden'}
          </button>
        </form>

        <p className="staff-login-help">
          Passwort vergessen? Die Leitung kann es zurücksetzen.
        </p>
        <Link to="/" className="staff-back">
          Zur Website
        </Link>
      </div>
    </main>
  )
}

export default StaffLogin
