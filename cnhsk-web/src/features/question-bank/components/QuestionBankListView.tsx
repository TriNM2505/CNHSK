import type { ApiError } from '@/shared/api'
import {
  Button,
  DataTable,
  EmptyState,
  ErrorState,
  FormField,
  Input,
  LevelBadge,
  PageHeader,
  PageState,
  Pagination,
  Select,
  Skeleton,
  StatusBadge,
  SUPPORTED_HSK_LEVELS,
  type DataTableColumn,
  type HskLevel,
  type PageStateValue,
  type StatusTone,
} from '@/shared/ui'

import {
  QUESTION_SOURCE_LABELS,
  QUESTION_STATUS_LABELS,
  QUESTION_TYPE_LABELS,
  type QuestionListQuery,
} from '../questionListQuery'
import { limitQuestionInput } from '../questionEditForm'
import type {
  KnowledgeNode,
  QuestionPage,
  QuestionStatus,
  QuestionSummary,
  QuestionType,
} from '../types'

export type QuestionBankListViewProps = {
  data?: QuestionPage
  error?: ApiError
  nodes: readonly KnowledgeNode[]
  nodesError?: ApiError
  nodesState: PageStateValue
  onClearFilters: () => void
  onFiltersChange: (query: QuestionListQuery) => void
  onNodesRetry: () => void
  onOpenQuestion: (id: number) => void
  onPageChange: (page: number) => void
  onRetry: () => void
  onSearchChange: (value: string) => void
  onSearchSubmit: () => void
  query: QuestionListQuery
  searchDraft: string
  searchError?: string
  state: PageStateValue
}

const statusTones: Record<QuestionStatus, StatusTone> = {
  PENDING_REVIEW: 'warning',
  APPROVED: 'success',
  REJECTED: 'danger',
  ARCHIVED: 'neutral',
}

const columns: readonly DataTableColumn<QuestionSummary>[] = [
  { id: 'code', header: 'Mã câu hỏi', render: (row) => row.code ?? '—' },
  {
    id: 'stem',
    header: 'Tóm tắt nội dung',
    render: (row) => <p className="break-words">{row.stemSummary}</p>,
  },
  { id: 'type', header: 'Loại câu hỏi', render: (row) => QUESTION_TYPE_LABELS[row.qtype] },
  {
    id: 'level',
    header: 'HSK',
    render: (row) => <LevelBadge exact level={row.hskLevel as HskLevel} />,
  },
  {
    id: 'status',
    header: 'Nguồn / Trạng thái',
    render: (row) => (
      <div className="space-y-2">
        <p>{QUESTION_SOURCE_LABELS[row.source]}</p>
        <StatusBadge tone={statusTones[row.status]}>
          {QUESTION_STATUS_LABELS[row.status]}
        </StatusBadge>
      </div>
    ),
  },
  {
    id: 'kids',
    header: 'Nút năng lực',
    render: (row) => (
      <div className="space-y-1 text-body-sm">
        <p>Chính: {row.primaryKid ?? 'Chưa gắn nút'}</p>
        {row.secondaryKids.length > 0 ? <p>Phụ: {row.secondaryKids.join(', ')}</p> : null}
      </div>
    ),
  },
  { id: 'used', header: 'Đã dùng', render: (row) => (row.used ? 'Đã dùng' : 'Chưa dùng') },
]

export function QuestionBankListView({
  data,
  error,
  nodes,
  nodesError,
  nodesState,
  onClearFilters,
  onFiltersChange,
  onNodesRetry,
  onOpenQuestion,
  onPageChange,
  onRetry,
  onSearchChange,
  onSearchSubmit,
  query,
  searchDraft,
  searchError,
  state,
}: QuestionBankListViewProps) {
  return (
    <div className="mx-auto max-w-content space-y-6">
      <PageHeader
        title="Kho câu hỏi"
        subtitle="Tra cứu câu hỏi theo cấp HSK, loại và nút năng lực."
      />
      <form
        className="rounded-md border border-line bg-surface p-4 sm:p-6"
        noValidate
        onSubmit={(event) => {
          event.preventDefault()
          onSearchSubmit()
        }}
      >
        <div className="grid gap-x-4 sm:grid-cols-2 xl:grid-cols-4">
          <FormField htmlFor="question-level" label="Cấp HSK">
            <Select
              value={query.hskLevel}
              onChange={(event) =>
                onFiltersChange({
                  ...query,
                  hskLevel: event.target.value ? (Number(event.target.value) as HskLevel) : '',
                })
              }
            >
              <option value="">Tất cả cấp HSK</option>
              {SUPPORTED_HSK_LEVELS.map((level) => (
                <option key={level} value={level}>
                  HSK {level}
                </option>
              ))}
            </Select>
          </FormField>
          <FormField htmlFor="question-type" label="Loại câu hỏi">
            <Select
              value={query.qtype}
              onChange={(event) =>
                onFiltersChange({ ...query, qtype: event.target.value as QuestionType | '' })
              }
            >
              <option value="">Tất cả loại câu</option>
              {(Object.keys(QUESTION_TYPE_LABELS) as QuestionType[]).map((type) => (
                <option key={type} value={type}>
                  {QUESTION_TYPE_LABELS[type]}
                </option>
              ))}
            </Select>
          </FormField>
          <FormField htmlFor="question-status" label="Trạng thái">
            <Select
              value={query.status}
              onChange={(event) =>
                onFiltersChange({ ...query, status: event.target.value as QuestionStatus | '' })
              }
            >
              <option value="">Tất cả trạng thái</option>
              {(Object.keys(QUESTION_STATUS_LABELS) as QuestionStatus[]).map((status) => (
                <option key={status} value={status}>
                  {QUESTION_STATUS_LABELS[status]}
                </option>
              ))}
            </Select>
          </FormField>
          <FormField htmlFor="question-kid" label="Nút năng lực">
            <Select
              disabled={nodesState === 'loading'}
              value={query.kid}
              onChange={(event) => onFiltersChange({ ...query, kid: event.target.value })}
            >
              <option value="">Tất cả nút</option>
              {query.kid && !nodes.some((node) => node.kid === query.kid) ? (
                <option value={query.kid}>{query.kid} (chưa có trong cây)</option>
              ) : null}
              {nodes.map((node) => (
                <option key={node.kid} value={node.kid}>
                  {node.kid} · {node.nameVi}
                </option>
              ))}
            </Select>
          </FormField>
        </div>
        {nodesState === 'loading' ? (
          <p className="mb-4 text-body-sm text-ink-secondary" role="status">
            Đang tải nút năng lực…
          </p>
        ) : null}
        {nodesState === 'error' ? (
          <ErrorState
            className="mb-4"
            title="Không thể tải nút năng lực"
            message={nodesError?.message ?? 'Vui lòng thử lại.'}
            onRetry={onNodesRetry}
            requestId={nodesError?.requestId}
          />
        ) : null}
        <div className="flex flex-wrap items-start gap-3">
          <FormField
            className="mb-0 min-w-0 flex-1"
            htmlFor="question-search"
            label="Tìm câu hỏi"
            error={searchError}
            hint="Tìm theo mã hoặc nội dung, từ 2 đến 100 ký tự."
          >
            <Input
              onChange={(event) => onSearchChange(limitQuestionInput(event.target.value, 100))}
              placeholder="Nhập mã hoặc nội dung câu hỏi"
              value={searchDraft}
            />
          </FormField>
          <div className="flex gap-3 pt-7">
            <Button type="submit">Tìm kiếm</Button>
            <Button onClick={onClearFilters} type="button" variant="outline">
              Xoá bộ lọc
            </Button>
          </div>
        </div>
      </form>
      <PageState
        empty={
          <EmptyState
            title="Chưa có câu hỏi trong phạm vi này"
            description="Thử thay đổi hoặc xoá bộ lọc để xem câu hỏi khác."
            action={
              <div className="flex flex-wrap justify-center gap-3">
                <Button onClick={onClearFilters} type="button" variant="outline">
                  Xoá bộ lọc
                </Button>
                {query.page > 0 ? (
                  <Button onClick={() => onPageChange(0)} type="button" variant="outline">
                    Về trang đầu
                  </Button>
                ) : null}
              </div>
            }
          />
        }
        error={
          <ErrorState
            title="Không thể tải kho câu hỏi"
            message={error?.message ?? 'Vui lòng thử lại.'}
            onRetry={onRetry}
            requestId={error?.requestId}
          />
        }
        loading={
          <div aria-label="Đang tải câu hỏi" className="space-y-3" role="status">
            <Skeleton className="h-12" />
            <Skeleton className="h-64" />
          </div>
        }
        state={state}
      >
        <section aria-label="Kết quả tìm câu hỏi" className="space-y-4">
          <p className="text-body-sm text-ink-secondary">
            Tổng cộng {data?.totalElements ?? 0} câu hỏi
          </p>
          <DataTable
            caption="Danh sách câu hỏi"
            columns={columns}
            getRowLabel={(row) => `Mở câu hỏi ${row.code ?? row.id}`}
            onRowClick={(row) => onOpenQuestion(row.id)}
            rowKey={(row) => row.id}
            rows={data?.items ?? []}
          />
          <Pagination
            label="Phân trang câu hỏi"
            onChange={(page) => onPageChange(page - 1)}
            page={(data?.page ?? 0) + 1}
            size={data?.size ?? 10}
            total={data?.totalElements ?? 0}
          />
        </section>
      </PageState>
    </div>
  )
}
