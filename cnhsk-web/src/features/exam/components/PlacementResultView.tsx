import { Link } from 'react-router-dom'
import { Button } from '@/shared/ui/Button'
import type { PlacementLevel, PlacementResult } from '../types'

const levels: Record<PlacementLevel, string> = {
  BELOW_1: 'Chưa tới HSK 1',
  HSK1: 'HSK 1',
  HSK2: 'HSK 2',
  HSK3: 'HSK 3',
  ABOVE_3: 'HSK 3 trở lên',
}

export function PlacementResultView({
  result,
  onRetake,
  pending,
  error,
}: {
  result: PlacementResult
  onRetake: () => void
  pending?: boolean
  error?: string
}) {
  return (
    <main className="mx-auto max-w-content space-y-6 p-4 sm:p-6">
      <h1 className="text-h1-sm sm:text-h1">Kết quả xếp trình độ</h1>
      <p>Cấp ước lượng: {levels[result.level]}</p>
      <p>{result.confidence === 'SURE' ? 'Chắc chắn' : 'Kết quả tạm'}</p>
      <table className="w-full text-left">
        <caption className="text-h2 text-left">Kết quả theo cấp</caption>
        <thead>
          <tr>
            <th scope="col">Cấp</th>
            <th scope="col">Số câu đúng</th>
            <th scope="col">Tỷ lệ</th>
          </tr>
        </thead>
        <tbody>
          {result.byLevel.map(({ level, correct, total, percent }) => (
            <tr key={level}>
              <th scope="row">HSK {level}</th>
              <td>
                {correct}/{total}
              </td>
              <td>{percent}%</td>
            </tr>
          ))}
        </tbody>
      </table>
      <section aria-label="Hồ sơ sáu miền">
        <h2 className="text-h2">Hồ sơ sáu miền</h2>
        <ul className="grid gap-3 sm:grid-cols-2">
          {result.domains.map(({ domain, measured, correct, total, percent, tentative }) => (
            <li key={domain} className="rounded-md border border-line p-3">
              <h3 className="text-h3">{domain}</h3>
              <p>
                {measured
                  ? correct + '/' + total + (percent === null ? '' : ' (' + percent + '%)')
                  : 'Chưa đo'}
                {tentative && <span> · tạm</span>}
              </p>
            </li>
          ))}
        </ul>
      </section>
      {result.wrongKids.length > 0 && (
        <section aria-label="Câu sai theo KID">
          <h2 className="text-h2">Câu sai theo KID</h2>
          <ul className="space-y-2">
            {result.wrongKids.map(({ kid, nameVi, wrong, total }) => (
              <li key={kid}>
                {nameVi} ({kid}) · {wrong}/{total} câu sai{' '}
                <Link
                  className="text-brand-700 underline"
                  to={'/learn/practice/targeted?kid=' + encodeURIComponent(kid)}
                >
                  Luyện nút này
                </Link>
              </li>
            ))}
          </ul>
        </section>
      )}
      {error && (
        <p role="alert" className="text-danger">
          {error}
        </p>
      )}
      <Button disabled={pending} onClick={onRetake}>
        Làm lại
      </Button>
    </main>
  )
}
