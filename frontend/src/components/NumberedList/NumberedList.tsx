import { useId } from 'react'
import './NumberedList.css'

type NumberedListProps = {
  /** Heading for screen readers; the section has no visible headline. */
  label: string
  items: { title: string; text: string }[]
  /** True when the order matters (steps), false for a plain set of points. */
  ordered?: boolean
}

/** Three short points side by side, numbered 01, 02, 03. */
function NumberedList({ label, items, ordered = false }: NumberedListProps) {
  const headingId = useId()
  const List = ordered ? 'ol' : 'ul'

  return (
    <section className="container numbered" aria-labelledby={headingId}>
      <h2 id={headingId} className="visually-hidden">
        {label}
      </h2>

      {/* role="list" keeps the list semantics in Safari despite
          list-style: none. */}
      <List className="numbered-grid" role="list">
        {items.map((item, index) => (
          <li
            key={item.title}
            className="numbered-item"
            data-rise
            data-rise-delay={index}
          >
            <span className="numbered-number" aria-hidden="true">
              {String(index + 1).padStart(2, '0')}
            </span>
            <h3>{item.title}</h3>
            <p>{item.text}</p>
          </li>
        ))}
      </List>
    </section>
  )
}

export default NumberedList
