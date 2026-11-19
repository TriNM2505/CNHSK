import { FileSpreadsheet, ListChecks, ShieldCheck } from 'lucide-react'
import { cn } from '@/shared/lib/cn'

export function ImportFlowSteps({ step }: { step: 1 | 2 | 3 }) {
  const stages = [
    { label: 'Chọn file CSV', Icon: FileSpreadsheet },
    { label: 'Kiểm tra dữ liệu', Icon: ListChecks },
    { label: 'Xác nhận nhập', Icon: ShieldCheck },
  ]
  return (
    <ol
      aria-label="Các bước nhập dữ liệu"
      className="grid gap-2 rounded-md border border-brand-100 bg-surface p-3 sm:grid-cols-3"
    >
      {stages.map(({ label, Icon }, index) => (
        <li
          key={label}
          aria-current={index + 1 === step ? 'step' : undefined}
          className={cn(
            'flex min-w-0 items-center gap-3 rounded-sm px-3 py-2 text-body-sm',
            index + 1 === step ? 'bg-brand-50 text-brand-700' : 'text-ink-secondary',
          )}
        >
          <span
            className={cn(
              'flex size-8 shrink-0 items-center justify-center rounded-pill text-label',
              index + 1 === step ? 'bg-brand-600 text-surface' : 'bg-canvas text-ink-secondary',
            )}
          >
            {index + 1}
          </span>
          <span className="min-w-0 flex-1">{label}</span>
          <Icon aria-hidden="true" className="size-4 shrink-0" />
        </li>
      ))}
    </ol>
  )
}
