import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError, MIN_PASSWORD_LENGTH, staffApi } from '../api'
import { useStaffAuth } from '../StaffAuth'
import { usePageTitle } from '../usePageTitle'

function StaffPassword() {
  usePageTitle('Passwort')

  const auth = useStaffAuth()
  const navigate = useNavigate()
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [repeat, setRepeat] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState(false)
  const [sending, setSending] = useState(false)
  const messageRef = useRef<HTMLParagraphElement>(null)

  useEffect(() => {
    if (error || done) messageRef.current?.focus()
  }, [error, done])

  if (auth.status !== 'signedIn') return null
  const temporary = auth.me.mustChangePassword

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setDone(false)

    if (next.length < MIN_PASSWORD_LENGTH) {
      setError(`Das neue Passwort muss mindestens ${MIN_PASSWORD_LENGTH} Zeichen lang sein.`)
      return
    }
    if (next !== repeat) {
      setError('Die beiden neuen Passwörter stimmen nicht überein.')
      return
    }

    setSending(true)
    try {
      const me = await staffApi.changePassword(current, next)
      setCurrent('')
      setNext('')
      setRepeat('')
      auth.update(me)

      if (temporary) {
        navigate('/personal', { replace: true })
      } else {
        setDone(true)
      }
    } catch (requestError) {
      setError(requestError instanceof ApiError ? requestError.message : 'Das hat nicht geklappt.')
    } finally {
      setSending(false)
    }
  }

  return (
    <div className="staff-page staff-page--narrow">
      <p className="eyebrow">Anmeldung</p>
      <h1>{temporary ? 'Wähle dein eigenes Passwort' : 'Passwort ändern'}</h1>

      {temporary && (
        <p className="staff-intro">
          Dein jetziges Passwort hat die Leitung für dich festgelegt. Bevor es weitergeht,
          wähle bitte ein eigenes, das nur du kennst.
        </p>
      )}

      <form className="staff-form" onSubmit={handleSubmit}>
        <div className="staff-field">
          <label htmlFor="password-current">
            {temporary ? 'Vorläufiges Passwort' : 'Bisheriges Passwort'}
          </label>
          <input
            id="password-current"
            type="password"
            autoComplete="current-password"
            required
            value={current}
            onChange={(event) => setCurrent(event.target.value)}
          />
        </div>

        <div className="staff-field">
          <label htmlFor="password-new">Neues Passwort</label>
          <input
            id="password-new"
            type="password"
            autoComplete="new-password"
            required
            minLength={MIN_PASSWORD_LENGTH}
            aria-describedby="password-new-hint"
            value={next}
            onChange={(event) => setNext(event.target.value)}
          />
          <p id="password-new-hint" className="staff-hint">
            Mindestens {MIN_PASSWORD_LENGTH} Zeichen. Ein kurzer Satz lässt sich gut merken.
          </p>
        </div>

        <div className="staff-field">
          <label htmlFor="password-repeat">Neues Passwort wiederholen</label>
          <input
            id="password-repeat"
            type="password"
            autoComplete="new-password"
            required
            value={repeat}
            onChange={(event) => setRepeat(event.target.value)}
          />
        </div>

        {error && (
          <p ref={messageRef} className="staff-error" role="alert" tabIndex={-1}>
            {error}
          </p>
        )}
        {done && (
          <p ref={messageRef} className="staff-success" role="status" tabIndex={-1}>
            Dein Passwort ist geändert. Auf anderen Geräten musst du dich neu anmelden.
          </p>
        )}

        <button type="submit" className="btn btn--dark staff-submit" disabled={sending}>
          {sending ? 'Wird gespeichert …' : 'Passwort speichern'}
        </button>
      </form>
    </div>
  )
}

export default StaffPassword
