import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import { setSessionEndedHandler, staffApi, type CurrentStaff } from './api'

type AuthState =
  | { status: 'loading' }
  | { status: 'signedOut'; expired: boolean }
  | { status: 'signedIn'; me: CurrentStaff }

type StaffAuth = AuthState & {
  signIn: (username: string, password: string) => Promise<CurrentStaff>
  signOut: () => Promise<void>
  /** After the backend returned a changed person (new password, …). */
  update: (me: CurrentStaff) => void
}

const StaffAuthContext = createContext<StaffAuth | null>(null)

/** Who is signed in to the staff area; checked once on load. */
export function StaffAuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ status: 'loading' })

  useEffect(() => {
    let active = true

    staffApi
      .me()
      .then((me) => {
        if (active) setState({ status: 'signedIn', me })
      })
      .catch(() => {
        if (active) setState({ status: 'signedOut', expired: false })
      })

    // Any later 401 means the session ended (timeout, deactivated, password
    // reset by an admin).
    setSessionEndedHandler(() => setState({ status: 'signedOut', expired: true }))

    return () => {
      active = false
      setSessionEndedHandler(() => {})
    }
  }, [])

  const signIn = useCallback(async (username: string, password: string) => {
    const me = await staffApi.signIn(username, password)
    setState({ status: 'signedIn', me })
    return me
  }, [])

  const signOut = useCallback(async () => {
    try {
      await staffApi.signOut()
    } finally {
      setState({ status: 'signedOut', expired: false })
    }
  }, [])

  const update = useCallback((me: CurrentStaff) => {
    setState({ status: 'signedIn', me })
  }, [])

  const value = useMemo(
    () => ({ ...state, signIn, signOut, update }),
    [state, signIn, signOut, update],
  )

  return <StaffAuthContext value={value}>{children}</StaffAuthContext>
}

// The hook belongs with its provider.
// eslint-disable-next-line react-refresh/only-export-components
export function useStaffAuth() {
  const auth = useContext(StaffAuthContext)
  if (!auth) throw new Error('useStaffAuth needs StaffAuthProvider')
  return auth
}
