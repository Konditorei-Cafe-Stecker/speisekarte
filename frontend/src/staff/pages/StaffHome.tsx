import { useCallback, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError, staffApi, type StaffOrderSummary } from '../api'
import { useStaffAuth } from '../StaffAuth'
import { usePageTitle } from '../usePageTitle'
import { usePolling } from '../usePolling'
import { bremenClockTime, bremenToday, clockTime, minutesWaiting, waitingFor } from '../time'
import { formatEuro } from '../../utils/format'

const REFRESH_MS = 15_000

/** Orders older than this are marked, so none is forgotten. */
const LONG_WAIT_MINUTES = 10

function StaffHome() {
  usePageTitle('Übersicht')
  const auth = useStaffAuth()

  const [open, setOpen] = useState<StaffOrderSummary[] | null>(null)
  const [today, setToday] = useState<StaffOrderSummary[]>([])
  const [loadError, setLoadError] = useState<string | null>(null)
  const [now, setNow] = useState(() => new Date())

  const refresh = useCallback(async () => {
    try {
      const [openOrders, handled] = await Promise.all([
        staffApi.listOrders('open'),
        staffApi.listOrders('today'),
      ])
      setOpen(openOrders)
      setToday(handled)
      setLoadError(null)
      setNow(new Date())
    } catch (error) {
      setLoadError(
        error instanceof ApiError ? error.message : 'Die Bestellungen konnten nicht geladen werden.',
      )
    }
  }, [])

  usePolling(refresh, REFRESH_MS)

  if (auth.status !== 'signedIn') return null
  const { me } = auth

  // Today's orders first; open orders from earlier days (guests who never
  // showed their code) are kept apart so they do not push today's down.
  const todayDate = bremenToday(now)
  const openToday = open?.filter((order) => order.createdAt.startsWith(todayDate)) ?? null
  const openOlder = open?.filter((order) => !order.createdAt.startsWith(todayDate)) ?? []

  return (
    <div className="staff-page">
      <p className="eyebrow">Personalbereich</p>
      <h1>Hallo, {me.displayName}.</h1>

      <CodeLookup />

      <section className="staff-section" aria-labelledby="open-orders-title">
        <div className="staff-section-head">
          <h2 id="open-orders-title">
            Offene Bestellungen{openToday ? ` (${openToday.length})` : ''}
          </h2>
          <p className="staff-hint">Aktualisiert sich alle 15 Sekunden.</p>
        </div>

        {loadError && (
          <p className="staff-error" role="alert">
            {loadError}
          </p>
        )}

        {!open && !loadError && <p role="status">Wird geladen …</p>}

        {openToday && openToday.length === 0 && (
          <p className="staff-empty">Gerade keine offenen Bestellungen.</p>
        )}

        {openToday && openToday.length > 0 && (
          <OrderCards orders={openToday} now={now} />
        )}

        {openOlder.length > 0 && (
          <details className="staff-details staff-older">
            <summary>
              <h3>Offen seit früheren Tagen ({openOlder.length})</h3>
            </summary>
            <p className="staff-hint">
              Diese Gäste haben ihren Code nie gezeigt. Öffne sie und storniere sie, wenn sie
              erledigt sind.
            </p>
            <OrderCards orders={openOlder} now={now} />
          </details>
        )}
      </section>

      {today.length > 0 && (
        <section className="staff-section" aria-labelledby="today-title">
          <details className="staff-details">
            <summary>
              <h2 id="today-title">Heute erledigt ({today.length})</h2>
            </summary>
            <ul className="order-history">
              {today.map((order) => (
                <li key={order.orderReference}>
                  <Link to={`/personal/bestellung/${order.orderReference}`}>
                    {order.shortCode ?? 'Ohne Code'}
                  </Link>
                  <span className={`team-badge ${order.status === 'CANCELLED' ? 'is-off' : ''}`}>
                    {order.status === 'CANCELLED' ? 'Storniert' : 'Bestätigt'}
                  </span>
                  <span>
                    {order.handledAt ? bremenClockTime(order.handledAt) : ''}
                    {order.handledBy ? ` · ${order.handledBy}` : ''}
                  </span>
                  <span className="order-history-total">{formatEuro(order.totalPrice)}</span>
                </li>
              ))}
            </ul>
          </details>
        </section>
      )}

      <ul className="staff-links">
        {me.role === 'ADMIN' && (
          <li>
            <Link to="/personal/team">Team verwalten</Link>
            <span>Konten anlegen, deaktivieren, Passwörter zurücksetzen</span>
          </li>
        )}
        <li>
          <Link to="/personal/passwort">Passwort ändern</Link>
          <span>Dein eigenes Passwort für die Anmeldung</span>
        </li>
      </ul>
    </div>
  )
}

function OrderCards({ orders, now }: { orders: StaffOrderSummary[]; now: Date }) {
  return (
    <ul className="order-cards">
      {orders.map((order) => {
        const longWait = minutesWaiting(order.createdAt, now) >= LONG_WAIT_MINUTES
        return (
          <li key={order.orderReference}>
            <Link
              to={`/personal/bestellung/${order.orderReference}`}
              className={`order-card ${longWait ? 'is-waiting' : ''}`}
            >
              <span className="order-card-code">{order.shortCode ?? '—'}</span>
              <span className="order-card-info">
                <span>
                  {clockTime(order.createdAt, now)} · {waitingFor(order.createdAt, now)}
                </span>
                <span>
                  {order.itemCount} Artikel · {formatEuro(order.totalPrice)}
                </span>
              </span>
              {order.hasNote && <span className="team-badge is-pending">Wünsche</span>}
              {longWait && <span className="team-badge is-off">Wartet lange</span>}
            </Link>
          </li>
        )
      })}
    </ul>
  )
}

/** Opens an order by the four characters the guest reads out. */
function CodeLookup() {
  const navigate = useNavigate()
  const [code, setCode] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [searching, setSearching] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const cleaned = code.replace(/\s+/g, '').toUpperCase()
    if (!cleaned) return

    setSearching(true)
    setError(null)
    try {
      const order = await staffApi.findOrderByCode(cleaned)
      navigate(`/personal/bestellung/${order.orderReference}`)
    } catch (requestError) {
      setError(requestError instanceof ApiError ? requestError.message : 'Das hat nicht geklappt.')
      setSearching(false)
    }
  }

  return (
    <form className="code-lookup staff-panel" onSubmit={handleSubmit}>
      <label htmlFor="order-code">Code vom Handy des Gasts</label>
      <p id="order-code-hint" className="staff-hint">
        Oder scanne den QR-Code mit der Kamera deines Handys.
      </p>
      <div className="staff-input-row">
        <input
          id="order-code"
          className="staff-mono code-lookup-input"
          aria-describedby="order-code-hint"
          autoComplete="off"
          autoCapitalize="characters"
          spellCheck={false}
          maxLength={6}
          value={code}
          onChange={(event) => setCode(event.target.value.toUpperCase())}
        />
        <button type="submit" className="btn btn--dark staff-submit" disabled={searching}>
          Öffnen
        </button>
      </div>
      {error && (
        <p className="staff-error" role="alert">
          {error}
        </p>
      )}
    </form>
  )
}

export default StaffHome
