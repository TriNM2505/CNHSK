import { AudioLines, BookOpenText, Brush, Gamepad2, GraduationCap, Languages } from 'lucide-react'
import type { ReactNode } from 'react'

import type { RoleArea } from '../roleAreas'
import { Button, Card, Skeleton, StatusBadge } from '@/shared/ui'

export type LandingViewState = 'loading' | 'empty' | 'error' | 'ready'

export type LandingViewProps = {
  areas?: readonly RoleArea[]
  displayName?: string
  onRetry: () => void
  state: LandingViewState
}

const LEARNING_MODES: readonly {
  description: string
  icon: ReactNode
  title: string
}[] = [
  {
    description: 'Học từ vựng theo từng tình huống và chủ điểm gần gũi.',
    icon: <BookOpenText aria-hidden="true" className="size-6" />,
    title: 'Chủ đề',
  },
  {
    description: 'Hiểu cấu trúc câu bằng giải thích và ví dụ tiếng Việt.',
    icon: <Languages aria-hidden="true" className="size-6" />,
    title: 'Ngữ pháp',
  },
  {
    description: 'Luyện nghe, phân biệt âm và nói rõ hơn qua từng chặng.',
    icon: <AudioLines aria-hidden="true" className="size-6" />,
    title: 'Phát âm',
  },
  {
    description: 'Nhận diện và làm quen với cách viết chữ Hán đúng nét.',
    icon: <Brush aria-hidden="true" className="size-6" />,
    title: 'Chữ Hán',
  },
  {
    description: 'Ôn kiến thức bằng những lượt chơi ngắn và dễ bắt đầu.',
    icon: <Gamepad2 aria-hidden="true" className="size-6" />,
    title: 'Game',
  },
]

function GuestActions() {
  return (
    <div className="flex flex-col gap-3 sm:flex-row">
      <Button to="/register">Bắt đầu học</Button>
      <Button to="/login" variant="outline">
        Đăng nhập
      </Button>
    </div>
  )
}

function SessionRegion({ areas = [], displayName, onRetry, state }: LandingViewProps) {
  if (state === 'loading') {
    return (
      <div aria-label="Đang kiểm tra phiên đăng nhập" className="space-y-3">
        <Skeleton className="max-w-sm" variant="text" />
        <Skeleton className="max-w-md" variant="card" />
      </div>
    )
  }

  if (state === 'error') {
    return (
      <div className="space-y-3">
        <GuestActions />
        <div
          className="flex flex-wrap items-center gap-2 text-body-sm text-ink-secondary"
          role="status"
        >
          <span>Chưa kiểm tra được phiên đăng nhập.</span>
          <Button onClick={onRetry} size="sm" variant="ghost">
            Thử lại
          </Button>
        </div>
      </div>
    )
  }

  if (!displayName) return <GuestActions />

  return (
    <section
      aria-labelledby="your-areas-title"
      className="rounded-lg bg-surface p-5 shadow-md sm:p-6"
    >
      <p className="text-body-sm text-ink-secondary">Xin chào, {displayName}</p>
      <h2 className="mt-1 text-h2 text-ink" id="your-areas-title">
        Khu vực của bạn
      </h2>
      {state === 'empty' || areas.length === 0 ? (
        <p className="mt-3 text-body text-ink-secondary">
          Tài khoản chưa được gán khu vực riêng. Bạn vẫn có thể xem phần giới thiệu bên dưới.
        </p>
      ) : (
        <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {areas.map((area) => (
            <Card
              className="flex min-h-28 flex-col items-start justify-between gap-3 p-4"
              key={area.role}
            >
              <div>
                <h3 className="text-h3 text-ink">{area.label}</h3>
                {area.status === 'under-construction' ? (
                  <StatusBadge className="mt-2" tone="info">
                    Đang xây dựng
                  </StatusBadge>
                ) : (
                  <p className="mt-1 text-body-sm text-ink-secondary">Khu vực đã sẵn sàng</p>
                )}
              </div>
              {area.status === 'available' && area.to ? (
                <Button size="sm" to={area.to} variant="outline">
                  Mở {area.label.toLocaleLowerCase('vi')}
                </Button>
              ) : null}
            </Card>
          ))}
        </div>
      )}
    </section>
  )
}

export function LandingView(props: LandingViewProps) {
  return (
    <main className="min-w-0 bg-canvas">
      <section className="bg-surface-subtle px-4 py-12 sm:py-16 lg:py-20">
        <div className="mx-auto grid max-w-content items-center gap-10 lg:grid-cols-[minmax(0,1.2fr)_minmax(280px,0.8fr)]">
          <div className="min-w-0">
            <p className="text-label text-brand-700">Tiếng Trung và HSK dành cho người Việt</p>
            <h1 className="mt-3 max-w-reading text-display-sm text-brand-800 sm:text-display">
              Học tiếng Trung rõ ràng, từng bước một
            </h1>
            <p className="mt-4 max-w-reading text-body text-ink-secondary">
              Học từ vựng, ngữ pháp, phát âm và chữ Hán trong một không gian thống nhất, với hướng
              dẫn bằng tiếng Việt.
            </p>
            <div className="mt-7">
              <SessionRegion {...props} />
            </div>
          </div>

          <div className="rounded-lg border border-brand-100 bg-surface p-6 text-center shadow-md sm:p-8">
            <div className="mx-auto flex size-14 items-center justify-center rounded-pill bg-brand-50 text-brand-700">
              <GraduationCap aria-hidden="true" className="size-7" />
            </div>
            <p className="mt-5 font-hanzi text-hanzi-lg text-brand-800" lang="zh-CN">
              汉语
            </p>
            <p className="mt-1 text-h3 text-ink">hànyǔ</p>
            <p className="mt-2 text-body text-ink-secondary">Tiếng Hán, tiếng Trung Quốc</p>
          </div>
        </div>
      </section>

      <section
        aria-labelledby="learning-modes-title"
        className="mx-auto max-w-content px-4 py-12 sm:py-16"
      >
        <div className="max-w-reading">
          <p className="text-label text-brand-700">Nhiều cách để bắt đầu</p>
          <h2 className="mt-2 text-h1-sm text-ink sm:text-h1" id="learning-modes-title">
            Học theo cách phù hợp với bạn
          </h2>
          <p className="mt-3 text-body text-ink-secondary">
            Mỗi hình thức tập trung vào một phần khác nhau của tiếng Trung. Các chức năng học cần
            tài khoản và vai trò phù hợp.
          </p>
        </div>
        <div className="mt-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {LEARNING_MODES.map((mode) => (
            <Card className="p-5" key={mode.title}>
              <div className="flex size-touch items-center justify-center rounded-sm bg-brand-50 text-brand-700">
                {mode.icon}
              </div>
              <h3 className="mt-4 text-h3 text-ink">{mode.title}</h3>
              <p className="mt-2 text-body-sm text-ink-secondary">{mode.description}</p>
            </Card>
          ))}
        </div>
      </section>
    </main>
  )
}
