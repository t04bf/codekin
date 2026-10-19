import { useQuery } from '@tanstack/react-query'
import { userApi } from '@/api/private'
import { useAuthStore } from '@/features/auth/session'

export function useProfile() {
  const userId = useAuthStore(state => state.userId)
  return useQuery({ queryKey: ['me', userId], queryFn: ({ signal }) => userApi.getMe(signal), enabled: Boolean(userId) })
}
