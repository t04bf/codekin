import { useEffect, useState } from 'react'
import { DateInput, type DateInputProps } from '@heroui/date-input'
import { HeroUIProvider } from '@heroui/system'
import { parseDate, type CalendarDate } from '@internationalized/date'
import { cn } from '@/lib/utils'

interface UnifiedDateInputProps {
  id: string
  label: string
  value: string
  onChange: (value: string) => void
  isRequired?: boolean
  isDisabled?: boolean
  className?: string
}

function parseValue(value: string): CalendarDate | null {
  if (!value) return null
  try {
    const normalized = value.replace(/[./]/g, '-')
    return parseDate(normalized.length === 7 ? `${normalized}-01` : normalized)
  } catch {
    return null
  }
}

// Adapted from Magic Resume's UnifiedDateInput: segmented year/month editing.
export function UnifiedDateInput({ id, label, value, onChange, isRequired, isDisabled, className }: UnifiedDateInputProps) {
  const [selectedDate, setSelectedDate] = useState(() => parseValue(value))
  useEffect(() => { setSelectedDate(parseValue(value)) }, [value])

  return <HeroUIProvider locale="ja-JP">
    <DateInput<CalendarDate>
      id={id}
      aria-label={label}
      value={isDisabled ? null : selectedDate}
      onChange={date => {
        setSelectedDate(date)
        // 年份逐位输入时也保持可解析格式，避免受控回填清空已填月份。
        onChange(date ? `${String(date.year).padStart(4, '0')}/${String(date.month).padStart(2, '0')}` : '')
      }}
      isRequired={isRequired}
      isDisabled={isDisabled}
      // HeroUI supports month segments at runtime, but its public type omits month.
      granularity={'month' as DateInputProps<CalendarDate>['granularity']}
      variant="bordered"
      labelPlacement="outside"
      shouldForceLeadingZeros
      disableAnimation
      className={cn('w-full', isDisabled && 'opacity-50', className)}
      classNames={{
        base: 'flex w-full flex-col',
        inputWrapper: 'h-10 min-h-10 rounded-md border border-input bg-background px-3 py-2 shadow-none focus-within:ring-2 focus-within:ring-ring focus-within:ring-offset-2',
        innerWrapper: 'inline-flex h-full w-full items-center',
        input: 'inline-flex h-full w-full items-center text-base text-foreground md:text-sm',
        segment: 'rounded-sm px-0.5 tabular-nums text-foreground outline-none focus:bg-primary focus:text-primary-foreground data-[placeholder=true]:text-muted-foreground',
      }}
    />
  </HeroUIProvider>
}
