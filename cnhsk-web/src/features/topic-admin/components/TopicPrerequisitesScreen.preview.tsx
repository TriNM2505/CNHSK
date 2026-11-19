import { useState } from 'react'
import type { AdminTopic, TopicFilters, TopicRef } from '../types'
import { TopicPrerequisitesView, type TopicPrerequisitesViewProps } from './TopicPrerequisitesView'

export type TopicPrerequisitePreset =
  | 'ready'
  | 'cycle'
  | 'warning'
  | 'loading'
  | 'empty'
  | 'error'
  | 'unselected'
  | 'saving'
  | 'limit'
  | 'candidate-loading'
  | 'candidate-error'
  | 'pagination'

function makeSamples(base: TopicPrerequisitesViewProps): AdminTopic[] {
  return [
    ...base.topics.data!.items,
    { id: 12, name: 'Gia đình và bạn bè 家人', hskLevel: 1, isRoot: true, prereqs: [] },
    {
      id: 21,
      name: 'Thời gian và lịch hẹn 时间',
      hskLevel: 2,
      isRoot: false,
      prereqs: [base.topics.data!.items[0]!],
    },
    {
      id: 22,
      name: 'Mua sắm và thanh toán 购物',
      hskLevel: 2,
      isRoot: false,
      prereqs: [base.topics.data!.items[0]!],
    },
  ]
}
const limitTopics: AdminTopic[] = Array.from({ length: 11 }, (_, index) => ({
  id: 100 + index,
  name: `Chủ đề mẫu ${index + 1}`,
  hskLevel: 1,
  isRoot: true,
  prereqs: [],
}))

export function TopicPrerequisitesScreenPreview({
  preset,
  examples,
}: {
  preset: TopicPrerequisitePreset
  examples: Record<'ready' | 'cycle' | 'warning', TopicPrerequisitesViewProps>
}) {
  const base = examples.ready
  const startingTopic =
    preset === 'limit' ? { ...base.selected!, prereqs: limitTopics.slice(0, 10) } : base.selected!
  const [selected, setSelected] = useState<AdminTopic | undefined>(
    ['loading', 'empty', 'error', 'unselected'].includes(preset) ? undefined : startingTopic,
  )
  const [chosen, setChosen] = useState<readonly TopicRef[]>(
    preset === 'warning' ? examples.warning.chosen : startingTopic.prereqs,
  )
  const [topics, setTopics] = useState<AdminTopic[]>(
    preset === 'limit' ? [startingTopic, ...limitTopics] : makeSamples(base),
  )
  const [filters, setFilters] = useState<TopicFilters>({ q: '', page: 0 })
  const [candidateFilters, setCandidateFilters] = useState<TopicFilters>({ q: '', page: 0 })
  const [loadError, setLoadError] = useState(preset === 'error')
  const [candidateError, setCandidateError] = useState(preset === 'candidate-error')
  const [feedback, setFeedback] = useState<
    Pick<TopicPrerequisitesViewProps, 'error' | 'message' | 'cycleNames' | 'warnings'>
  >(
    preset === 'cycle'
      ? {
          error: examples.cycle.error,
          cycleNames: examples.cycle.cycleNames,
          warnings: [],
        }
      : preset === 'warning'
        ? {
            message: examples.warning.message,
            warnings: examples.warning.warnings,
          }
        : { warnings: [] },
  )
  const saving = preset === 'saving'
  const clearFeedback = () => setFeedback({ warnings: [] })
  function list(
    currentFilters: TopicFilters,
    candidates = false,
  ): TopicPrerequisitesViewProps['topics'] {
    const matching = topics.filter(
      (topic) =>
        (!currentFilters.q ||
          topic.name.toLocaleLowerCase('vi').includes(currentFilters.q.toLocaleLowerCase('vi'))) &&
        (!currentFilters.hsk || topic.hskLevel === currentFilters.hsk),
    )
    const size = preset === 'pagination' ? 3 : 20
    const state =
      (!candidates && preset === 'loading') || (candidates && preset === 'candidate-loading')
        ? 'loading'
        : (!candidates && loadError) || (candidates && candidateError)
          ? 'error'
          : 'ready'
    return {
      state,
      data: {
        items:
          !candidates && preset === 'empty'
            ? []
            : matching.slice(currentFilters.page * size, (currentFilters.page + 1) * size),
        page: currentFilters.page,
        size,
        totalElements: !candidates && preset === 'empty' ? 0 : matching.length,
      },
      error:
        state === 'error'
          ? { message: 'Không thể tải danh sách chủ đề mẫu.', requestId: 'req-preview-topics' }
          : undefined,
      filters: currentFilters,
      pending: saving,
      selected,
      selectedIds: chosen.map(({ id }) => id),
      onRetry: () => (candidates ? setCandidateError(false) : setLoadError(false)),
      onFilter: candidates ? setCandidateFilters : setFilters,
      onSelect: (topic) => {
        if (saving) return
        clearFeedback()
        if (candidates) {
          if (topic.id === selected?.id) return
          setChosen((current) =>
            current.some(({ id }) => id === topic.id)
              ? current.filter(({ id }) => id !== topic.id)
              : current.length < 10
                ? [...current, topic]
                : current,
          )
        } else {
          setSelected(topic)
          setChosen(topic.prereqs)
          setCandidateFilters({ q: '', page: 0 })
        }
      },
    }
  }
  return (
    <>
      <p
        role="note"
        className="mx-4 mt-4 rounded-md border border-info bg-info-bg px-4 py-3 text-body-sm text-info sm:mx-6 lg:mx-8"
      >
        Bản xem trước · Thao tác chỉ áp dụng cho dữ liệu mẫu.
      </p>
      <TopicPrerequisitesView
        topics={list(filters)}
        candidates={list(candidateFilters, true)}
        selected={selected}
        chosen={chosen}
        saving={saving}
        {...feedback}
        onRemove={(id) => {
          if (!saving) {
            clearFeedback()
            setChosen((current) => current.filter((topic) => topic.id !== id))
          }
        }}
        onSave={() => {
          if (!selected || saving) return
          if (preset === 'cycle') {
            setFeedback({
              error: examples.cycle.error,
              cycleNames: examples.cycle.cycleNames,
              warnings: [],
            })
            return
          }
          const updated = { ...selected, prereqs: [...chosen], isRoot: chosen.length === 0 }
          setTopics((current) =>
            current.map((topic) => (topic.id === selected.id ? updated : topic)),
          )
          setSelected(updated)
          setFeedback({
            message: 'Đã cập nhật bản mẫu. Dữ liệu thật không thay đổi.',
            warnings: [],
          })
        }}
      />
    </>
  )
}
