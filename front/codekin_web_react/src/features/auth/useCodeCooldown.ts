import { useEffect, useState } from 'react'

/** A closed dialog or changed recipient must not reset that recipient's send cooldown. */
export function useCodeCooldown(recipient: string) {
  const [deadlines, setDeadlines] = useState<Record<string, number>>({})
  const [now, setNow] = useState(Date.now)
  useEffect(() => {
    if (!Object.values(deadlines).some(value => value > Date.now())) return
    const timer = window.setInterval(() => {
      const time = Date.now()
      setNow(time)
      if (!Object.values(deadlines).some(value => value > time)) window.clearInterval(timer)
    }, 1000)
    return () => window.clearInterval(timer)
  }, [deadlines])
  return {
    remaining: Math.max(0, Math.ceil(((deadlines[recipient] || 0) - now) / 1000)),
    start(key: string) {
      const time = Date.now()
      setNow(time)
      setDeadlines(values => ({ ...values, [key]: time + 60_000 }))
    },
  }
}
