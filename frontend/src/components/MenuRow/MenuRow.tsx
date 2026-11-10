import { useRef } from 'react'
import './MenuRow.css'

type MenuRowProps = {
  name: string
  price: string
  /** Size, description and notes, already filtered for empty values. */
  details: string[]
  /** Name plus size, used to tell variants apart in button labels. */
  label: string
  available: boolean
  quantity: number
  onAdd: () => void
  onRemove: () => void
}

function MenuRow({
  name,
  price,
  details,
  label,
  available,
  quantity,
  onAdd,
  onRemove,
}: MenuRowProps) {
  const addButtonRef = useRef<HTMLButtonElement>(null)
  const selected = quantity > 0

  function handleRemove() {
    // The minus button disappears at zero; keep keyboard focus in the row.
    if (quantity === 1) addButtonRef.current?.focus()
    onRemove()
  }

  return (
    <li className={`menu-row ${available ? '' : 'is-unavailable'}`}>
      <div className="menu-row-main">
        <div className="menu-row-top">
          <h3>{name}</h3>
          <span className="menu-row-leader" aria-hidden="true" />
          <span className="menu-row-price">{price}</span>
        </div>

        {details.length > 0 && (
          <p className="menu-row-meta">{details.join(' · ')}</p>
        )}

        {!available && (
          <p className="menu-row-meta menu-row-unavailable">
            Derzeit nicht verfügbar
          </p>
        )}
      </div>

      <div className="menu-row-controls">
        {selected && (
          <>
            <button
              type="button"
              className="qty-button"
              aria-label={`${label}: Anzahl verringern`}
              onClick={handleRemove}
            >
              <svg
                aria-hidden="true"
                width="16"
                height="16"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2.2"
                strokeLinecap="round"
              >
                <path d="M5 12h14" />
              </svg>
            </button>

            <span className="menu-row-quantity">
              <span className="visually-hidden">Menge: </span>
              {quantity}
            </span>
          </>
        )}

        <button
          ref={addButtonRef}
          type="button"
          className={`qty-button ${selected ? 'is-filled' : ''}`}
          aria-label={`${label}: hinzufügen`}
          disabled={!available}
          onClick={onAdd}
        >
          <svg
            aria-hidden="true"
            width="16"
            height="16"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2.2"
            strokeLinecap="round"
          >
            <path d="M12 5v14M5 12h14" />
          </svg>
        </button>
      </div>
    </li>
  )
}

export default MenuRow
