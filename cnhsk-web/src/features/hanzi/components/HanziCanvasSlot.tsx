import { PenLine } from 'lucide-react'

/**
 * Chỗ đặt khung viết `HanziCanvas` (spec 006 FR-030): đúng 300 × 300 px, co về tối thiểu 240 px trên màn nhỏ.
 * Thư viện `hanzi-writer` chưa được duyệt (spec 006 Q1) nên khung này chưa vẽ chữ và chưa gửi kết quả.
 */
export function HanziCanvasSlot() {
  return (
    <div
      className="mx-auto flex aspect-square w-full min-w-[240px] max-w-[300px] flex-col items-center justify-center gap-2 rounded-md border-2 border-dashed border-line bg-surface-subtle p-4 text-center"
      data-testid="hanzi-canvas-slot"
    >
      <PenLine aria-hidden="true" className="size-6 text-ink-muted" />
      <p className="text-body-sm text-ink-secondary">Khung viết chữ đang được hoàn thiện</p>
    </div>
  )
}
