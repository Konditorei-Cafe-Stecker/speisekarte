import { useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { AdminError, fetchAdminForecast } from '../api/admin'
import type { AdminForecast, ForecastDay, StaffingInput } from '../api/admin'
import { formatEuro } from '../utils/format'
import { readSession, removeSession, writeSession } from '../utils/sessionStore'
import './Admin.css'

const TOKEN_KEY = 'stecker.admin.token'
const HOURS = [9, 10, 11, 12, 13, 14, 15, 16, 17]

const dayFormatter = new Intl.DateTimeFormat('de-DE', {
  weekday: 'short',
  day: '2-digit',
  month: '2-digit',
})
const longDayFormatter = new Intl.DateTimeFormat('de-DE', {
  weekday: 'long',
  day: 'numeric',
  month: 'long',
})
const timestampFormatter = new Intl.DateTimeFormat('de-DE', {
  dateStyle: 'medium',
  timeStyle: 'short',
})
const numberFormatter = new Intl.NumberFormat('de-DE', { maximumFractionDigits: 0 })

/** "2026-11-23" → a local date without timezone shifts. */
function parseDay(day: string) {
  const [year, month, date] = day.split('-').map(Number)
  return new Date(year, month - 1, date)
}

function formatTemperature(day: ForecastDay) {
  if (day.tempMin === null || day.tempMax === null) return '–'
  return `${Math.round(day.tempMin)} bis ${Math.round(day.tempMax)} °C`
}

function useNoIndex() {
  useEffect(() => {
    const meta = document.createElement('meta')
    meta.name = 'robots'
    meta.content = 'noindex, nofollow'
    document.head.appendChild(meta)
    return () => meta.remove()
  }, [])
}

function Admin() {
  useNoIndex()
  const [token, setToken] = useState<string | null>(() =>
    readSession(TOKEN_KEY, (value) => (typeof value === 'string' ? value : null)),
  )
  const [staffing, setStaffing] = useState<StaffingInput>({ ordersPerStaffHour: 6, minStaff: 2 })
  const [forecast, setForecast] = useState<AdminForecast | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    if (!token) return
    const controller = new AbortController()
    // Wait briefly so typing in the staffing fields does not send a request per key.
    const timer = window.setTimeout(() => {
      setLoading(true)
      fetchAdminForecast(token, staffing, controller.signal)
        .then((data) => {
          setForecast(data)
          setError(null)
        })
        .catch((reason: unknown) => {
          if (controller.signal.aborted) return
          if (reason instanceof AdminError && reason.status === 401) {
            removeSession(TOKEN_KEY)
            setToken(null)
          }
          setError(reason instanceof Error ? reason.message : 'Unbekannter Fehler.')
        })
        .finally(() => {
          if (!controller.signal.aborted) setLoading(false)
        })
    }, 300)

    return () => {
      window.clearTimeout(timer)
      controller.abort()
    }
  }, [token, staffing])

  function logIn(code: string) {
    writeSession(TOKEN_KEY, code)
    setError(null)
    setToken(code)
  }

  function logOut() {
    removeSession(TOKEN_KEY)
    setToken(null)
    setForecast(null)
  }

  if (!token) {
    return <Login error={error} onSubmit={logIn} />
  }

  return (
    <section className="admin container">
      <header className="admin-header">
        <div>
          <p className="eyebrow">Planung</p>
          <h1>
            Die nächsten <em>7 Tage</em>
          </h1>
          {forecast && (
            <p className="admin-meta">
              Prognose vom {timestampFormatter.format(new Date(forecast.generatedAt))} ·{' '}
              {longDayFormatter.format(parseDay(forecast.from))} bis{' '}
              {longDayFormatter.format(parseDay(forecast.to))}
            </p>
          )}
        </div>
        <button type="button" className="btn btn--dark admin-logout" onClick={logOut}>
          Abmelden
        </button>
      </header>

      {forecast?.basis === 'SYNTHETIC' && (
        <p className="admin-notice" role="note">
          <strong>Simulationsdaten.</strong> Die Prognose lernt aus simulierten Bestellungen,
          weil noch keine echten Verkaufsdaten vorliegen. Wetter, Spiele und Termine sind echt;
          die Zahlen sind Richtwerte, keine Erfahrungswerte.
        </p>
      )}

      {error && (
        <p className="admin-error" role="alert">
          {error}
        </p>
      )}

      {!forecast && !error && <p className="admin-loading">Prognose wird geladen …</p>}

      {forecast && (
        <div className={loading ? 'admin-body is-updating' : 'admin-body'}>
          <Summary forecast={forecast} />
          <Revenue days={forecast.days} />
          <Staffing
            forecast={forecast}
            staffing={staffing}
            onChange={setStaffing}
          />
          <Inventory forecast={forecast} />
        </div>
      )}
    </section>
  )
}

function Login({ error, onSubmit }: { error: string | null; onSubmit: (code: string) => void }) {
  const [code, setCode] = useState('')

  function submit(event: FormEvent) {
    event.preventDefault()
    if (code.trim()) onSubmit(code.trim())
  }

  return (
    <section className="admin admin-login container">
      <p className="eyebrow">Planung</p>
      <h1>Anmelden</h1>
      <form className="admin-login-form" onSubmit={submit}>
        <label htmlFor="admin-code">Zugangscode</label>
        <input
          id="admin-code"
          type="password"
          autoComplete="current-password"
          value={code}
          onChange={(event) => setCode(event.target.value)}
        />
        <button type="submit" className="btn btn--accent">
          Weiter
        </button>
      </form>
      {error && (
        <p className="admin-error" role="alert">
          {error}
        </p>
      )}
      <p className="admin-hint">
        Der Code bleibt nur in diesem Browser-Tab gespeichert, bis er geschlossen wird.
      </p>
    </section>
  )
}

function Summary({ forecast }: { forecast: AdminForecast }) {
  const { summary } = forecast
  return (
    <div className="admin-tiles">
      <div className="admin-tile">
        <p className="admin-tile-label">Umsatz erwartet</p>
        <p className="admin-tile-value">{formatEuro(summary.expectedRevenue)}</p>
        <p className="admin-tile-note">
          wahrscheinlich {formatEuro(summary.revenueLow)} – {formatEuro(summary.revenueHigh)}
        </p>
      </div>
      <div className="admin-tile">
        <p className="admin-tile-label">Bestellungen</p>
        <p className="admin-tile-value">{numberFormatter.format(summary.expectedOrders)}</p>
        <p className="admin-tile-note">
          ca. {numberFormatter.format(summary.expectedOrders / forecast.days.length)} pro Öffnungstag
        </p>
      </div>
      <div className="admin-tile">
        <p className="admin-tile-label">Personalstunden</p>
        <p className="admin-tile-value">{summary.staffHours}</p>
        <p className="admin-tile-note">im Service, laut Personalplan unten</p>
      </div>
      <div className="admin-tile">
        <p className="admin-tile-label">Stärkster Tag</p>
        <p className="admin-tile-value">{dayFormatter.format(parseDay(summary.busiestDay))}</p>
        <p className="admin-tile-note">nach erwartetem Umsatz</p>
      </div>
    </div>
  )
}

function Revenue({ days }: { days: ForecastDay[] }) {
  const max = Math.max(...days.map((day) => day.revenueHigh))
  return (
    <section className="admin-section" aria-labelledby="umsatz">
      <h2 id="umsatz">Umsatz pro Tag</h2>
      <p className="admin-section-intro">
        Der Balken zeigt den erwarteten Umsatz, die Linie den wahrscheinlichen Bereich
        (8 von 10 Tagen liegen darin).
      </p>
      <ol className="admin-days">
        {days.map((day) => (
          <li key={day.day} className="admin-day">
            <div className="admin-day-head">
              <span className="admin-day-name">{dayFormatter.format(parseDay(day.day))}</span>
              <span className="admin-day-value">{formatEuro(day.expectedRevenue)}</span>
            </div>
            <div
              className="admin-bar"
              title={`${formatEuro(day.revenueLow)} – ${formatEuro(day.revenueHigh)}`}
            >
              <span
                className="admin-bar-fill"
                style={{ width: `${(day.expectedRevenue / max) * 100}%` }}
              />
              <span
                className="admin-bar-range"
                style={{
                  left: `${(day.revenueLow / max) * 100}%`,
                  width: `${((day.revenueHigh - day.revenueLow) / max) * 100}%`,
                }}
              />
            </div>
            <p className="admin-day-facts">
              {numberFormatter.format(day.expectedOrders)} Bestellungen ({day.ordersLow}–
              {day.ordersHigh}) · {formatTemperature(day)}
              {day.rainyHours > 0 && ` · ${day.rainyHours} Std. Regen`}
            </p>
            {day.events.length > 0 && (
              <ul className="admin-chips" aria-label="Termine">
                {day.events.map((event) => (
                  <li key={event} className="admin-chip">
                    {event}
                  </li>
                ))}
              </ul>
            )}
            {day.offers.length > 0 && (
              <ul className="admin-offers" aria-label="Angebotsvorschläge">
                {day.offers.map((offer) => (
                  <li key={offer}>{offer}</li>
                ))}
              </ul>
            )}
          </li>
        ))}
      </ol>
    </section>
  )
}

function Staffing({
  forecast,
  staffing,
  onChange,
}: {
  forecast: AdminForecast
  staffing: StaffingInput
  onChange: (staffing: StaffingInput) => void
}) {
  const maxStaff = Math.max(1, ...forecast.days.flatMap((day) => day.hours.map((h) => h.staff)))

  return (
    <section className="admin-section" aria-labelledby="personal">
      <h2 id="personal">Personalplanung (Service)</h2>
      <p className="admin-section-intro">
        Personen pro Stunde, damit auch ein lebhafter Tag (9 von 10 Tagen ruhiger) gut
        abgedeckt ist. Die Annahme darunter bitte an die eigene Erfahrung anpassen.
      </p>

      <div className="admin-controls">
        <label>
          Bestellungen, die eine Person pro Stunde schafft
          <input
            type="number"
            min={1}
            max={50}
            step={1}
            value={staffing.ordersPerStaffHour}
            onChange={(event) => {
              const value = Number(event.target.value)
              if (value >= 1 && value <= 50) onChange({ ...staffing, ordersPerStaffHour: value })
            }}
          />
        </label>
        <label>
          Mindestbesetzung
          <input
            type="number"
            min={0}
            max={20}
            step={1}
            value={staffing.minStaff}
            onChange={(event) => {
              const value = Number(event.target.value)
              if (value >= 0 && value <= 20) onChange({ ...staffing, minStaff: value })
            }}
          />
        </label>
      </div>

      <div className="admin-table-wrap">
        <table className="admin-staff">
          <thead>
            <tr>
              <th scope="col">Tag</th>
              {HOURS.map((hour) => (
                <th key={hour} scope="col">
                  {hour}
                </th>
              ))}
              <th scope="col">Std.</th>
            </tr>
          </thead>
          <tbody>
            {forecast.days.map((day) => {
              const byHour = new Map(day.hours.map((h) => [h.hour, h]))
              return (
                <tr key={day.day}>
                  <th scope="row">{dayFormatter.format(parseDay(day.day))}</th>
                  {HOURS.map((hour) => {
                    const slot = byHour.get(hour)
                    if (!slot) {
                      return (
                        <td key={hour} className="is-closed" aria-label="geschlossen">
                          –
                        </td>
                      )
                    }
                    // Minimum staffing stays light; only extra people get darker.
                    const extra = slot.staff - forecast.staffing.minStaff
                    const range = Math.max(1, maxStaff - forecast.staffing.minStaff)
                    const level = extra <= 0 ? 1 : 1 + Math.ceil((extra / range) * 3)
                    return (
                      <td
                        key={hour}
                        className={`staff-level-${level}`}
                        title={`${hour}:00 – ca. ${slot.expectedOrders.toFixed(1)} Bestellungen, lebhaft bis ${slot.ordersHigh}`}
                      >
                        {slot.staff}
                      </td>
                    )
                  })}
                  <td className="admin-staff-total">{day.staffHours}</td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
      <p className="admin-hint">
        17-Uhr-Spalte: nur bis 17:30 (letzte Bestellungen). Küche und Theke sind hier
        nicht eingerechnet.
      </p>
    </section>
  )
}

function Inventory({ forecast }: { forecast: AdminForecast }) {
  const categories = useMemo(
    () => Array.from(new Set(forecast.products.map((p) => p.category))).sort(),
    [forecast.products],
  )
  const [category, setCategory] = useState<string>('Alle')
  const [query, setQuery] = useState('')

  const shown = forecast.products.filter(
    (product) =>
      (category === 'Alle' || product.category === category) &&
      product.product.toLowerCase().includes(query.trim().toLowerCase()),
  )

  return (
    <section className="admin-section" aria-labelledby="waren">
      <h2 id="waren">Warenbedarf für die Woche</h2>
      <p className="admin-section-intro">
        Erwartete Stückzahl pro Produkt und eine Menge für den Fall viel Betriebs (in 9 von 10
        Wochen reicht sie). Zutaten wie Kaffee oder Milch lassen sich erst berechnen, wenn
        Rezepturen hinterlegt sind.
      </p>

      <div className="admin-controls">
        <div className="admin-filter" role="group" aria-label="Kategorie">
          {['Alle', ...categories].map((name) => (
            <button
              key={name}
              type="button"
              className={name === category ? 'admin-filter-btn is-active' : 'admin-filter-btn'}
              aria-pressed={name === category}
              onClick={() => setCategory(name)}
            >
              {name}
            </button>
          ))}
        </div>
        <label className="admin-search">
          Produkt suchen
          <input type="search" value={query} onChange={(event) => setQuery(event.target.value)} />
        </label>
      </div>

      <div className="admin-table-wrap">
        <table className="admin-products">
          <thead>
            <tr>
              <th scope="col">Produkt</th>
              <th scope="col" className="num">
                Erwartet
              </th>
              <th scope="col" className="num">
                Viel Betrieb
              </th>
              <th scope="col" className="num">
                Umsatz
              </th>
            </tr>
          </thead>
          <tbody>
            {shown.map((product) => (
              <tr key={product.menuItemId}>
                <th scope="row">
                  {product.product}
                  <span className="admin-product-group">
                    {product.subcategory ?? product.category}
                  </span>
                </th>
                <td className="num">{numberFormatter.format(product.expectedUnits)}</td>
                <td className="num">{product.busyCaseUnits}</td>
                <td className="num">{formatEuro(product.expectedRevenue)}</td>
              </tr>
            ))}
            {shown.length === 0 && (
              <tr>
                <td colSpan={4}>Keine Produkte gefunden.</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </section>
  )
}

export default Admin
