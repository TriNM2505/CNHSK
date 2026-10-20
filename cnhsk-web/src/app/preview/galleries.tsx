import { BookOpen, CheckCircle2, PartyPopper, Search, SearchX, Trash2 } from 'lucide-react'
import { useState, type ReactNode } from 'react'

import {
  Button,
  Card,
  ConfirmPanel,
  DataTable,
  EmptyState,
  ErrorState,
  FormField,
  HanziText,
  IconButton,
  Input,
  LevelBadge,
  PageState,
  PageHeader,
  Pagination,
  PasswordInput,
  Progress,
  Section,
  Select,
  Skeleton,
  StatTile,
  StatusBadge,
  Tabs,
  Textarea,
  Toast,
  type DataTableColumn,
} from '@/shared/ui'

interface GalleryProps {
  children: ReactNode
  title: string
}

function Gallery({ children, title }: GalleryProps) {
  return (
    <section aria-labelledby="gallery-title" className="mx-auto max-w-content">
      <h1 className="mb-6 text-h2 text-ink" id="gallery-title">
        {title}
      </h1>
      {children}
    </section>
  )
}

function ExampleGroup({ children, title }: GalleryProps) {
  return (
    <section aria-labelledby={`example-${title}`} className="mb-8">
      <h2 className="mb-3 text-h3 text-ink" id={`example-${title}`}>
        {title}
      </h2>
      {children}
    </section>
  )
}

export function ButtonGallery() {
  const variants = ['primary', 'secondary', 'outline', 'ghost', 'danger'] as const
  const sizes = ['sm', 'md'] as const

  return (
    <Gallery title="Button">
      <ExampleGroup title="Biến thể và kích thước">
        <div className="flex flex-wrap items-center gap-3">
          {variants.flatMap((variant) =>
            sizes.map((size) => (
              <Button key={`${variant}-${size}`} size={size} variant={variant}>
                {variant} · {size}
              </Button>
            )),
          )}
        </div>
      </ExampleGroup>
      <ExampleGroup title="Trạng thái">
        <div className="flex flex-wrap items-center gap-3">
          <Button loading>Đang xử lý</Button>
          <Button disabled>Không khả dụng</Button>
        </div>
      </ExampleGroup>
      <ExampleGroup title="Cách hiển thị">
        <div className="flex flex-wrap items-center gap-3">
          <Button>Button gốc</Button>
          <Button to="/">Link nội bộ</Button>
          <Button href="https://example.com" target="_blank" variant="outline">
            Link bên ngoài
          </Button>
        </div>
      </ExampleGroup>
    </Gallery>
  )
}

export function IconButtonGallery() {
  return (
    <Gallery title="IconButton">
      <div className="flex flex-wrap items-center gap-3">
        <IconButton label="Tìm kiếm">
          <Search aria-hidden="true" className="size-5" />
        </IconButton>
        <IconButton label="Xóa mục" variant="danger">
          <Trash2 aria-hidden="true" className="size-5" />
        </IconButton>
        <IconButton disabled label="Tìm kiếm không khả dụng">
          <Search aria-hidden="true" className="size-5" />
        </IconButton>
      </div>
    </Gallery>
  )
}

export function CardGallery() {
  return (
    <Gallery title="Card">
      <div className="grid gap-4 sm:grid-cols-3">
        <Card>
          <h2 className="text-h3 text-ink">Mặc định</h2>
          <p className="mt-2 text-body-sm text-ink-secondary">Nội dung thẻ thông thường.</p>
        </Card>
        <Card tabIndex={0} variant="interactive">
          <h2 className="text-h3 text-ink">Tương tác</h2>
          <p className="mt-2 text-body-sm text-ink-secondary">Di chuột hoặc đặt tiêu điểm.</p>
        </Card>
        <Card variant="selected">
          <h2 className="text-h3 text-ink">Đã chọn</h2>
          <p className="mt-2 text-body-sm text-ink-secondary">Một lựa chọn đang hoạt động.</p>
        </Card>
      </div>
    </Gallery>
  )
}

export function SectionGallery() {
  return (
    <Gallery title="Section">
      <Section
        actions={<Button size="sm">Xem tất cả</Button>}
        subtitle="Mô tả ngắn giúp làm rõ nội dung trong phần."
        title="Chủ đề đang học"
      >
        <Card>Nội dung của section</Card>
      </Section>
    </Gallery>
  )
}

export function PageHeaderGallery() {
  return (
    <Gallery title="PageHeader">
      <PageHeader
        actions={<Button>Tạo mới</Button>}
        subtitle="Theo dõi và tiếp tục hành trình học tiếng Trung."
        title="Tổng quan học tập"
      />
    </Gallery>
  )
}

export function FormFieldGallery() {
  return (
    <Gallery title="FormField">
      <div className="max-w-reading">
        <FormField
          hint="Dùng email đã đăng ký với CNHSK."
          htmlFor="preview-email"
          label="Email"
          required
        >
          <Input id="preview-email" placeholder="ban@example.com" type="email" />
        </FormField>
        <FormField
          error="Mật khẩu phải có ít nhất 8 ký tự."
          htmlFor="preview-password-error"
          label="Mật khẩu"
          required
        >
          <PasswordInput id="preview-password-error" defaultValue="123" />
        </FormField>
      </div>
    </Gallery>
  )
}

export function InputGallery() {
  return (
    <Gallery title="Input">
      <div className="max-w-reading space-y-3">
        <Input aria-label="Ô nhập mặc định" placeholder="Nhập nội dung" />
        <Input aria-label="Ô nhập không khả dụng" disabled value="Không khả dụng" readOnly />
      </div>
    </Gallery>
  )
}

export function SelectGallery() {
  return (
    <Gallery title="Select">
      <Select aria-label="Chọn cấp độ" className="max-w-reading" defaultValue="3">
        <option value="1">HSK 1</option>
        <option value="3">HSK 3</option>
        <option value="6">HSK 6</option>
      </Select>
    </Gallery>
  )
}

export function TextareaGallery() {
  return (
    <Gallery title="Textarea">
      <Textarea
        aria-label="Ghi chú học tập"
        className="max-w-reading"
        defaultValue="Ôn lại các từ mới sau mỗi buổi học."
      />
    </Gallery>
  )
}

export function PasswordInputGallery() {
  return (
    <Gallery title="PasswordInput">
      <PasswordInput
        aria-label="Mật khẩu mẫu"
        className="max-w-reading"
        defaultValue="matkhau123"
      />
    </Gallery>
  )
}

export function StatusBadgeGallery() {
  const statuses = [
    ['success', 'Hoàn thành'],
    ['warning', 'Cần chú ý'],
    ['danger', 'Có lỗi'],
    ['info', 'Thông tin'],
    ['neutral', 'Chưa bắt đầu'],
  ] as const

  return (
    <Gallery title="StatusBadge">
      <div className="flex flex-wrap gap-3">
        {statuses.map(([tone, label]) => (
          <StatusBadge key={tone} tone={tone}>
            {label}
          </StatusBadge>
        ))}
      </div>
    </Gallery>
  )
}

export function LevelBadgeGallery() {
  const levels = [1, 2, 3, 4, 5, 6, 7, 8, 9] as const

  return (
    <Gallery title="LevelBadge">
      <ExampleGroup title="Theo nhóm cấp">
        <div className="flex flex-wrap gap-3">
          {levels.map((level) => (
            <LevelBadge key={level} level={level} />
          ))}
        </div>
      </ExampleGroup>
      <ExampleGroup title="Cấp chính xác">
        <div className="flex flex-wrap gap-3">
          {levels.map((level) => (
            <LevelBadge exact key={level} level={level} />
          ))}
        </div>
      </ExampleGroup>
    </Gallery>
  )
}

export function HanziTextGallery() {
  return (
    <Gallery title="HanziText">
      <div className="flex flex-col items-start gap-4">
        <HanziText size="lg">学习中文</HanziText>
        <HanziText size="md">汉字练习</HanziText>
        <HanziText size="sm">你好</HanziText>
      </div>
    </Gallery>
  )
}

export function StatTileGallery() {
  return (
    <Gallery title="StatTile">
      <div className="grid gap-4 sm:grid-cols-3">
        <StatTile label="Từ đã học" value="128" />
        <StatTile label="Chuỗi ngày" value="12" />
        <StatTile label="Độ chính xác" value="86%" />
      </div>
    </Gallery>
  )
}

export function FormControlsGallery() {
  return (
    <Gallery title="Các điều khiển biểu mẫu">
      <div className="max-w-reading">
        <FormField htmlFor="all-input" label="Từ khóa">
          <Input id="all-input" placeholder="Nhập từ khóa" />
        </FormField>
        <FormField htmlFor="all-select" label="Cấp độ">
          <Select defaultValue="2" id="all-select">
            <option value="1">HSK 1</option>
            <option value="2">HSK 2</option>
          </Select>
        </FormField>
        <FormField htmlFor="all-textarea" label="Ghi chú">
          <Textarea id="all-textarea" placeholder="Nhập ghi chú" />
        </FormField>
        <FormField htmlFor="all-password" label="Mật khẩu">
          <PasswordInput id="all-password" />
        </FormField>
      </div>
    </Gallery>
  )
}

export function SharedUiOverviewGallery() {
  return (
    <Gallery title="Tổng quan component">
      <Card className="flex items-center gap-3">
        <span className="flex size-touch items-center justify-center rounded-sm bg-brand-50 text-brand-700">
          <BookOpen aria-hidden="true" className="size-5" />
        </span>
        <div>
          <p className="text-label text-ink">Thư viện giao diện CNHSK</p>
          <p className="text-body-sm text-ink-secondary">
            Chọn từng component trong thanh danh sách để xem đầy đủ biến thể.
          </p>
        </div>
      </Card>
    </Gallery>
  )
}

export function ProgressGallery() {
  return (
    <Gallery title="Progress">
      <div className="max-w-reading space-y-4">
        <Progress value={35} />
        <Progress label="7 trên 10 bài" max={10} tone="success" value={7} />
        <Progress label="3 ngày liên tiếp" max={7} tone="accent" value={3} />
      </div>
    </Gallery>
  )
}

interface PreviewUser {
  email: string
  id: number
  name: string
  role: string
  status: ReactNode
}

const previewUsers: PreviewUser[] = [
  {
    email: 'ba•••@gmail.com',
    id: 1,
    name: 'Người học mẫu',
    role: 'LEARNER',
    status: <StatusBadge tone="success">Hoạt động</StatusBadge>,
  },
  {
    email: 'th•••@gmail.com',
    id: 2,
    name: 'Giáo viên mẫu',
    role: 'TEACHER',
    status: <StatusBadge tone="neutral">Chưa xác minh</StatusBadge>,
  },
]

const previewUserColumns: DataTableColumn<PreviewUser>[] = [
  { header: 'Tên hiển thị', id: 'name', render: (row) => row.name },
  { header: 'Email', id: 'email', render: (row) => row.email },
  { header: 'Vai trò', id: 'role', render: (row) => row.role },
  { header: 'Trạng thái', id: 'status', render: (row) => row.status },
  { header: 'Mã nội bộ', hideOnMobile: true, id: 'id', numeric: true, render: (row) => row.id },
]

export function DataTableGallery() {
  return (
    <Gallery title="DataTable">
      <ExampleGroup title="Mặc định">
        <DataTable
          caption="Danh sách tài khoản mẫu"
          columns={previewUserColumns}
          rowKey={(row) => row.id}
          rows={previewUsers}
        />
      </ExampleGroup>
      <ExampleGroup title="Hàng tương tác">
        <DataTable
          caption="Danh sách tài khoản có thể mở"
          columns={previewUserColumns}
          getRowLabel={(row) => `Mở tài khoản ${row.name}`}
          onRowClick={() => undefined}
          rowKey={(row) => row.id}
          rows={previewUsers}
        />
      </ExampleGroup>
    </Gallery>
  )
}

export function PaginationGallery() {
  const [page, setPage] = useState(2)

  return (
    <Gallery title="Pagination">
      <Pagination onChange={setPage} page={page} size={10} total={34} />
    </Gallery>
  )
}

export function TabsGallery() {
  const [activeTab, setActiveTab] = useState('vocabulary')

  return (
    <Gallery title="Tabs">
      <Tabs
        items={[
          { id: 'vocabulary', label: 'Từ mới', panel: <Card>Nội dung từ mới</Card> },
          { id: 'recognition', label: 'Nhận diện', panel: <Card>Bài luyện nhận diện</Card> },
          { id: 'listening', label: 'Nghe', panel: <Card>Bài luyện nghe</Card> },
          { disabled: true, id: 'test', label: 'Kiểm tra', panel: null },
        ]}
        label="Các bước học"
        onChange={setActiveTab}
        value={activeTab}
      />
    </Gallery>
  )
}

export function ConfirmPanelGallery() {
  const [panel, setPanel] = useState<'default' | 'danger' | null>(null)

  return (
    <Gallery title="ConfirmPanel">
      <div className="flex flex-wrap gap-3">
        <Button onClick={() => setPanel('default')}>Mở xác nhận</Button>
        <Button onClick={() => setPanel('danger')} variant="danger">
          Mở xác nhận xoá
        </Button>
      </div>
      <ConfirmPanel
        confirmLabel="Tiếp tục"
        message="Hãy kiểm tra lại thông tin trước khi tiếp tục."
        onCancel={() => setPanel(null)}
        onConfirm={() => setPanel(null)}
        open={panel === 'default'}
        title="Xác nhận thao tác"
      />
      <ConfirmPanel
        confirmLabel="Xoá"
        danger
        message="Dữ liệu đã xoá không thể khôi phục."
        onCancel={() => setPanel(null)}
        onConfirm={() => setPanel(null)}
        open={panel === 'danger'}
        title="Xoá mục này?"
      />
    </Gallery>
  )
}

export function ToastGallery() {
  const [visibleTones, setVisibleTones] = useState(() => new Set(['success', 'error', 'info']))
  const closeToast = (tone: string) => {
    setVisibleTones((current) => {
      const next = new Set(current)
      next.delete(tone)
      return next
    })
  }

  return (
    <Gallery title="Toast">
      <div className="mb-4">
        <Button
          onClick={() => setVisibleTones(new Set(['success', 'error', 'info']))}
          size="sm"
          variant="outline"
        >
          Hiện lại thông báo
        </Button>
      </div>
      <div className="space-y-3">
        {visibleTones.has('success') ? (
          <Toast message="Đã lưu thay đổi." onClose={() => closeToast('success')} tone="success" />
        ) : null}
        {visibleTones.has('error') ? (
          <Toast
            message="Không thể hoàn thành thao tác."
            onClose={() => closeToast('error')}
            tone="error"
          />
        ) : null}
        {visibleTones.has('info') ? (
          <Toast
            message="Dữ liệu đang được cập nhật."
            onClose={() => closeToast('info')}
            tone="info"
          />
        ) : null}
      </div>
    </Gallery>
  )
}

export function EmptyStateGallery() {
  return (
    <Gallery title="EmptyState">
      <div className="grid gap-4 lg:grid-cols-3">
        <EmptyState
          action={<Button>Bắt đầu</Button>}
          description="Tạo mục đầu tiên để bắt đầu sử dụng."
          title="Chưa có dữ liệu"
        />
        <EmptyState
          description="Hãy thay đổi từ khoá hoặc bộ lọc rồi thử lại."
          icon={<SearchX aria-hidden="true" className="size-5" />}
          title="Không có kết quả"
        />
        <EmptyState
          action={<Button variant="outline">Xem nội dung khác</Button>}
          description="Bạn đã xử lý tất cả mục trong danh sách."
          icon={<PartyPopper aria-hidden="true" className="size-5" />}
          title="Đã hoàn thành"
        />
      </div>
    </Gallery>
  )
}

export function ErrorStateGallery() {
  return (
    <Gallery title="ErrorState">
      <div className="space-y-4">
        <ErrorState message="Kết nối tới máy chủ bị gián đoạn." />
        <ErrorState
          message="Không tải được danh sách. Vui lòng thử lại."
          onRetry={() => undefined}
          requestId="req-mau-123"
        />
      </div>
    </Gallery>
  )
}

export function SkeletonGallery() {
  return (
    <Gallery title="Skeleton">
      <div className="space-y-4">
        <Skeleton variant="text" />
        <Skeleton variant="card" />
        <Skeleton variant="table" />
      </div>
    </Gallery>
  )
}

export function PageStateGallery() {
  return (
    <Gallery title="PageState">
      <div className="grid gap-4 lg:grid-cols-2">
        <StateExample title="Đang tải">
          <PageState
            empty={null}
            error={null}
            loading={<Skeleton variant="card" />}
            state="loading"
          >
            Nội dung
          </PageState>
        </StateExample>
        <StateExample title="Rỗng">
          <PageState
            empty={<EmptyState description="Chưa có dữ liệu mẫu." title="Danh sách trống" />}
            error={null}
            loading={null}
            state="empty"
          >
            Nội dung
          </PageState>
        </StateExample>
        <StateExample title="Lỗi">
          <PageState
            empty={null}
            error={<ErrorState message="Không thể tải dữ liệu mẫu." onRetry={() => undefined} />}
            loading={null}
            state="error"
          >
            Nội dung
          </PageState>
        </StateExample>
        <StateExample title="Có dữ liệu">
          <PageState empty={null} error={null} loading={null} state="ready">
            <Card className="flex items-center gap-3">
              <CheckCircle2 aria-hidden="true" className="size-5 text-success" />
              Nội dung đã sẵn sàng
            </Card>
          </PageState>
        </StateExample>
      </div>
    </Gallery>
  )
}

function StateExample({ children, title }: GalleryProps) {
  return (
    <section className="rounded-md border border-line bg-surface p-4">
      <h2 className="mb-3 text-label text-ink">{title}</h2>
      {children}
    </section>
  )
}
