import ongoingPanda from '../assets/contest-ongoing-panda.webp'
import upcomingPanda from '../assets/contest-upcoming-panda.webp'
import endedPanda from '../assets/contest-ended-panda.webp'
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
      height={state === 'ENDED' ? 356 : 351}
      loading={state === 'ONGOING' ? 'eager' : 'lazy'}
      src={sources[state]}
      width="384"
    />
  )
}
