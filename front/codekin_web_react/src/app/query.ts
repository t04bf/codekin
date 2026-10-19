import { QueryCache, QueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { errorMessage } from '@/lib/errors'
import { useAuthStore } from '@/features/auth/session'

export const queryClient = new QueryClient({
  queryCache: new QueryCache({ onError: error => toast.error(errorMessage(error, '数据加载失败，请重试'), { id: 'query-error' }) }),
  defaultOptions: { queries: { retry: false, staleTime: 30_000, refetchOnWindowFocus: false }, mutations: { retry: false } },
})
// Account boundaries discard private cached data and cancel its requests.
useAuthStore.subscribe((state, previous) => {
  if (state.userId !== previous.userId) queryClient.clear()
})
