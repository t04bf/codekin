import { forwardRef, useState, type ComponentPropsWithoutRef } from 'react'
import { Eye, EyeOff } from 'lucide-react'
import { Input } from './ui/input'
import { Button } from './ui/button'
import { cn } from '@/lib/utils'
export const PasswordInput = forwardRef<HTMLInputElement, ComponentPropsWithoutRef<typeof Input>>(({ className, disabled, ...props }, ref) => {
  const [visible, setVisible] = useState(false)
  return <div className="relative"><Input {...props} ref={ref} type={visible ? 'text' : 'password'} disabled={disabled} className={cn('pr-12', className)}/><Button type="button" variant="ghost" size="icon" disabled={disabled} className="absolute right-0 top-0" aria-label={visible ? '隐藏密码' : '显示密码'} onClick={() => setVisible(value => !value)}>{visible ? <EyeOff className="h-4 w-4"/> : <Eye className="h-4 w-4"/>}</Button></div>
})
PasswordInput.displayName = 'PasswordInput'
