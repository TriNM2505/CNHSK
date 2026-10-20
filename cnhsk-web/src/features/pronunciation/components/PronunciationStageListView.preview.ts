import type { PronunciationStageListViewProps } from './PronunciationStageListView'

const noop = () => undefined

const stages: PronunciationStageListViewProps['stages'] = [
  {
    activeAnswered: null,
    activeSessionId: null,
    bestAccuracy: 92.5,
    description: 'Phân biệt nhóm âm môi cơ bản.',
    id: 101,
    name: 'Thanh mẫu môi',
    passPercent: 80,
    playable: true,
    stageIndex: 1,
    state: 'COMPLETED',
    targetSounds: ['b', 'p', 'm', 'f'],
    unitCount: 10,
  },
  {
    activeAnswered: 4,
    activeSessionId: 202,
    bestAccuracy: null,
    description: 'Luyện nghe các âm đầu lưỡi.',
    id: 102,
    name: 'Thanh mẫu lưỡi',
    passPercent: 80,
    playable: true,
    stageIndex: 2,
    state: 'AVAILABLE',
    targetSounds: ['d', 't', 'n', 'l'],
    unitCount: 10,
  },
  {
    activeAnswered: null,
    activeSessionId: null,
    bestAccuracy: null,
    description: 'Phân biệt các âm cuốn lưỡi.',
    id: 103,
    name: 'Âm cuốn lưỡi',
    passPercent: 80,
    playable: true,
    stageIndex: 3,
    state: 'LOCKED',
    targetSounds: ['zh', 'ch', 'sh', 'r'],
    unitCount: 12,
  },
  {
    activeAnswered: null,
    activeSessionId: null,
    bestAccuracy: null,
    description: 'Nội dung đang được biên soạn.',
    id: 104,
    name: 'Âm đầu lưỡi',
    passPercent: 80,
    playable: false,
    stageIndex: 4,
    state: 'LOCKED',
    targetSounds: ['z', 'c', 's'],
    unitCount: 0,
  },
]

export const pronunciationStageListPreviews = {
  empty: { onRetry: noop, stages: [], state: 'empty' },
  error: {
    error: { message: 'Không thể kết nối tới máy chủ.', requestId: 'req-preview' },
    onRetry: noop,
    stages: [],
    state: 'error',
  },
  loading: { onRetry: noop, stages: [], state: 'loading' },
  ready: { onRetry: noop, stages, state: 'ready' },
} satisfies Record<string, PronunciationStageListViewProps>
