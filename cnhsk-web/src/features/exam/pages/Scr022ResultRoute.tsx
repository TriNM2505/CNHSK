import { useParams } from 'react-router-dom'
import Scr022ExamResultPage from './Scr022ExamResultPage'
import Scr022PlacementResultPage from './Scr022PlacementResultPage'

export default function Scr022ResultRoute() {
  const { sessionId, runId } = useParams()
  const param = sessionId ?? runId
  const id = Number(param)
  if (!param || !/^\d+$/.test(param) || !Number.isSafeInteger(id) || id <= 0) {
    return <p role="alert">Không tìm thấy kết quả.</p>
  }
  return sessionId === undefined ? (
    <Scr022PlacementResultPage key={id} runId={id} />
  ) : (
    <Scr022ExamResultPage key={id} sessionId={id} />
  )
}
