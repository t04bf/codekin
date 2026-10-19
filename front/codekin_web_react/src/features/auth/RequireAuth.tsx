import { useEffect, useState } from 'react'
import { Navigate, Outlet, useLocation } from 'react-router'
import { useAuthStore } from './session'
import { LoadingState } from '@/components/LoadingState'

export function RequireAuth() {
  const refreshToken = useAuthStore(state => state.refreshToken)
  const accessToken = useAuthStore(state => state.accessToken)
  const [checking, setChecking] = useState(true)
  const location = useLocation()
  useEffect(() => {
    let active = true
    setChecking(true)
    void useAuthStore.getState().ensureAccessToken().finally(() => { if (active) setChecking(false) })
    return () => { active = false }
  }, [refreshToken])
  if (checking) return <LoadingState/>
  if (!accessToken) return <Navigate to={`/login?redirect=${encodeURIComponent(location.pathname + location.search)}`} replace/>
  return <Outlet/>
}
