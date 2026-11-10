import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useLocation } from 'react-router-dom'
import './Menu.css'
import MenuRow from '../components/MenuRow/MenuRow'
import OrderBar from '../components/OrderBar/OrderBar'
import type { OrderLine } from '../components/OrderBar/OrderBar'
import { fetchMenuItems } from '../api/menu'
import type { MenuItem } from '../api/menu'
import { createOrder } from '../api/orders'
import type { OrderResponse } from '../api/orders'
import { formatCents, toCents } from '../utils/format'
import { readSession, removeSession, writeSession } from '../utils/sessionStore'

const QUANTITIES_KEY = 'stecker.menu.quantities'
const LAST_ORDER_KEY = 'stecker.menu.lastOrder'

type Quantities = Record<number, number>

type PreparedItem = {
  item: MenuItem
  cents: number
  /** Name plus size, so variants with the same name can be told apart. */
  label: string
  noteLines: string[]
  /** Lower-case text the search runs against. */
  searchText: string
}

type MenuGroup = {
  id: string
  title: string
  category: string
  /** Note lines every item in the group has; shown once under the heading. */
  sharedNotes: string[]
  items: PreparedItem[]
}

function categoryId(category: string) {
  return category
    .toLowerCase()
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/[^a-z0-9]+/g, '-')
}

/** Lower-case and without accents, so "kannchen" also finds "Kännchen". */
function normalizeText(text: string) {
  return text
    .toLocaleLowerCase('de-DE')
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
}

/**
 * Splits the notes field into lines. Lines that only repeat the footnote
 * legend ("Fußnoten: …") are dropped because the legend is under the list.
 */
function splitNotes(notes: string | null) {
  if (!notes) return []

  return notes
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line !== '' && !/^fußnoten:/i.test(line))
}

function buildGroups(items: MenuItem[]): MenuGroup[] {
  const groups = new Map<string, MenuGroup>()

  for (const item of items) {
    const key = `${item.category}\u0000${item.subcategory ?? ''}`
    let group = groups.get(key)

    if (!group) {
      group = {
        id: `gruppe-${groups.size + 1}`,
        title: item.subcategory ?? item.category,
        category: item.category,
        sharedNotes: [],
        items: [],
      }
      groups.set(key, group)
    }

    group.items.push({
      item,
      cents: toCents(item.price),
      label: [item.name, item.sizeVolume].filter(Boolean).join(', '),
      noteLines: splitNotes(item.notes),
      searchText: normalizeText(
        [item.name, item.description, item.subcategory]
          .filter(Boolean)
          .join(' '),
      ),
    })
  }

  // Keep the categories together in the order they first appear.
  const categoryOrder = [...new Set(items.map((item) => item.category))]
  const ordered = [...groups.values()].sort(
    (a, b) =>
      categoryOrder.indexOf(a.category) - categoryOrder.indexOf(b.category),
  )

  for (const group of ordered) {
    if (group.items.length < 2) continue

    group.sharedNotes = group.items[0].noteLines.filter((line) =>
      group.items.every((entry) => entry.noteLines.includes(line)),
    )
  }

  return ordered
}

function parseQuantities(value: unknown): Quantities | null {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    return null
  }

  const quantities: Quantities = {}

  for (const [key, quantity] of Object.entries(value)) {
    const id = Number(key)

    if (
      Number.isInteger(id) &&
      typeof quantity === 'number' &&
      Number.isInteger(quantity) &&
      quantity > 0
    ) {
      quantities[id] = quantity
    }
  }

  return quantities
}

function parseOrder(value: unknown): OrderResponse | null {
  if (typeof value !== 'object' || value === null) return null

  const order = value as Partial<OrderResponse>

  if (
    typeof order.orderReference !== 'string' ||
    typeof order.totalPrice !== 'number' ||
    !Array.isArray(order.items)
  ) {
    return null
  }

  return order as OrderResponse
}

function countSelected(items: MenuItem[], quantities: Quantities) {
  return items.reduce((total, item) => total + (quantities[item.id] ?? 0), 0)
}

function MenuSkeleton() {
  return (
    <div className="menu-skeleton" aria-hidden="true">
      {[0, 1].map((group) => (
        <div className="menu-skeleton-group" key={group}>
          <span className="menu-skeleton-bar menu-skeleton-heading" />
          {[0, 1, 2, 3].map((row) => (
            <div className="menu-skeleton-row" key={row}>
              <div>
                <span className="menu-skeleton-bar menu-skeleton-name" />
                <span className="menu-skeleton-bar menu-skeleton-meta" />
              </div>
              <span className="menu-skeleton-circle" />
            </div>
          ))}
        </div>
      ))}
    </div>
  )
}

function Menu() {
  const { hash } = useLocation()
  const [items, setItems] = useState<MenuItem[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [attempt, setAttempt] = useState(0)
  const [query, setQuery] = useState('')
  // undefined: nothing picked yet (a hash such as /menu#speisen may decide),
  // null: "Alle", otherwise the chosen category name.
  const [pickedCategory, setPickedCategory] = useState<
    string | null | undefined
  >(undefined)
  const [quantities, setQuantities] = useState<Quantities>(
    () => readSession(QUANTITIES_KEY, parseQuantities) ?? {},
  )
  const [sheetOpen, setSheetOpen] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [orderError, setOrderError] = useState<string | null>(null)
  const [completedOrder, setCompletedOrder] = useState<OrderResponse | null>(
    () => readSession(LAST_ORDER_KEY, parseOrder),
  )
  const headingRef = useRef<HTMLHeadingElement>(null)
  const toolbarRef = useRef<HTMLDivElement>(null)
  const listRef = useRef<HTMLDivElement>(null)
  const filterChanged = useRef(false)

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

  // A link such as /menu#speisen selects a category instead of an anchor,
  // so the page still has to start at the top.
  useEffect(() => {
    if (window.location.hash) {
      window.scrollTo({ top: 0, left: 0, behavior: 'instant' })
    }
  }, [])

  // Selection and last order survive a reload within the same tab.
  useEffect(() => {
    writeSession(QUANTITIES_KEY, quantities)
  }, [quantities])

  useEffect(() => {
    if (completedOrder) {
      writeSession(LAST_ORDER_KEY, completedOrder)
    } else {
      removeSession(LAST_ORDER_KEY)
    }
  }, [completedOrder])

  const groups = useMemo(() => buildGroups(items), [items])
  const categories = useMemo(
    () => [...new Set(items.map((item) => item.category))],
    [items],
  )

  let hashId = ''
  try {
    hashId = decodeURIComponent(hash.slice(1))
  } catch {
    // A malformed hash simply selects nothing.
  }

  const hashCategory =
    categories.find((category) => categoryId(category) === hashId) ?? null
  const activeCategory =
    pickedCategory === undefined ? hashCategory : pickedCategory

  const searchTerms = normalizeText(query).split(/\s+/).filter(Boolean)
  const visibleGroups = groups
    .filter(
      (group) => activeCategory === null || group.category === activeCategory,
    )
    .map((group) => ({
      ...group,
      items: group.items.filter((entry) =>
        searchTerms.every((term) => entry.searchText.includes(term)),
      ),
    }))
    .filter((group) => group.items.length > 0)

  const visibleCount = visibleGroups.reduce(
    (total, group) => total + group.items.length,
    0,
  )

  // When the filter changes while the reader is deep in the list, bring the
  // start of the results back up under the sticky toolbar.
  useEffect(() => {
    if (!filterChanged.current) return
    filterChanged.current = false

    const list = listRef.current
    const toolbar = toolbarRef.current
    if (!list || !toolbar) return

    const offset =
      list.getBoundingClientRect().top - toolbar.getBoundingClientRect().bottom

    if (offset < 0) {
      window.scrollBy({ top: offset, left: 0, behavior: 'instant' })
    }
  }, [query, activeCategory])

  const orderLines: OrderLine[] = groups.flatMap((group) =>
    group.items
      .filter((entry) => (quantities[entry.item.id] ?? 0) > 0)
      .map((entry) => ({
        id: entry.item.id,
        label: entry.label,
        quantity: quantities[entry.item.id],
        totalCents: entry.cents * quantities[entry.item.id],
      })),
  )

  const selectedCount = countSelected(items, quantities)
  const totalCents = orderLines.reduce(
    (total, line) => total + line.totalCents,
    0,
  )

  function retryLoading() {
    setError(null)
    setLoading(true)
    setAttempt((previous) => previous + 1)
  }

  function pickCategory(category: string | null) {
    filterChanged.current = true
    setPickedCategory(category)
  }

  function changeQuery(value: string) {
    filterChanged.current = true
    setQuery(value)
  }

  function changeQuantity(item: MenuItem, delta: number) {
    const current = quantities[item.id] ?? 0

    if (!item.available && delta > 0) return

    const next = { ...quantities }
    const quantity = Math.max(0, current + delta)

    if (quantity === 0) {
      delete next[item.id]
    } else {
      next[item.id] = quantity
    }

    setQuantities(next)
    setOrderError(null)

    // The bar disappears with the last item; do not reopen the sheet later.
    if (countSelected(items, next) === 0 && !completedOrder) {
      setSheetOpen(false)
    }
  }

  function clearSelection() {
    setQuantities({})
    setOrderError(null)
    setSheetOpen(false)
    headingRef.current?.focus({ preventScroll: true })
  }

  function dismissOrder() {
    setCompletedOrder(null)
    setSheetOpen(false)
    headingRef.current?.focus({ preventScroll: true })
  }

  const closeSheet = useCallback(() => setSheetOpen(false), [])

  async function submitOrder() {
    if (orderLines.length === 0 || submitting) return

    setSubmitting(true)
    setOrderError(null)

    try {
      const order = await createOrder({
        items: orderLines.map((line) => ({
          menuItemId: line.id,
          quantity: line.quantity,
        })),
      })

      setCompletedOrder(order)
      setQuantities({})
      setSheetOpen(true)
    } catch (requestError) {
      // The raw server text is for developers, not for guests.
      console.error('Bestellung fehlgeschlagen:', requestError)
      setOrderError(
        'Die Bestellung konnte nicht gesendet werden. Bitte versuch es noch einmal oder gib der Bedienung Bescheid.',
      )
    } finally {
      setSubmitting(false)
    }
  }

  const ready = !loading && !error
  const filtering = searchTerms.length > 0 || activeCategory !== null

  return (
    <section className="menu" aria-busy={loading}>
      <header className="menu-header">
        <p className="eyebrow">Frisch aus unserer Theke</p>
        <h1 ref={headingRef} tabIndex={-1}>
          Speisekarte
        </h1>
        <p className="menu-description">
          Auswählen, Bestellung prüfen und der Bedienung zeigen.
        </p>
      </header>

      {!error && (loading || items.length > 0) && (
        <div className="menu-toolbar" ref={toolbarRef}>
          <div className="menu-search">
            <label htmlFor="menu-suche" className="visually-hidden">
              Speisekarte durchsuchen
            </label>
            <svg
              aria-hidden="true"
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
            >
              <circle cx="11" cy="11" r="7" />
              <path d="M20 20l-3.5-3.5" />
            </svg>
            <input
              id="menu-suche"
              type="search"
              placeholder="Suchen, z. B. Cappuccino"
              autoComplete="off"
              enterKeyHint="search"
              value={query}
              onChange={(event) => changeQuery(event.target.value)}
            />
          </div>

          <div className="menu-categories" role="group" aria-label="Kategorien">
            <button
              type="button"
              aria-pressed={activeCategory === null}
              onClick={() => pickCategory(null)}
            >
              Alle
            </button>
            {categories.map((category) => (
              <button
                type="button"
                key={category}
                aria-pressed={activeCategory === category}
                onClick={() => pickCategory(category)}
              >
                {category}
              </button>
            ))}
          </div>
        </div>
      )}

      <p className="visually-hidden" role="status">
        {loading && 'Speisekarte wird geladen…'}
        {ready && filtering && `${visibleCount} Treffer`}
      </p>

      {loading && <MenuSkeleton />}

      {error && (
        <div className="menu-message" role="alert">
          <p>{error}</p>
          <button type="button" className="menu-button" onClick={retryLoading}>
            Erneut versuchen
          </button>
        </div>
      )}

      {ready && items.length === 0 && (
        <p className="menu-message">Aktuell sind keine Produkte vorhanden.</p>
      )}

      {ready && items.length > 0 && visibleCount === 0 && (
        <div className="menu-message">
          <p>Nichts gefunden. Versuch es mit einem anderen Begriff.</p>
          <button
            type="button"
            className="menu-button"
            onClick={() => {
              changeQuery('')
              pickCategory(null)
            }}
          >
            Alles anzeigen
          </button>
        </div>
      )}

      <div ref={listRef}>
        {ready &&
          visibleGroups.map((group) => (
            <section
              className="menu-group"
              key={group.id}
              aria-labelledby={group.id}
            >
              <div className="menu-group-head">
                <h2 id={group.id}>{group.title}</h2>
                {group.title !== group.category && (
                  <span className="menu-group-category">{group.category}</span>
                )}
              </div>

              {group.sharedNotes.length > 0 && (
                <p className="menu-group-note">
                  {group.sharedNotes.join(' · ')}
                </p>
              )}

              <ul className="menu-rows">
                {group.items.map((entry) => (
                  <MenuRow
                    key={entry.item.id}
                    name={entry.item.name}
                    price={formatCents(entry.cents)}
                    label={entry.label}
                    details={[
                      entry.item.sizeVolume,
                      entry.item.description,
                      ...entry.noteLines.filter(
                        (line) => !group.sharedNotes.includes(line),
                      ),
                    ].filter((detail): detail is string => Boolean(detail))}
                    available={entry.item.available}
                    quantity={quantities[entry.item.id] ?? 0}
                    onAdd={() => changeQuantity(entry.item, 1)}
                    onRemove={() => changeQuantity(entry.item, -1)}
                  />
                ))}
              </ul>
            </section>
          ))}
      </div>

      {ready && items.length > 0 && (
        <p className="menu-legend">
          ¹ Konservierungsstoff · ² Farbstoff · ³ Antioxidationsmittel · ⁴
          kakaohaltige Fettglasur · ⁵ saisonabhängig
        </p>
      )}

      {ready && (
        <OrderBar
          lines={orderLines}
          count={selectedCount}
          totalCents={totalCents}
          open={sheetOpen}
          submitting={submitting}
          error={orderError}
          completedOrder={completedOrder}
          onToggle={() => setSheetOpen((open) => !open)}
          onClose={closeSheet}
          onClear={clearSelection}
          onSubmit={submitOrder}
          onDismissOrder={dismissOrder}
        />
      )}
    </section>
  )
}

export default Menu
