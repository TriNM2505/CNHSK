import { createElement, useState } from 'react'
import type { PreviewEntry, PreviewState } from '@/app/preview/types'
import { ApiError } from '@/shared/api'
import { GradingSubmissionView } from './components/GradingSubmissionView'
import { MyGradingRequestsView } from './components/MyGradingRequestsView'
import { GradingRequestDetailView } from './components/GradingRequestDetailView'
import { completedRequest, essay, pendingRequest, policy, requestPage } from './test-fixtures'
import type { RequestStatus } from './types'

const error = new ApiError({
  status: 0,
  errorCode: 'NETWORK_ERROR',
  message: 'Không tải được dữ liệu. Vui lòng thử lại.',
})
function SubmissionPreview({ state }: { state: PreviewState }) {
  const [text, setText] = useState(state === 'empty' ? '' : essay)
  const [prompt, setPrompt] = useState('Viết về thói quen học tập.')
  const [hsk, setHsk] = useState('2'),
    [confirming, setConfirming] = useState(false)
  return createElement(GradingSubmissionView, {
    state: state === 'empty' ? 'ready' : state,
    policy,
    error,
    essay: text,
    prompt,
    hsk,
    pending: false,
    confirming,
    onEssay: setText,
    onPrompt: setPrompt,
    onHsk: setHsk,
    onRetry: () => undefined,
    onReview: () => setConfirming(true),
    onCancel: () => setConfirming(false),
    onSend: () => setConfirming(false),
  })
}
function ListPreview({ state }: { state: PreviewState }) {
  const [status, setStatus] = useState<RequestStatus | ''>('')
  return createElement(MyGradingRequestsView, {
    state,
    result: requestPage,
    error,
    status,
    page: 0,
    onStatus: setStatus,
    onPage: () => undefined,
    onRetry: () => undefined,
  })
}
function DetailPreview({ state }: { state: PreviewState }) {
  const [confirming, setConfirming] = useState(false)
  return createElement(GradingRequestDetailView, {
    state: state === 'empty' ? 'ready' : state,
    request: state === 'empty' ? pendingRequest : completedRequest,
    error,
    confirming,
    pending: false,
    onRetry: () => undefined,
    onReviewCancel: () => setConfirming(true),
    onCloseCancel: () => setConfirming(false),
    onCancel: () => setConfirming(false),
  })
}
export const previews = [
  {
    group: 'screen',
    id: 'grading-submission',
    scr: 'SCR-032',
    title: 'Gửi bài chấm',
    states: ['loading', 'empty', 'error', 'ready'],
    render: (state) => createElement(SubmissionPreview, { state }),
  },
  {
    group: 'screen',
    id: 'grading-requests',
    scr: 'SCR-033',
    title: 'Yêu cầu chấm bài của tôi',
    states: ['loading', 'empty', 'error', 'ready'],
    render: (state) => createElement(ListPreview, { state }),
  },
  {
    group: 'screen',
    id: 'grading-detail',
    scr: 'SCR-034',
    title: 'Chi tiết yêu cầu chấm bài',
    states: ['loading', 'empty', 'error', 'ready'],
    render: (state) => createElement(DetailPreview, { state }),
  },
] satisfies readonly PreviewEntry[]
