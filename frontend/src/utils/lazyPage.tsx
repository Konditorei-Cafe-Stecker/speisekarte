import { lazy, useState } from 'react'
import type { ComponentType } from 'react'

type Loader = () => Promise<{ default: ComponentType }>

export type LazyPage = ComponentType & { preload: () => Promise<void> }

/**
 * React.lazy with one addition: preload() fetches the page's chunk ahead of
 * time, and a page that is already loaded renders immediately.
 *
 * Why this matters: when a lazy page has to show a loading state first,
 * React waits about 300 ms before it swaps in the real content. Preloading
 * the page for the address the visitor arrived on avoids that wait.
 */
export function lazyPage(load: Loader): LazyPage {
  let Loaded: ComponentType | null = null
  const Lazy = lazy(load)

  function Page() {
    // Decided once per visit to the page, so a preload that finishes later
    // never swaps the component underneath a page that is already showing.
    const [Component] = useState<ComponentType>(() => Loaded ?? Lazy)

    return <Component />
  }

  Page.preload = () =>
    load().then((module) => {
      Loaded = module.default
    })

  return Page
}
