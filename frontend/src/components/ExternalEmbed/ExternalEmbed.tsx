import { useEffect, useRef, useState, type HTMLAttributeReferrerPolicy } from 'react'
import { Link } from 'react-router-dom'
import './ExternalEmbed.css'

type ExternalEmbedProps = {
  src: string
  /** Title of the iframe, read by screen readers. */
  title: string
  /** Tells the guest who receives data once the content is loaded. */
  note: string
  buttonLabel: string
  allow?: string
  allowFullScreen?: boolean
  referrerPolicy?: HTMLAttributeReferrerPolicy
}

/**
 * Video or map from another company (YouTube, Google Maps).
 *
 * Nothing is requested from that company until the guest presses the
 * button. Until then the frame shows a short note instead. The parent
 * decides the size of the frame.
 */
function ExternalEmbed({
  src,
  title,
  note,
  buttonLabel,
  allow,
  allowFullScreen,
  referrerPolicy,
}: ExternalEmbedProps) {
  const [loaded, setLoaded] = useState(false)
  const frameRef = useRef<HTMLIFrameElement>(null)

  // The button disappears on click; keyboard focus continues in the frame.
  useEffect(() => {
    if (loaded) frameRef.current?.focus()
  }, [loaded])

  return (
    <div className="external-embed">
      {loaded ? (
        <iframe
          ref={frameRef}
          src={src}
          title={title}
          allow={allow}
          allowFullScreen={allowFullScreen}
          referrerPolicy={referrerPolicy}
        />
      ) : (
        <div className="external-embed-consent">
          <p>
            {note} Mehr dazu unter <Link to="/datenschutz">Datenschutz</Link>.
          </p>
          <button
            type="button"
            className="btn btn--dark"
            onClick={() => setLoaded(true)}
          >
            {buttonLabel}
          </button>
        </div>
      )}
    </div>
  )
}

export default ExternalEmbed
