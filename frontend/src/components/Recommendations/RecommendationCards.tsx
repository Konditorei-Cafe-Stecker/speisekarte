import { formatWindow } from '../../api/recommendations'
import type { Recommendation } from '../../api/recommendations'
import type { MenuItem } from '../../api/menu'
import { formatCents, toCents } from '../../utils/format'
import './Recommendations.css'

type OrderControls = {
  quantities: Record<number, number>
  onAdd: (item: MenuItem) => void
  onRemove: (item: MenuItem) => void
}

type RecommendationCardsProps = {
  recommendations: Recommendation[]
  /** Add the dishes to the order right here. */
  order?: OrderControls
}

function productLabel(item: MenuItem) {
  return [item.name, item.sizeVolume].filter(Boolean).join(', ')
}

/**
 * Today's recommendations as a slim row of cards at the top of the
 * Speisekarte: a heading, its time window if it has one ("9–12 Uhr"), and
 * the dishes with their normal prices.
 */
function RecommendationCards({ recommendations, order }: RecommendationCardsProps) {
  return (
    <ul className="recommendation-cards" role="list">
      {recommendations.map((recommendation) => (
        <li
          key={recommendation.kind}
          className={`recommendation-card recommendation-card--${recommendation.kind
            .toLowerCase()
            .replaceAll('_', '-')}`}
        >
          <div className="recommendation-head">
            <h3>{recommendation.title}</h3>
            {formatWindow(recommendation) && (
              <span className="recommendation-window">{formatWindow(recommendation)}</span>
            )}
          </div>

          <ul className="recommendation-products" role="list">
            {recommendation.products.map((item) => {
              const label = productLabel(item)
              const quantity = order?.quantities[item.id] ?? 0

              return (
                <li key={item.id} className="recommendation-product">
                  <span className="recommendation-product-name">
                    {item.name}
                    {item.sizeVolume && <small>{item.sizeVolume}</small>}
                    {/* Tells variants with the same name apart, e.g. the
                        two belegte Brötchen. */}
                    {item.description && <small>{item.description}</small>}
                  </span>
                  <span className="recommendation-product-price">
                    {formatCents(toCents(item.price))}
                  </span>

                  {order && (
                    <span className="recommendation-product-controls">
                      {quantity > 0 && (
                        <>
                          <button
                            type="button"
                            className="qty-button"
                            aria-label={`${label}: Anzahl verringern`}
                            onClick={() => order.onRemove(item)}
                          >
                            <MinusIcon />
                          </button>
                          <span className="menu-row-quantity">
                            <span className="visually-hidden">Menge: </span>
                            {quantity}
                          </span>
                        </>
                      )}
                      <button
                        type="button"
                        className={`qty-button ${quantity > 0 ? 'is-filled' : ''}`}
                        aria-label={`${label}: hinzufügen`}
                        onClick={() => order.onAdd(item)}
                      >
                        <PlusIcon />
                      </button>
                    </span>
                  )}
                </li>
              )
            })}
          </ul>
        </li>
      ))}
    </ul>
  )
}

function PlusIcon() {
  return (
    <svg aria-hidden="true" width="16" height="16" viewBox="0 0 24 24" fill="none"
      stroke="currentColor" strokeWidth="2.2" strokeLinecap="round">
      <path d="M12 5v14M5 12h14" />
    </svg>
  )
}

function MinusIcon() {
  return (
    <svg aria-hidden="true" width="16" height="16" viewBox="0 0 24 24" fill="none"
      stroke="currentColor" strokeWidth="2.2" strokeLinecap="round">
      <path d="M5 12h14" />
    </svg>
  )
}

export default RecommendationCards
