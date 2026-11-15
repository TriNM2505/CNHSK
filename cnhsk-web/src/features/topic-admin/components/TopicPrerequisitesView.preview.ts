import type { TopicPrerequisitesViewProps } from './TopicPrerequisitesView'
import type { AdminTopic } from '../types'

const topic: AdminTopic = {
  id: 31,
  name: 'Giao tiếp trong cuộc sống hằng ngày 日常交流',
  hskLevel: 2,
  isRoot: false,
  prereqs: [{ id: 11, name: 'Chào hỏi và giới thiệu bản thân 问候', hskLevel: 1 }],
}
const root: AdminTopic = {
  id: 11,
  name: 'Chào hỏi và giới thiệu bản thân 问候',
  hskLevel: 1,
  isRoot: true,
  prereqs: [],
}
const advanced: AdminTopic = {
  id: 52,
  name: 'Những cuộc trò chuyện tại trường học và nơi làm việc 学校与工作',
  hskLevel: 3,
  isRoot: true,
  prereqs: [],
}
const noop = () => undefined
const list: TopicPrerequisitesViewProps['topics'] = {
  data: { items: [root, topic, advanced], page: 0, size: 20, totalElements: 3 },
  filters: { q: '', page: 0 },
  onFilter: noop,
  onRetry: noop,
  onSelect: noop,
  pending: false,
  selected: topic,
  selectedIds: [11],
  state: 'ready',
}
const base: TopicPrerequisitesViewProps = {
  topics: list,
  candidates: list,
  selected: topic,
  chosen: topic.prereqs,
  onRemove: noop,
  onSave: noop,
  saving: false,
  warnings: [],
}
export const topicPrerequisitePreviews = {
  ready: base,
  cycle: {
    ...base,
    error: { message: 'Không thể lưu vì có vòng tiên quyết.', requestId: 'req-preview-cycle' },
    cycleNames: [topic.name, advanced.name, root.name, topic.name],
  },
  warning: {
    ...base,
    chosen: [...topic.prereqs, advanced],
    message: 'Đã lưu tiên quyết chủ đề.',
    warnings: [
      'Tiên quyết Những cuộc trò chuyện tại trường học và nơi làm việc (HSK 3) cao hơn cấp HSK 2 của chủ đề.',
    ],
  },
  loading: { ...base, selected: undefined, topics: { ...list, state: 'loading' } },
  empty: {
    ...base,
    selected: undefined,
    topics: { ...list, data: { ...list.data!, items: [], totalElements: 0 } },
  },
  error: {
    ...base,
    selected: undefined,
    topics: {
      ...list,
      state: 'error',
      error: { message: 'Không thể kết nối tới máy chủ.', requestId: 'req-preview' },
    },
  },
} satisfies Record<string, TopicPrerequisitesViewProps>
