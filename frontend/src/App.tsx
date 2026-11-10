import { Suspense, useEffect, useState } from 'react'
import { BrowserRouter, Routes, Route } from 'react-router-dom'
import Navbar from './components/Navbar/Navbar'
import Footer from './components/Footer/Footer'
import ScrollToTop from './components/ScrollToTop/ScrollToTop'
import { pages, preloadRoute, routes } from './routes'

// The page for the address the visitor arrived on starts loading as soon
// as this file runs, in parallel with the first render of the header.
const firstPage = preloadRoute(window.location.pathname).catch(() => {
  // If the chunk fails here, the route below tries again and reports it.
})

function RouteLoading() {
  return <div className="route-loading" aria-busy="true" />
}

function App() {
  const [firstPageReady, setFirstPageReady] = useState(false)

  useEffect(() => {
    let active = true

    firstPage.then(() => {
      if (active) setFirstPageReady(true)
    })

    return () => {
      active = false
    }
  }, [])

  // Once the first page is showing and the browser is idle, fetch the small
  // Speisekarte chunk so that "Bestellen" opens without a wait.
  useEffect(() => {
    if (!firstPageReady) return

    const timer = window.setTimeout(() => {
      pages.menu.preload().catch(() => {})
    }, 3000)

    return () => window.clearTimeout(timer)
  }, [firstPageReady])

  return (
    <BrowserRouter>
      <ScrollToTop />

      <a className="skip-link" href="#inhalt">
        Zum Inhalt springen
      </a>

      <Navbar />

      <main id="inhalt" tabIndex={-1}>
        {firstPageReady ? (
          <Suspense fallback={<RouteLoading />}>
            <Routes>
              {routes.map(({ path, Page }) => (
                <Route key={path} path={path} element={<Page />} />
              ))}
            </Routes>
          </Suspense>
        ) : (
          <RouteLoading />
        )}
      </main>

      <Footer />
    </BrowserRouter>
  )
}

export default App
