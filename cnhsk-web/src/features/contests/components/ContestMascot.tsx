import ongoingPanda from '../assets/contest-ongoing-panda.png'
import upcomingPanda from '../assets/contest-upcoming-panda.png'
import endedPanda from '../assets/contest-ended-panda.png'
import type { ScheduleState } from '../types'

const sources: Record<ScheduleState, string> = {
  ONGOING: ongoingPanda,
  UPCOMING: upcomingPanda,
  ENDED: endedPanda,
}

export function ContestMascot({ state }: { state: ScheduleState }) {
  return (
    <img
      alt=""
      aria-hidden="true"
      className="block h-28 w-full object-contain"
      decoding="async"
      height="1280"
      loading={state === 'ONGOING' ? 'eager' : 'lazy'}
      src={sources[state]}
      width="1280"
    />
  )
}
