import { Search } from 'lucide-react'
import type { ReactNode } from 'react'

import type { ApiError } from '@/shared/api'
import {
  Button,
  DataTable,
  EmptyState,
  ErrorState,
  FormField,
  Input,
  LevelBadge,
  PageBanner,
  PageState,
  Pagination,
  Select,
  Skeleton,
  StatusBadge,
  SUPPORTED_HSK_LEVELS,
  Tabs,
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
  QuestionSource,
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
  /** Tree review tab; only passed for content editors. Without it the screen shows the list alone. */
  treeView?: {
    onViewChange: (view: QuestionBankView) => void
    panel: ReactNode
    view: QuestionBankView
  }
}

export type QuestionBankView = 'list' | 'tree'

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
  treeView,
}: QuestionBankListViewProps) {
  const withTreeTab = (list: ReactNode) =>
    treeView ? (
      <Tabs
        items={[
          { id: 'list', label: 'Câu hỏi', panel: <div className="space-y-6">{list}</div> },
          { id: 'tree', label: 'Sơ đồ cây', panel: <div>{treeView.panel}</div> },
        ]}
        label="Chế độ xem kho câu hỏi"
        onChange={(id) => treeView.onViewChange(id as QuestionBankView)}
        value={treeView.view}
      />
    ) : (
      list
    )

  return (
    <div className="mx-auto w-full min-w-0 max-w-content space-y-6 px-4 py-6 sm:px-6 lg:px-8">
      <PageBanner
        className="px-4 py-4 sm:px-5"
        title="Kho câu hỏi"
        subtitle="Tra cứu câu hỏi theo cấp HSK, loại và nút năng lực."
      />
      {withTreeTab(
        <>
          <form
            className="min-w-0 rounded-md border border-brand-100 bg-surface p-4 shadow-sm sm:p-5"
            noValidate
            onSubmit={(event) => {
              event.preventDefault()
              onSearchSubmit()
            }}
          >
            <div className="grid gap-x-4 sm:grid-cols-2 lg:grid-cols-4">
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
              <FormField htmlFor="question-source" label="Nguồn">
                <Select
                  value={query.source ?? ''}
                  onChange={(event) =>
                    onFiltersChange({ ...query, source: event.target.value as QuestionSource | '' })
                  }
                >
                  <option value="">Tất cả nguồn</option>
                  {(Object.keys(QUESTION_SOURCE_LABELS) as QuestionSource[]).map((source) => (
                    <option key={source} value={source}>
                      {QUESTION_SOURCE_LABELS[source]}
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
            <div className="grid gap-3 border-t border-line pt-4 lg:grid-cols-3">
              <FormField
                className="mb-0 min-w-0 lg:col-span-2"
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
              <div className="flex flex-wrap items-start gap-3 lg:pt-7">
                <Button type="submit">
                  <span className="flex items-center gap-2">
                    <Search aria-hidden="true" className="size-4 shrink-0" />
                    Tìm kiếm
                  </span>
                </Button>
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
            <section aria-label="Kết quả tìm câu hỏi" className="min-w-0 space-y-4">
              <p className="border-l-4 border-brand-600 pl-3 text-body-sm font-semibold text-ink">
                Tổng cộng {data?.totalElements ?? 0} câu hỏi
              </p>
              <DataTable
                className="border-brand-100 shadow-sm"
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
        </>,
      )}
    </div>
  )
}
