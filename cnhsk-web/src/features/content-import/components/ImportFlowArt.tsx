import { FileSpreadsheet, ListChecks, ShieldCheck } from 'lucide-react'
import { useId } from 'react'
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

export function ImportFlowArt() {
  const sceneId = useId()
  return (
    <svg
      aria-hidden="true"
      className="pointer-events-none absolute inset-0 size-full select-none text-brand-600"
      fill="none"
      preserveAspectRatio="none"
      viewBox="0 0 900 600"
    >
      <defs>
        <linearGradient id={`${sceneId}-hills`} x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="currentColor" stopOpacity="0" />
          <stop offset="100%" stopColor="currentColor" stopOpacity="0.09" />
        </linearGradient>
      </defs>
      <circle cx="800" cy="52" r="28" fill="currentColor" opacity="0.04" />
      <path
        d="M0 600V558L110 532L230 570L360 510L500 558L640 484L760 552L900 510V600Z"
        fill={`url(#${sceneId}-hills)`}
      />
      <path
        d="M0 600C150 554 250 608 450 570C650 532 750 592 900 560V600Z"
        fill="currentColor"
        opacity="0.04"
      />
      <g stroke="currentColor" strokeWidth="2" opacity="0.08">
        <path d="M760 106V26L800 66H846V106ZM760 26L800 66M774 82H828M774 94H816" />
        <path d="M720 118H834" strokeDasharray="4 8" />
      </g>
    </svg>
  )
}
