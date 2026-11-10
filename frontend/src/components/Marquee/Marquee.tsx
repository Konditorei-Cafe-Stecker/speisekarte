import './Marquee.css'

const words = [
  'Torten',
  'Kuchen',
  'Feines Gebäck',
  'Konditor-Eis aus eigener Herstellung',
  'Kaffee',
  'Seit 1908',
]

/** Decorative endless band. The same words appear elsewhere on the page,
 *  so it is hidden from screen readers. */
function Marquee() {
  return (
    <div className="marquee" aria-hidden="true">
      <div className="marquee-track">
        {[0, 1].map((copy) => (
          <span className="marquee-copy" key={copy}>
            {words.map((word) => (
              <span key={word}>
                {word} <em>·</em>{' '}
              </span>
            ))}
          </span>
        ))}
      </div>
    </div>
  )
}

export default Marquee
