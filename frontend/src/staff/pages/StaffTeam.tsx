import { useCallback, useEffect, useId, useRef, useState, type FormEvent } from 'react'
import {
  ApiError,
  MIN_PASSWORD_LENGTH,
  staffApi,
  suggestPassword,
  type StaffAccount,
  type StaffRole,
} from '../api'
import { useStaffAuth } from '../StaffAuth'
import { usePageTitle } from '../usePageTitle'

const ROLE_LABEL: Record<StaffRole, string> = {
  STAFF: 'Personal',
  ADMIN: 'Admin',
}

function messageOf(error: unknown) {
  return error instanceof ApiError ? error.message : 'Das hat nicht geklappt.'
}

function formatLastLogin(value: string | null) {
  if (!value) return 'Noch nie angemeldet'

  return `Zuletzt angemeldet ${new Intl.DateTimeFormat('de-DE', {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone: 'Europe/Berlin',
  }).format(new Date(value))}`
}

/** Admins: create accounts, switch roles, deactivate, reset passwords. */
function StaffTeam() {
  usePageTitle('Team')

  const auth = useStaffAuth()
  const [accounts, setAccounts] = useState<StaffAccount[] | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const load = useCallback(() => {
    staffApi
      .listStaff()
      .then(setAccounts)
      .catch((error: unknown) => setLoadError(messageOf(error)))
  }, [])

  useEffect(() => {
    load()
  }, [load])

  function retry() {
    setLoadError(null)
    load()
  }

  if (auth.status !== 'signedIn') return null
  const myUsername = auth.me.username

  function replace(updated: StaffAccount) {
    setAccounts((current) =>
      current ? current.map((account) => (account.id === updated.id ? updated : account)) : current,
    )
  }

  return (
    <div className="staff-page">
      <p className="eyebrow">Verwaltung</p>
      <h1>Team</h1>

      <p className="visually-hidden" role="status">
        {notice}
      </p>

      <CreateAccount
        onCreated={(account) => {
          setAccounts((current) => (current ? [account, ...current] : [account]))
          setNotice(`Konto für ${account.displayName} angelegt.`)
        }}
      />

      <section className="staff-section" aria-labelledby="team-list-title">
        <h2 id="team-list-title">Konten</h2>

        {loadError && (
          <div className="staff-error" role="alert">
            <p>{loadError}</p>
            <button type="button" className="staff-button" onClick={retry}>
              Noch einmal laden
            </button>
          </div>
        )}

        {!accounts && !loadError && <p role="status">Wird geladen …</p>}

        {accounts && (
          <ul className="team-list">
            {accounts.map((account) => (
              <AccountRow
                key={account.id}
                account={account}
                isSelf={account.username === myUsername}
                onChanged={(updated, message) => {
                  replace(updated)
                  setNotice(message)
                }}
              />
            ))}
          </ul>
        )}
      </section>
    </div>
  )
}

function CreateAccount({ onCreated }: { onCreated: (account: StaffAccount) => void }) {
  const [username, setUsername] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [role, setRole] = useState<StaffRole>('STAFF')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [created, setCreated] = useState<{ username: string; password: string } | null>(null)
  const [sending, setSending] = useState(false)
  const messageRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (error || created) messageRef.current?.focus()
  }, [error, created])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setCreated(null)

    if (password.length < MIN_PASSWORD_LENGTH) {
      setError(`Das vorläufige Passwort muss mindestens ${MIN_PASSWORD_LENGTH} Zeichen lang sein.`)
      return
    }

    setSending(true)
    try {
      const account = await staffApi.createStaff({
        username,
        displayName,
        role,
        temporaryPassword: password,
      })
      onCreated(account)
      setCreated({ username: account.username, password })
      setUsername('')
      setDisplayName('')
      setRole('STAFF')
      setPassword('')
    } catch (requestError) {
      setError(messageOf(requestError))
    } finally {
      setSending(false)
    }
  }

  return (
    <section className="staff-section staff-panel" aria-labelledby="team-create-title">
      <h2 id="team-create-title">Neues Konto</h2>
      <p className="staff-hint">
        Gib Benutzername und vorläufiges Passwort der Person persönlich weiter. Beim ersten
        Anmelden wählt sie ein eigenes Passwort.
      </p>

      <form className="staff-form staff-form--grid" onSubmit={handleSubmit}>
        <div className="staff-field">
          <label htmlFor="new-display-name">Name</label>
          <input
            id="new-display-name"
            required
            maxLength={100}
            autoComplete="off"
            value={displayName}
            onChange={(event) => setDisplayName(event.target.value)}
          />
        </div>

        <div className="staff-field">
          <label htmlFor="new-username">Benutzername</label>
          <input
            id="new-username"
            required
            autoComplete="off"
            autoCapitalize="none"
            spellCheck={false}
            aria-describedby="new-username-hint"
            value={username}
            onChange={(event) => setUsername(event.target.value)}
          />
          <p id="new-username-hint" className="staff-hint">
            Kleinbuchstaben, Ziffern, Punkt oder Bindestrich, z. B. anna.m
          </p>
        </div>

        <div className="staff-field">
          <label htmlFor="new-role">Rolle</label>
          <select
            id="new-role"
            value={role}
            onChange={(event) => setRole(event.target.value as StaffRole)}
          >
            <option value="STAFF">Personal (Bestellungen)</option>
            <option value="ADMIN">Admin (zusätzlich Team und Speisekarte)</option>
          </select>
        </div>

        <PasswordWithSuggestion
          id="new-password"
          label="Vorläufiges Passwort"
          value={password}
          onChange={setPassword}
        />

        <div ref={messageRef} tabIndex={-1} className="staff-form-message">
          {error && (
            <p className="staff-error" role="alert">
              {error}
            </p>
          )}
          {created && (
            <div className="staff-success" role="status">
              <p>Konto angelegt. Zugangsdaten zum Weitergeben:</p>
              <p className="staff-credentials">
                Benutzername <strong>{created.username}</strong>
                <br />
                Vorläufiges Passwort <strong>{created.password}</strong>
              </p>
            </div>
          )}
        </div>

        <button type="submit" className="btn btn--dark staff-submit" disabled={sending}>
          {sending ? 'Wird angelegt …' : 'Konto anlegen'}
        </button>
      </form>
    </section>
  )
}

function PasswordWithSuggestion({
  id,
  label,
  value,
  onChange,
}: {
  id: string
  label: string
  value: string
  onChange: (value: string) => void
}) {
  return (
    <div className="staff-field">
      <label htmlFor={id}>{label}</label>
      <div className="staff-input-row">
        <input
          id={id}
          type="text"
          required
          minLength={MIN_PASSWORD_LENGTH}
          autoComplete="off"
          spellCheck={false}
          className="staff-mono"
          value={value}
          onChange={(event) => onChange(event.target.value)}
        />
        <button type="button" className="staff-button" onClick={() => onChange(suggestPassword())}>
          Vorschlagen
        </button>
      </div>
    </div>
  )
}

function AccountRow({
  account,
  isSelf,
  onChanged,
}: {
  account: StaffAccount
  isSelf: boolean
  onChanged: (account: StaffAccount, message: string) => void
}) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [resetting, setResetting] = useState(false)
  const [password, setPassword] = useState('')
  const [resetDone, setResetDone] = useState<string | null>(null)
  const formId = useId()

  async function change(
    changes: Partial<Pick<StaffAccount, 'role' | 'active'>>,
    message: string,
  ) {
    setBusy(true)
    setError(null)
    try {
      onChanged(await staffApi.updateStaff(account.id, changes), message)
    } catch (requestError) {
      setError(messageOf(requestError))
    } finally {
      setBusy(false)
    }
  }

  async function reset(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)

    if (password.length < MIN_PASSWORD_LENGTH) {
      setError(`Das vorläufige Passwort muss mindestens ${MIN_PASSWORD_LENGTH} Zeichen lang sein.`)
      return
    }

    setBusy(true)
    try {
      await staffApi.resetPassword(account.id, password)
      setResetDone(password)
      setPassword('')
      setResetting(false)
      onChanged(
        { ...account, mustChangePassword: true },
        `Passwort von ${account.displayName} zurückgesetzt.`,
      )
    } catch (requestError) {
      setError(messageOf(requestError))
    } finally {
      setBusy(false)
    }
  }

  const name = account.displayName

  return (
    <li className="team-row">
      <div className="team-row-main">
        <h3>
          {name}
          {isSelf && <span className="team-self"> (du)</span>}
        </h3>
        <p className="team-meta">
          <span className="staff-mono">{account.username}</span>
          <span className={`team-badge ${account.role === 'ADMIN' ? 'is-admin' : ''}`}>
            {ROLE_LABEL[account.role]}
          </span>
          {!account.active && <span className="team-badge is-off">Deaktiviert</span>}
          {account.active && account.mustChangePassword && (
            <span className="team-badge is-pending">Vorläufiges Passwort</span>
          )}
        </p>
        <p className="team-meta">{formatLastLogin(account.lastLoginAt)}</p>
      </div>

      {!isSelf && (
        <div className="team-actions">
          <button
            type="button"
            className="staff-button"
            disabled={busy}
            onClick={() =>
              change(
                { active: !account.active },
                account.active ? `${name} deaktiviert.` : `${name} wieder aktiviert.`,
              )
            }
          >
            {account.active ? 'Deaktivieren' : 'Aktivieren'}
          </button>
          {account.active && (
            <button
              type="button"
              className="staff-button"
              disabled={busy}
              onClick={() =>
                change(
                  { role: account.role === 'ADMIN' ? 'STAFF' : 'ADMIN' },
                  account.role === 'ADMIN'
                    ? `${name} ist jetzt Personal.`
                    : `${name} ist jetzt Admin.`,
                )
              }
            >
              {account.role === 'ADMIN' ? 'Admin-Rechte entziehen' : 'Zum Admin machen'}
            </button>
          )}
          {account.active && (
            <button
              type="button"
              className="staff-button"
              aria-expanded={resetting}
              aria-controls={formId}
              disabled={busy}
              onClick={() => {
                setResetting((open) => !open)
                setResetDone(null)
                setError(null)
              }}
            >
              Passwort zurücksetzen
            </button>
          )}
        </div>
      )}

      {resetting && (
        <form id={formId} className="staff-form team-reset" onSubmit={reset}>
          <p className="staff-hint">
            {name} wird überall abgemeldet und muss beim nächsten Anmelden ein eigenes Passwort
            wählen.
          </p>
          <PasswordWithSuggestion
            id={`${formId}-password`}
            label="Neues vorläufiges Passwort"
            value={password}
            onChange={setPassword}
          />
          <div className="team-reset-actions">
            <button type="submit" className="btn btn--dark staff-submit" disabled={busy}>
              Zurücksetzen
            </button>
            <button
              type="button"
              className="staff-button staff-button--quiet"
              onClick={() => setResetting(false)}
            >
              Abbrechen
            </button>
          </div>
        </form>
      )}

      {resetDone && (
        <p className="staff-success team-row-message" role="status">
          Neues vorläufiges Passwort für {name}: <strong className="staff-mono">{resetDone}</strong>
        </p>
      )}

      {error && (
        <p className="staff-error team-row-message" role="alert">
          {error}
        </p>
      )}
    </li>
  )
}

export default StaffTeam
