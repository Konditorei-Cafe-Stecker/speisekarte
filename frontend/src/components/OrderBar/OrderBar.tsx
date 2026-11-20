import { useEffect, useRef } from 'react'
import { MAX_NOTE_LENGTH, staffOrderUrl, type OrderResponse } from '../../api/orders'
import { formatCents, formatEuro } from '../../utils/format'
import OrderQrCode from '../OrderQrCode/OrderQrCode'
import './OrderBar.css'

export type OrderLine = {
  id: number
  label: string
  quantity: number
  totalCents: number
}

type OrderBarProps = {
  lines: OrderLine[]
  count: number
  totalCents: number
  open: boolean
  submitting: boolean
  /** German message shown inside the sheet when sending failed. */
  error: string | null
  /** The last order that was sent successfully, if any. */
  completedOrder: OrderResponse | null
  /** Wishes for the waiter, typed into the sheet before sending. */
  note: string
  onNoteChange: (note: string) => void
  onToggle: () => void
  onClose: () => void
  onClear: () => void
  onSubmit: () => void
  onDismissOrder: () => void
}

const statusLabels: Record<OrderResponse['status'], string> = {
  PENDING: 'Wartet auf die Bedienung',
  CONFIRMED: 'Bestätigt',
  COMPLETED: 'Abgeschlossen',
  CANCELLED: 'Storniert',
}

/** Heading of the confirmation and text of the pill, by status. */
const confirmationTitles: Record<OrderResponse['status'], string> = {
  PENDING: 'Bestellung gesendet',
  CONFIRMED: 'Bestellung bestätigt',
  COMPLETED: 'Bestellung abgeschlossen',
  CANCELLED: 'Bestellung storniert',
}

/** "7KX4" reads better as "7K X4" from across a table. */
function spacedCode(code: string) {
  return code.length === 4 ? `${code.slice(0, 2)} ${code.slice(2)}` : code
}

function statusLabel(status: OrderResponse['status']) {
  return statusLabels[status] ?? status
}

function OrderBar({
  lines,
  count,
  totalCents,
  open,
  submitting,
  error,
  completedOrder,
  note,
  onNoteChange,
  onToggle,
  onClose,
  onClear,
  onSubmit,
  onDismissOrder,
}: OrderBarProps) {
  const toggleRef = useRef<HTMLButtonElement>(null)
  const confirmationRef = useRef<HTMLHeadingElement>(null)
  const orderReference = completedOrder?.orderReference

  // Escape closes the sheet and returns focus to the button that opened it.
  useEffect(() => {
    if (!open) return

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key !== 'Escape') return

      onClose()
      toggleRef.current?.focus()
    }

    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [open, onClose])

  // After a successful order, move focus to the confirmation so screen
  // readers announce it. Does nothing while the sheet is closed.
  useEffect(() => {
    if (!orderReference) return

    confirmationRef.current?.focus({ preventScroll: true })
  }, [orderReference])

  if (count === 0 && !completedOrder) return null

  const showConfirmation = count === 0 && completedOrder !== null

  return (
    <aside className="order-bar" aria-label="Deine Bestellung">
      <div className="order-pill">
        <p className="order-pill-text" role="status">
          {showConfirmation && completedOrder ? (
            <strong>{confirmationTitles[completedOrder.status]}</strong>
          ) : (
            <>
              {count} Artikel · <strong>{formatCents(totalCents)}</strong>
            </>
          )}
        </p>

        <button
          ref={toggleRef}
          type="button"
          className="order-pill-toggle"
          aria-expanded={open}
          aria-controls={open ? 'order-sheet' : undefined}
          onClick={onToggle}
        >
          {open ? 'Schließen' : 'Bestellung ansehen'}
        </button>
      </div>

      {open && showConfirmation && completedOrder && (
        <div id="order-sheet" className="order-sheet">
          <h2 ref={confirmationRef} tabIndex={-1}>
            {confirmationTitles[completedOrder.status]}
          </h2>

          {completedOrder.status === 'PENDING' && (
            <div className="order-confirmation order-confirmation--code">
              <p>
                Zeig diesen Code der Bedienung. Sie scannt ihn oder tippt die
                vier Zeichen ein.
              </p>
              <OrderQrCode
                value={staffOrderUrl(completedOrder.orderReference)}
                label={`QR-Code der Bestellung ${completedOrder.shortCode ?? ''}`}
              />
              {completedOrder.shortCode ? (
                <p className="order-code">
                  <span>Code</span>
                  <strong aria-label={completedOrder.shortCode.split('').join(' ')}>
                    {spacedCode(completedOrder.shortCode)}
                  </strong>
                </p>
              ) : (
                <p className="order-number">
                  <span>Bestellnummer</span>
                  <strong>{completedOrder.orderReference}</strong>
                </p>
              )}
            </div>
          )}

          {completedOrder.status === 'CONFIRMED' && (
            <p className="order-confirmation">
              Die Bedienung hat deine Bestellung aufgenommen. Guten Appetit!
            </p>
          )}

          {completedOrder.status === 'CANCELLED' && (
            <p className="order-confirmation">
              Die Bestellung wurde storniert. Sprich bitte die Bedienung an,
              falls das nicht stimmt.
            </p>
          )}

          <p className="order-status" role="status">
            Status: <strong>{statusLabel(completedOrder.status)}</strong>
          </p>

          {completedOrder.note && (
            <p className="order-note-sent">
              <span>Deine Wünsche</span>
              {completedOrder.note}
            </p>
          )}

          <ul className="order-lines">
            {completedOrder.items.map((item) => (
              <li key={item.menuItemId}>
                <span>
                  {item.quantity} × {item.productName}
                </span>
                <span className="order-line-total">
                  {formatEuro(item.lineTotal)}
                </span>
              </li>
            ))}
            <li className="order-lines-total">
              <span>Gesamt</span>
              <span className="order-line-total">
                {formatEuro(completedOrder.totalPrice)}
              </span>
            </li>
          </ul>

          <button
            type="button"
            className="order-quiet"
            onClick={onDismissOrder}
          >
            Bestätigung entfernen
          </button>
        </div>
      )}

      {open && !showConfirmation && (
        <div id="order-sheet" className="order-sheet">
          <div className="order-sheet-head">
            <h2>Deine Bestellung</h2>
            <button
              type="button"
              className="order-quiet"
              disabled={submitting}
              onClick={onClear}
            >
              Auswahl löschen
            </button>
          </div>

          {completedOrder && (
            <p className="order-previous">
              Zuletzt gesendet:{' '}
              {completedOrder.shortCode
                ? `Code ${completedOrder.shortCode}`
                : `Nr. ${completedOrder.orderReference}`}{' '}
              · {statusLabel(completedOrder.status)}
            </p>
          )}

          <ul className="order-lines">
            {lines.map((line) => (
              <li key={line.id}>
                <span>
                  {line.quantity} × {line.label}
                </span>
                <span className="order-line-total">
                  {formatCents(line.totalCents)}
                </span>
              </li>
            ))}
          </ul>

          <div className="order-note">
            <label htmlFor="order-note">Wünsche an die Bedienung (optional)</label>
            <textarea
              id="order-note"
              rows={2}
              maxLength={MAX_NOTE_LENGTH}
              placeholder="z. B. ohne Sahne, Hafermilch, alles zusammen bringen"
              value={note}
              disabled={submitting}
              onChange={(event) => onNoteChange(event.target.value)}
            />
          </div>

          {error && (
            <p className="order-error" role="alert">
              {error}
            </p>
          )}

          <button
            type="button"
            className="order-submit"
            disabled={submitting}
            onClick={onSubmit}
          >
            {submitting
              ? 'Bestellung wird gesendet…'
              : `Bestellung absenden · ${formatCents(totalCents)}`}
          </button>
        </div>
      )}
    </aside>
  )
}

export default OrderBar
