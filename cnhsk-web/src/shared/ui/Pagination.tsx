import { ChevronLeft, ChevronRight } from 'lucide-react'

import { Button } from '@/shared/ui/Button'

export interface PaginationProps {
  label?: string
  onChange: (page: number) => void
  page: number
  size: number
  total: number
}

export function Pagination({ label = 'Phân trang', onChange, page, size, total }: PaginationProps) {
  const totalPages = Math.max(1, Math.ceil(total / Math.max(size, 1)))
  const currentPage = Math.min(Math.max(page, 1), totalPages)

  return (
    <nav aria-label={label} className="flex flex-wrap items-center justify-center gap-3">
      <Button
        disabled={currentPage === 1}
        onClick={() => onChange(currentPage - 1)}
        size="sm"
        variant="outline"
      >
        <ChevronLeft aria-hidden="true" className="size-4" />
        Trước
      </Button>
      <span aria-live="polite" className="text-body-sm tabular-nums text-ink-secondary">
        Trang {currentPage} / {totalPages}
      </span>
      <Button
        disabled={currentPage === totalPages}
        onClick={() => onChange(currentPage + 1)}
        size="sm"
        variant="outline"
      >
        Sau
        <ChevronRight aria-hidden="true" className="size-4" />
      </Button>
    </nav>
  )
}
