import { useEffect, useState } from 'react'
import './Menu.css'
import ProductCard from '../components/ProductCard/ProductCard'
import { fetchMenuItems } from '../api/menu'
import type { MenuItem } from '../api/menu'

const euroFormatter = new Intl.NumberFormat('de-DE', {
  style: 'currency',
  currency: 'EUR',
})

function categoryId(category: string) {
  return category
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9]+/g, '-')
}

function Menu() {
  const [items, setItems] = useState<MenuItem[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [attempt, setAttempt] = useState(0)
  const [quantities, setQuantities] = useState<Record<number, number>>({})

  useEffect(() => {
    const controller = new AbortController()

    fetchMenuItems(controller.signal)
      .then((menuItems) => {
        if (controller.signal.aborted) return

        setItems(menuItems)
        setLoading(false)
      })
      .catch(() => {
        if (controller.signal.aborted) return

        setError('Die Speisekarte konnte nicht geladen werden.')
        setLoading(false)
      })

    return () => controller.abort()
  }, [attempt])

  useEffect(() => {
    if (items.length === 0) return

    const scrollToCategory = () => {
      let id: string

      try {
        id = decodeURIComponent(window.location.hash.slice(1))
      } catch {
        return
      }

      if (!id) return

      document.getElementById(id)?.scrollIntoView({
        behavior: 'auto',
        block: 'start',
      })
    }

    const frame = requestAnimationFrame(scrollToCategory)
    window.addEventListener('hashchange', scrollToCategory)

    return () => {
      cancelAnimationFrame(frame)
      window.removeEventListener('hashchange', scrollToCategory)
    }
  }, [items])

  function retryLoading() {
    setError(null)
    setLoading(true)
    setAttempt((previous) => previous + 1)
  }

  function changeQuantity(item: MenuItem, quantity: number) {
    setQuantities((previous) => {
      const currentQuantity = previous[item.id] ?? 0

      if (!item.available && quantity > currentQuantity) {
        return previous
      }

      return {
        ...previous,
        [item.id]: Math.max(0, quantity),
      }
    })
  }

  const categories = [...new Set(items.map((item) => item.category))]

  const selectedQuantity = items.reduce(
    (total, item) => total + (quantities[item.id] ?? 0),
    0,
  )

  const totalCents = items.reduce(
    (total, item) =>
      total + Math.round(item.price * 100) * (quantities[item.id] ?? 0),
    0,
  )

  return (
    <section className="menu" aria-busy={loading}>
      <div className="menu-header">
        <p className="menu-eyebrow">UNSERE AUSWAHL</p>
        <h1>Speisekarte</h1>
        <p className="menu-description">
          Entdecke unsere Getränke, Speisen und Desserts.
        </p>
      </div>

      {!loading && !error && items.length > 0 && (
        <aside className="menu-summary" aria-label="Deine Auswahl">
          <div>
            <p>{selectedQuantity} Artikel ausgewählt</p>
            <p>
              Gesamt:{' '}
              <output aria-label="Gesamtpreis">
                {euroFormatter.format(totalCents / 100)}
              </output>
            </p>
          </div>

          <button
            type="button"
            disabled={selectedQuantity === 0}
            onClick={() => setQuantities({})}
          >
            Auswahl löschen
          </button>
        </aside>
      )}

      {loading && <p role="status">Speisekarte wird geladen…</p>}

      {error && (
        <div role="alert">
          <p>{error}</p>
          <button type="button" onClick={retryLoading}>
            Erneut versuchen
          </button>
        </div>
      )}

      {!loading && !error && items.length === 0 && (
        <p>Aktuell sind keine Produkte vorhanden.</p>
      )}

      {!loading &&
        !error &&
        categories.map((category) => {
          const categoryItems = items.filter(
            (item) => item.category === category,
          )

          const subcategories = [
            ...new Set(
              categoryItems.map((item) => item.subcategory ?? ''),
            ),
          ]

          return (
            <section
              className="menu-category"
              id={categoryId(category)}
              key={category}
            >
              <h2>{category}</h2>

              {subcategories.map((subcategory) => (
                <div className="menu-subcategory" key={subcategory}>
                  {subcategory && <h3>{subcategory}</h3>}

                  <div className="product-grid">
                    {categoryItems
                      .filter(
                        (item) =>
                          (item.subcategory ?? '') === subcategory,
                      )
                      .map((item) => (
                        <ProductCard
                          key={item.id}
                          name={item.name}
                          description={item.description}
                          price={euroFormatter.format(item.price)}
                          sizeVolume={item.sizeVolume}
                          notes={item.notes}
                          available={item.available}
                          quantity={quantities[item.id] ?? 0}
                          onQuantityChange={(quantity) =>
                            changeQuantity(item, quantity)
                          }
                        />
                      ))}
                  </div>
                </div>
              ))}
            </section>
          )
        })}
    </section>
  )
}

export default Menu