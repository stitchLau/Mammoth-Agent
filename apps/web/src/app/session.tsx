import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { currentUser, ensureCsrf, logout, type SessionUser } from '../lib/api'

type SessionState =
  | { status: 'loading' }
  | { status: 'anonymous' }
  | { status: 'authenticated'; user: SessionUser }

interface SessionContextValue {
  state: SessionState
  signIn: (user: SessionUser) => void
  signOut: () => Promise<void>
}

const SessionContext = createContext<SessionContextValue | null>(null)

export function SessionProvider(props: { children: React.ReactNode }) {
  const [state, setState] = useState<SessionState>({ status: 'loading' })

  useEffect(() => {
    void (async () => {
      try {
        await ensureCsrf()
        const user = await currentUser()
        setState({ status: 'authenticated', user })
      } catch {
        setState({ status: 'anonymous' })
      }
    })()
  }, [])

  const signIn = useCallback((user: SessionUser) => {
    setState({ status: 'authenticated', user })
  }, [])

  const signOut = useCallback(async () => {
    try {
      await logout()
    } finally {
      setState({ status: 'anonymous' })
    }
  }, [])

  const value = useMemo(() => ({ state, signIn, signOut }), [state, signIn, signOut])
  return <SessionContext.Provider value={value}>{props.children}</SessionContext.Provider>
}

export function useSession(): SessionContextValue {
  const value = useContext(SessionContext)
  if (!value) {
    throw new Error('useSession must be used inside SessionProvider')
  }
  return value
}
