import { useEffect } from 'react'
import { Navigate, Outlet, Route, Routes, useLocation } from 'react-router-dom'
import './Staff.css'
import { StaffAuthProvider, useStaffAuth } from './StaffAuth'
import StaffLayout from './StaffLayout'
import StaffHome from './pages/StaffHome'
import StaffLogin from './pages/StaffLogin'
import StaffOrder from './pages/StaffOrder'
import StaffPassword from './pages/StaffPassword'
import StaffPlanning from './pages/StaffPlanning'
import StaffTeam from './pages/StaffTeam'

/**
 * The staff area under /personal: its own chunk and its own layout, without
 * the public header and footer. Search engines are asked not to index it.
 */
function StaffArea() {
  useEffect(() => {
    const robots = document.createElement('meta')
    robots.name = 'robots'
    robots.content = 'noindex, nofollow'
    document.head.append(robots)

    return () => robots.remove()
  }, [])

  return (
    <StaffAuthProvider>
      <Routes>
        <Route path="login" element={<StaffLogin />} />
        <Route element={<RequireSignedIn />}>
          <Route element={<StaffLayout />}>
            <Route index element={<StaffHome />} />
            <Route path="bestellung/:reference" element={<StaffOrder />} />
            <Route path="passwort" element={<StaffPassword />} />
            <Route element={<RequireAdmin />}>
              <Route path="team" element={<StaffTeam />} />
              <Route path="planung" element={<StaffPlanning />} />
            </Route>
          </Route>
        </Route>
        <Route path="*" element={<Navigate to="/personal" replace />} />
      </Routes>
    </StaffAuthProvider>
  )
}

/** Signed out: to the sign-in page. Temporary password: choose one first. */
function RequireSignedIn() {
  const auth = useStaffAuth()
  const location = useLocation()

  if (auth.status === 'loading') {
    return (
      <p className="staff-loading" role="status">
        Wird geladen …
      </p>
    )
  }

  if (auth.status === 'signedOut') {
    return (
      <Navigate
        to="/personal/login"
        replace
        state={{ from: location.pathname, expired: auth.expired }}
      />
    )
  }

  if (auth.me.mustChangePassword && location.pathname !== '/personal/passwort') {
    return <Navigate to="/personal/passwort" replace />
  }

  return <Outlet />
}

function RequireAdmin() {
  const auth = useStaffAuth()

  if (auth.status !== 'signedIn' || auth.me.role !== 'ADMIN') {
    return <Navigate to="/personal" replace />
  }

  return <Outlet />
}

export default StaffArea
