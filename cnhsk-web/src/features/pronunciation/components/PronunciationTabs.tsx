import { Headphones, Mic } from 'lucide-react'
import { NavLink } from 'react-router-dom'

import { cn } from '@/shared/lib/cn'

const TABS = [
  { end: true, icon: Headphones, label: 'Nghe phân biệt âm', to: '/learn/pronunciation' },
  { end: false, icon: Mic, label: 'Luyện nói', to: '/learn/pronunciation/speaking' },
] as const

/** The two halves of pronunciation: telling sounds apart by ear, and saying them for a teacher to grade. */
export function PronunciationTabs() {
  return (
    <nav aria-label="Phần luyện phát âm" className="flex gap-1 border-b border-line">
      {TABS.map(({ end, icon: Icon, label, to }) => (
        <NavLink
          className={({ isActive }) =>
            cn(
              '-mb-px inline-flex min-h-touch items-center gap-2 border-b-2 px-4 text-body font-semibold no-underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus',
              isActive ? 'border-brand-600 text-brand-700' : 'border-transparent text-ink-secondary hover:text-ink',
            )
          }
          end={end}
          key={to}
          to={to}
        >
          <Icon aria-hidden="true" className="size-5" />
          {label}
        </NavLink>
      ))}
    </nav>
  )
}
