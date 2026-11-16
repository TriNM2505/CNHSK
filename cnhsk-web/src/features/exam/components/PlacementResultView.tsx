import { Link } from 'react-router-dom'
import { Button, DataTable, PageHeader, StatTile } from '@/shared/ui'
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
    <main className="mx-auto w-full min-w-0 max-w-content space-y-6 px-4 py-6 pb-16 sm:px-6">
      <PageHeader title="Kết quả xếp trình độ" />
      <div className="grid min-w-0 gap-4 sm:grid-cols-2">
        <StatTile
          label="Trình độ"
          value={<span className="text-h2">Cấp ước lượng: {levels[result.level]}</span>}
        />
        <StatTile
          label="Độ tin cậy"
          value={
            <span className="text-h2">
              {result.confidence === 'SURE' ? 'Chắc chắn' : 'Kết quả tạm'}
            </span>
          }
        />
      </div>
      {error && (
        <p role="alert" className="text-danger">
          {error}
        </p>
      )}
      <div className="flex flex-wrap gap-3">
        <Button disabled={pending} onClick={onRetake}>
          Làm lại
        </Button>
        <Button to="/learn/exams" variant="outline">
          Danh sách đề thi
        </Button>
      </div>
      <DataTable
        caption="Kết quả theo cấp"
        captionVisible
        rows={result.byLevel}
        rowKey={(row) => row.level}
        columns={[
          { id: 'level', header: 'Cấp', render: (row) => `HSK ${row.level}` },
          {
            id: 'correct',
            header: 'Số câu đúng',
            render: (row) => `${row.correct}/${row.total}`,
            numeric: true,
          },
          { id: 'percent', header: 'Tỷ lệ', render: (row) => `${row.percent}%`, numeric: true },
        ]}
      />
      <section aria-label="Hồ sơ sáu miền">
        <h2 className="text-h2">Hồ sơ sáu miền</h2>
        <ul className="grid min-w-0 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {result.domains.map(({ domain, measured, correct, total, percent, tentative }) => (
            <li
              key={domain}
              className="min-w-0 space-y-3 rounded-md border border-line bg-surface p-6"
            >
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
                  className="inline-flex min-h-touch items-center text-label text-brand-700 underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2"
                  to={'/learn/practice/targeted?kid=' + encodeURIComponent(kid)}
                >
                  Luyện nút này
                </Link>
              </li>
            ))}
          </ul>
        </section>
      )}
    </main>
  )
}
