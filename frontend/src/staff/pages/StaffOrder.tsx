import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { fetchMenuItems, type MenuItem } from '../../api/menu'
import { formatCents, formatEuro, toCents } from '../../utils/format'
import { ApiError, staffApi, type StaffOrderDetail } from '../api'
import { usePageTitle } from '../usePageTitle'
import { bremenClockTime, clockTime, waitingFor } from '../time'

const MAX_QUANTITY = 99

/** A line as the waiter is editing it. */
type EditLine = {
  menuItemId: number
  name: string
  unitCents: number
  /** What the guest asked for; 0 for added products. */
  original: number
  quantity: number
}

function normalize(text: string) {
  return text.toLocaleLowerCase('de-DE').normalize('NFD').replace(/[̀-ͯ]/g, '')
}

function linesFrom(order: StaffOrderDetail): EditLine[] {
  return order.items.map((item) => ({
    menuItemId: item.menuItemId,
    name: item.productName,
    unitCents: toCents(item.unitPrice),
    original: item.originalQuantity,
    quantity: item.quantity,
  }))
}

/**
 * One order, as the waiter checks it with the guest: change quantities,
 * add or remove products, then confirm once it is in the till, or cancel.
 */
function StaffOrder() {
  const { reference = '' } = useParams()
  const [order, setOrder] = useState<StaffOrderDetail | null>(null)
  const [lines, setLines] = useState<EditLine[]>([])
  const [loadError, setLoadError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [askCancel, setAskCancel] = useState(false)
  const [done, setDone] = useState<string | null>(null)
  const headingRef = useRef<HTMLHeadingElement>(null)

  usePageTitle(order?.shortCode ? `Bestellung ${order.shortCode}` : 'Bestellung')

  useEffect(() => {
    let active = true

    staffApi
      .getOrder(reference)
      .then((loaded) => {
        if (!active) return
        setOrder(loaded)
        setLines(linesFrom(loaded))
      })
      .catch((error: unknown) => {
        if (active) {
          setLoadError(error instanceof ApiError ? error.message : 'Die Bestellung konnte nicht geladen werden.')
        }
      })

    return () => {
      active = false
    }
  }, [reference])

  useEffect(() => {
    if (done) headingRef.current?.focus()
  }, [done])

  if (loadError) {
    return (
      <div className="staff-page">
        <h1>Bestellung</h1>
        <p className="staff-error" role="alert">
          {loadError}
        </p>
        <p>
          <Link to="/personal" className="text-link">
            Zur Übersicht
          </Link>
        </p>
      </div>
    )
  }

  if (!order) {
    return (
      <p className="staff-loading" role="status">
        Bestellung wird geladen …
      </p>
    )
  }

  const editable = order.status === 'PENDING'
  const totalCents = lines.reduce((sum, line) => sum + line.unitCents * line.quantity, 0)
  const changed = lines.some((line) => line.quantity !== line.original)
  const empty = lines.every((line) => line.quantity === 0)

  function setQuantity(menuItemId: number, quantity: number) {
    setActionError(null)
    setLines((current) =>
      current.map((line) =>
        line.menuItemId === menuItemId
          ? { ...line, quantity: Math.min(MAX_QUANTITY, Math.max(0, quantity)) }
          : line,
      ),
    )
  }

  function addProduct(item: MenuItem) {
    setActionError(null)
    setLines((current) => {
      const existing = current.find((line) => line.menuItemId === item.id)
      if (existing) {
        return current.map((line) =>
          line.menuItemId === item.id
            ? { ...line, quantity: Math.min(MAX_QUANTITY, line.quantity + 1) }
            : line,
        )
      }
      return [
        ...current,
        {
          menuItemId: item.id,
          name: item.sizeVolume ? `${item.name} (${item.sizeVolume})` : item.name,
          unitCents: toCents(item.price),
          original: 0,
          quantity: 1,
        },
      ]
    })
  }

  async function run(action: () => Promise<StaffOrderDetail>, message: string) {
    setBusy(true)
    setActionError(null)
    try {
      const updated = await action()
      setOrder(updated)
      setLines(linesFrom(updated))
      setAskCancel(false)
      setDone(message)
    } catch (error) {
      setActionError(error instanceof ApiError ? error.message : 'Das hat nicht geklappt.')
      // Someone else may have handled it meanwhile: show the current state.
      if (error instanceof ApiError && error.status === 409) {
        staffApi
          .getOrder(reference)
          .then((current) => {
            setOrder(current)
            if (current.status !== 'PENDING') setLines(linesFrom(current))
          })
          .catch(() => {})
      }
    } finally {
      setBusy(false)
    }
  }

  function confirm() {
    const finalLines = lines.map(({ menuItemId, quantity }) => ({ menuItemId, quantity }))
    const edited = lines.some((line, index) => line.quantity !== (order?.items[index]?.quantity ?? 0))
    void run(
      () => staffApi.confirmOrder(reference, edited ? finalLines : undefined),
      'Bestätigt. Die Bestellung ist abgeschlossen.',
    )
  }

  function cancel() {
    void run(() => staffApi.cancelOrder(reference), 'Storniert.')
  }

  return (
    <div className="staff-page staff-order">
      <p className="eyebrow">
        <Link to="/personal" className="staff-crumb">
          Übersicht
        </Link>{' '}
        · Bestellung
      </p>

      <div className="staff-order-head">
        <h1 ref={headingRef} tabIndex={-1}>
          <span className="staff-mono">{order.shortCode ?? 'Ohne Code'}</span>
        </h1>
        <StatusBadge status={order.status} />
      </div>

      <p className="staff-order-meta">
        Gesendet um {clockTime(order.createdAt)} ({waitingFor(order.createdAt)})
        {order.handledAt && (
          <>
            {' · '}
            {order.status === 'CANCELLED' ? 'storniert' : 'bestätigt'} um{' '}
            {bremenClockTime(order.handledAt)}
            {order.handledBy ? ` von ${order.handledBy}` : ''}
          </>
        )}
      </p>

      {done && (
        <p className="staff-success" role="status">
          {done}{' '}
          <Link to="/personal" className="staff-inline-link">
            Zur Übersicht
          </Link>
        </p>
      )}

      {order.note && (
        <div className="staff-order-note">
          <h2>Wünsche des Gasts</h2>
          <p>{order.note}</p>
        </div>
      )}

      <section aria-labelledby="order-lines-title">
        <h2 id="order-lines-title" className="visually-hidden">
          Artikel
        </h2>
        <ul className="edit-lines">
          {lines.map((line) => (
            <li
              key={line.menuItemId}
              className={`edit-line ${line.quantity === 0 ? 'is-removed' : ''}`}
            >
              <div className="edit-line-text">
                <span className="edit-line-name">{line.name}</span>
                <span className="edit-line-meta">
                  {formatCents(line.unitCents)}
                  {line.original === 0 && line.quantity > 0 && ' · neu hinzugefügt'}
                  {line.original > 0 && line.quantity !== line.original && ` · Gast: ${line.original}`}
                  {line.quantity === 0 && ' · entfernt'}
                </span>
              </div>

              {editable ? (
                <div className="edit-line-qty" role="group" aria-label={`Anzahl ${line.name}`}>
                  <button
                    type="button"
                    className="qty-button"
                    aria-label={`${line.name}: eins weniger`}
                    disabled={busy || line.quantity === 0}
                    onClick={() => setQuantity(line.menuItemId, line.quantity - 1)}
                  >
                    −
                  </button>
                  <span className="qty-value" aria-live="polite">
                    {line.quantity}
                  </span>
                  <button
                    type="button"
                    className="qty-button"
                    aria-label={`${line.name}: eins mehr`}
                    disabled={busy || line.quantity >= MAX_QUANTITY}
                    onClick={() => setQuantity(line.menuItemId, line.quantity + 1)}
                  >
                    +
                  </button>
                </div>
              ) : (
                <span className="qty-value">{line.quantity}×</span>
              )}

              <span className="edit-line-total">
                {formatCents(line.unitCents * line.quantity)}
              </span>
            </li>
          ))}
          <li className="edit-line edit-line--total">
            <span>Gesamt</span>
            <span className="edit-line-total">
              {editable ? formatCents(totalCents) : formatEuro(order.totalPrice)}
            </span>
          </li>
        </ul>
      </section>

      {editable && <AddProduct onAdd={addProduct} disabled={busy} />}

      {actionError && (
        <p className="staff-error" role="alert">
          {actionError}
        </p>
      )}

      {editable && (
        <div className="staff-order-actions">
          <p className="staff-hint">
            Erst in die Kasse eingeben, dann hier bestätigen.
            {changed && ' Die Änderungen werden mit der Bestätigung gespeichert.'}
          </p>
          <button
            type="button"
            className="btn btn--dark staff-submit"
            disabled={busy || empty}
            onClick={confirm}
          >
            {busy ? 'Wird gespeichert …' : `Bestätigen · ${formatCents(totalCents)}`}
          </button>
          {empty && (
            <p className="staff-hint">Ohne Artikel kann nur storniert werden.</p>
          )}

          {!askCancel ? (
            <button
              type="button"
              className="staff-button"
              disabled={busy}
              onClick={() => setAskCancel(true)}
            >
              Stornieren
            </button>
          ) : (
            <div className="staff-cancel-confirm" role="group" aria-label="Stornieren bestätigen">
              <p>Bestellung wirklich stornieren?</p>
              <button type="button" className="staff-button staff-button--danger" disabled={busy} onClick={cancel}>
                Ja, stornieren
              </button>
              <button
                type="button"
                className="staff-button staff-button--quiet"
                disabled={busy}
                onClick={() => setAskCancel(false)}
              >
                Nein
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  )
}

function StatusBadge({ status }: { status: StaffOrderDetail['status'] }) {
  const labels: Record<StaffOrderDetail['status'], [string, string]> = {
    PENDING: ['Offen', 'is-pending'],
    CONFIRMED: ['Bestätigt', 'is-admin'],
    COMPLETED: ['Abgeschlossen', 'is-admin'],
    CANCELLED: ['Storniert', 'is-off'],
  }
  const [label, variant] = labels[status]
  return <span className={`team-badge ${variant}`}>{label}</span>
}

/** Search the menu and add a product to the order. */
function AddProduct({ onAdd, disabled }: { onAdd: (item: MenuItem) => void; disabled: boolean }) {
  const [menu, setMenu] = useState<MenuItem[] | null>(null)
  const [query, setQuery] = useState('')
  const [menuError, setMenuError] = useState(false)

  useEffect(() => {
    const controller = new AbortController()
    fetchMenuItems(controller.signal)
      .then(setMenu)
      .catch(() => {
        if (!controller.signal.aborted) setMenuError(true)
      })
    return () => controller.abort()
  }, [])

  const results = useMemo(() => {
    const terms = normalize(query).split(/\s+/).filter(Boolean)
    if (!menu || terms.length === 0) return []
    return menu
      .filter((item) => {
        const text = normalize(`${item.name} ${item.sizeVolume ?? ''} ${item.subcategory ?? ''}`)
        return terms.every((term) => text.includes(term))
      })
      .slice(0, 8)
  }, [menu, query])

  return (
    <div className="add-product">
      <label htmlFor="add-product-search">Artikel hinzufügen</label>
      <input
        id="add-product-search"
        type="search"
        autoComplete="off"
        placeholder="z. B. Espresso"
        value={query}
        disabled={disabled || !menu}
        onChange={(event) => setQuery(event.target.value)}
      />
      {menuError && (
        <p className="staff-hint">Die Speisekarte konnte nicht geladen werden.</p>
      )}
      {results.length > 0 && (
        <ul className="add-product-results">
          {results.map((item) => (
            <li key={item.id}>
              <button
                type="button"
                className="add-product-option"
                disabled={disabled || !item.available}
                onClick={() => {
                  onAdd(item)
                  setQuery('')
                }}
              >
                <span className="add-product-name">
                  {item.name}
                  {item.sizeVolume ? ` (${item.sizeVolume})` : ''}
                  {item.subcategory && <small>{item.subcategory}</small>}
                </span>
                <span>{item.available ? formatEuro(item.price) : 'nicht verfügbar'}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
      {query.trim() && menu && results.length === 0 && (
        <p className="staff-hint">Nichts gefunden.</p>
      )}
    </div>
  )
}

export default StaffOrder
