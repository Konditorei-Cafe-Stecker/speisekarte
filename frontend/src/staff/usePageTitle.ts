import { useEffect } from 'react'

/** Sets the browser tab title while a staff page is shown. */
export function usePageTitle(title: string) {
  useEffect(() => {
    const previous = document.title
    document.title = `${title} · Personal · Café Stecker`

    return () => {
      document.title = previous
    }
  }, [title])
}
