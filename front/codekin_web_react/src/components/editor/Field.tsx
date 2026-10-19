import { useId } from 'react'
import { FormField } from '@/components/FormField'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Switch } from '@/components/ui/switch'
import RichTextEditor from '@/components/shared/rich-editor/RichEditor'
import { UnifiedDateInput } from '@/components/ui/unified-date-input'

interface FieldProps {
  label?: string; value?: string; onChange: (value: string) => void;
  type?: 'text' | 'textarea' | 'date' | 'editor' | 'date-range';
  placeholder?: string; required?: boolean; className?: string; showPresentSwitch?: boolean;
}

const monthValue = (value: string) => value.match(/^(\d{4})[./-](\d{2})/)?.slice(1).join('-') || ''

export default function Field({ label, value = '', onChange, type = 'text', placeholder, required, className, showPresentSwitch }: FieldProps) {
  const id = useId()
  const [start = '', end = ''] = value.split(/\s+-\s*/)
  const present = value === '至今' || end === '至今'
  const error = type === 'date-range' && monthValue(start) && monthValue(end) && monthValue(end) < monthValue(start) ? '结束时间不能早于开始时间' : undefined
  return <FormField id={id} label={label || placeholder || '内容'} error={error}>
    {type === 'editor' ? <RichTextEditor inputId={id} label={label || '内容'} content={value} onChange={onChange} placeholder={placeholder}/> :
      type === 'textarea' ? <Textarea id={id} value={value} onChange={e => onChange(e.target.value)} placeholder={placeholder} required={required} className={className}/> :
      type === 'date-range' ? <div className="space-y-3">
        <div className="grid grid-cols-2 gap-3">
          <UnifiedDateInput id={id} label={`${label || '日期'}开始时间`} value={start}
            onChange={date => onChange(`${date} - ${end}`)} isRequired={required} className={className}/>
          <UnifiedDateInput id={`${id}-end`} label={`${label || '日期'}结束时间`} value={end} isDisabled={present}
            onChange={date => onChange(`${start} - ${date}`)} isRequired={required} className={className}/>
        </div>
        {showPresentSwitch && <label className="flex items-center gap-2 text-sm"><Switch checked={present} onCheckedChange={checked => onChange(`${start} - ${checked ? '至今' : ''}`)}/>至今</label>}
      </div> :
      type === 'date' ? <div className="space-y-3"><UnifiedDateInput id={id} label={label || '日期'} isDisabled={present} value={value} onChange={onChange} isRequired={required} className={className}/>{showPresentSwitch && <label className="flex items-center gap-2 text-sm"><Switch checked={present} onCheckedChange={checked => onChange(checked ? '至今' : '')}/>至今</label>}</div> :
      <Input id={id} value={value} onChange={e => onChange(e.target.value)} placeholder={placeholder} required={required} className={className}/>}
  </FormField>
}
