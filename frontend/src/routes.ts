import { lazyPage } from './utils/lazyPage'

// Each page is its own chunk. The animation library is only part of the
// Startseite chunk, so guests opening /menu never download it.
export const pages = {
  home: lazyPage(() => import('./pages/Home')),
  menu: lazyPage(() => import('./pages/Menu')),
  tortenservice: lazyPage(() => import('./pages/Tortenservice')),
  about: lazyPage(() => import('./pages/About')),
  impressum: lazyPage(() => import('./pages/Impressum')),
  datenschutz: lazyPage(() => import('./pages/Datenschutz')),
}

export const routes = [
  { path: '/', Page: pages.home },
  { path: '/menu', Page: pages.menu },
  { path: '/tortenservice', Page: pages.tortenservice },
  { path: '/about', Page: pages.about },
  { path: '/impressum', Page: pages.impressum },
  { path: '/datenschutz', Page: pages.datenschutz },
]

/** Starts loading the page that belongs to an address, if there is one. */
export function preloadRoute(pathname: string) {
  const route = routes.find((entry) => entry.path === pathname)

  return route ? route.Page.preload() : Promise.resolve()
}
