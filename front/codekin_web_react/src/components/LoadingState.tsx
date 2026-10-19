import { motion, useReducedMotion } from 'framer-motion'
import brandMark from '@/assets/brand-mark.png'
import { cn } from '@/lib/utils'

export function LoadingState({ variant = 'page', className }: { variant?: 'page' | 'section' | 'inline'; className?: string }) {
  const reduceMotion = useReducedMotion()
  const inline = variant === 'inline'
  return <motion.div
    role="status"
    aria-label="加载中"
    aria-busy="true"
    initial={{ opacity: reduceMotion ? 1 : 0 }}
    animate={{ opacity: 1 }}
    transition={{ duration: 0.2 }}
    className={cn('flex items-center justify-center', variant === 'page' ? 'min-h-dvh w-full bg-background' : variant === 'section' ? 'min-h-48 w-full' : 'inline-flex py-2', className)}
  >
    <div aria-hidden="true" className={cn('relative flex shrink-0 items-center justify-center', inline ? 'size-5' : variant === 'page' ? 'size-24' : 'size-16')}>
      <motion.div
        className="absolute inset-0 rounded-full border-2 border-primary/10 border-t-primary border-r-primary/40"
        animate={reduceMotion ? undefined : { rotate: 360 }}
        transition={{ duration: 1.2, ease: 'linear', repeat: Infinity }}
      />
      {!inline && <motion.img
        src={brandMark}
        alt=""
        className={variant === 'page' ? 'size-14 object-contain' : 'size-9 object-contain'}
        animate={reduceMotion ? undefined : { scale: [1, 1.06, 1] }}
        transition={{ duration: 1.8, ease: 'easeInOut', repeat: Infinity }}
      />}
    </div>
  </motion.div>
}
