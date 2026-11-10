import { useEffect, useState } from 'react'

type DeferredPictureProps = {
  /** AVIF files with widths, for example "a-480.avif 480w, a-784.avif 784w". */
  avif: string
  sizes: string
  /** JPEG for browsers without AVIF support. */
  fallback: string
  alt: string
  width: number
  height: number
  /** False for photos that sit beside the screen (sideways gallery),
   *  where the browser's own lazy loading would fetch them too late. */
  lazy?: boolean
}

/**
 * A photo below the first screen. It starts downloading only after the page
 * itself has finished loading and the browser is idle, so it never competes
 * with the hero photo, fonts and scripts on a slow connection.
 *
 * The frame around it already has its final size, so nothing moves when
 * the photo appears.
 */
function DeferredPicture({
  avif,
  sizes,
  fallback,
  alt,
  width,
  height,
  lazy = true,
}: DeferredPictureProps) {
  const [ready, setReady] = useState(false)

  useEffect(() => {
    let idle = 0
    let timer = 0

    function start() {
      // requestIdleCallback is missing in Safari; a short timer does the job.
      if (typeof window.requestIdleCallback === 'function') {
        idle = window.requestIdleCallback(() => setReady(true), {
          timeout: 1500,
        })
      } else {
        timer = window.setTimeout(() => setReady(true), 200)
      }
    }

    if (document.readyState === 'complete') {
      start()
    } else {
      window.addEventListener('load', start, { once: true })
    }

    return () => {
      window.removeEventListener('load', start)
      if (idle) window.cancelIdleCallback(idle)
      if (timer) window.clearTimeout(timer)
    }
  }, [])

  return (
    <picture>
      {ready && <source type="image/avif" srcSet={avif} sizes={sizes} />}
      <img
        src={ready ? fallback : undefined}
        alt={alt}
        width={width}
        height={height}
        loading={lazy ? 'lazy' : undefined}
      />
    </picture>
  )
}

export default DeferredPicture
