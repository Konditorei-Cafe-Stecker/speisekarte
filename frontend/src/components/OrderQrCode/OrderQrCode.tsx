import { useEffect, useState } from 'react'

type OrderQrCodeProps = {
  /** What the code contains (the waiter's address for this order). */
  value: string
  /** Read out by screen readers. */
  label: string
}

type Matrix = boolean[][]

/**
 * A QR code drawn as one SVG path. The QR library is only downloaded when
 * a guest has actually sent an order, so the Speisekarte stays small.
 */
function OrderQrCode({ value, label }: OrderQrCodeProps) {
  const [result, setResult] = useState<{ value: string; matrix: Matrix } | null>(null)

  useEffect(() => {
    let active = true

    import('uqr')
      .then(({ encode }) => {
        // Medium error correction: still scans from a scratched or dim screen.
        const { data } = encode(value, { ecc: 'M', border: 0 })
        if (active) setResult({ value, matrix: data })
      })
      .catch(() => {
        // Without the code the guest still has the short code to read out.
      })

    return () => {
      active = false
    }
  }, [value])

  const matrix = result?.value === value ? result.matrix : null
  if (!matrix) {
    return <div className="order-qr order-qr--loading" aria-hidden="true" />
  }

  const size = matrix.length
  const quiet = 4 // white margin scanners need, in modules
  let path = ''

  matrix.forEach((row, y) => {
    row.forEach((dark, x) => {
      if (dark) path += `M${x + quiet} ${y + quiet}h1v1h-1z`
    })
  })

  return (
    <svg
      className="order-qr"
      role="img"
      aria-label={label}
      viewBox={`0 0 ${size + quiet * 2} ${size + quiet * 2}`}
      shapeRendering="crispEdges"
    >
      <rect width="100%" height="100%" fill="#fff" />
      <path d={path} fill="#1c1512" />
    </svg>
  )
}

export default OrderQrCode
