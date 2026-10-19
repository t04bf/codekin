import { Label } from '@/components/ui/label'
import type { ReactNode } from 'react'

export function FormField({ id, label, error, children }: { id: string; label: string; error?: string; children: ReactNode }) {
  return <div className="flex flex-col gap-3"><Label htmlFor={id}>{label}</Label>{children}
    {error && <p id={`${id}-error`} role="alert" className="text-sm text-destructive">{error}</p>}
  </div>
}
