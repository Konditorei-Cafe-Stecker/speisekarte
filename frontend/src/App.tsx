import { Suspense, useEffect, useState } from 'react'
import { BrowserRouter, Routes, Route } from 'react-router-dom'
import Navbar from './components/Navbar/Navbar'
import Footer from './components/Footer/Footer'
import ScrollToTop from './components/ScrollToTop/ScrollToTop'
import { pages, preloadRoute, routes, staffArea as StaffArea } from './routes'

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

      <Routes>
        {/* The staff area brings its own layout and sign-in. */}
        <Route
          path="/personal/*"
          element={firstPageReady ? <StaffArea /> : <RouteLoading />}
        />
        <Route path="*" element={<PublicSite ready={firstPageReady} />} />
      </Routes>
    </BrowserRouter>
  )
}

/** Header, page and footer of the public website. */
function PublicSite({ ready }: { ready: boolean }) {
  return (
    <>
      <a className="skip-link" href="#inhalt">
        Zum Inhalt springen
      </a>

      <Navbar />

      <main id="inhalt" tabIndex={-1}>
        {ready ? (
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
    </>
  )
}

export default App
