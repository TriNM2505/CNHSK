import { Progress } from '@/shared/ui'

export interface SessionProgressProps {
  current: number
  total: number
}

export function SessionProgress({ current, total }: SessionProgressProps) {
  const safeTotal = Math.max(1, total)
  const safeCurrent = Math.min(Math.max(current, 0), safeTotal)

  return (
    <Progress
      aria-label={`Tiến độ lượt luyện: câu ${safeCurrent} trên ${safeTotal}`}
      label={`Câu ${safeCurrent}/${safeTotal}`}
      max={safeTotal}
      value={safeCurrent}
    />
  )
}
