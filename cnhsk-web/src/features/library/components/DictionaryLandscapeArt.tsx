import { useId } from 'react'

export function DictionaryLandscapeArt({ bridge = false }: { bridge?: boolean }) {
  const sceneId = useId()
  return (
    <svg
      aria-hidden="true"
      className="pointer-events-none absolute inset-0 size-full select-none text-brand-600"
      fill="none"
      preserveAspectRatio="none"
      viewBox="0 0 1000 210"
    >
      <defs>
        <linearGradient id={`${sceneId}-mist`} x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="currentColor" stopOpacity="0" />
          <stop offset="100%" stopColor="currentColor" stopOpacity="0.1" />
        </linearGradient>
      </defs>
      <path
        d="M0 210V170L84 132L176 176L292 100L420 180L552 140L664 174L780 88L902 170L1000 120V210Z"
        fill={`url(#${sceneId}-mist)`}
      />
      <path
        d="M0 195C128 150 220 204 368 178C526 150 594 202 730 172C846 146 922 184 1000 162V210H0Z"
        fill="currentColor"
        opacity="0.04"
      />
      <path
        d="M0 207C210 178 294 216 480 197C680 178 824 204 1000 188V210H0Z"
        fill="currentColor"
        opacity="0.05"
      />
      {bridge ? (
        <g opacity="0.1" stroke="currentColor" strokeLinecap="round" strokeWidth="2">
          <path d="M680 187Q790 102 900 187M680 180Q790 95 900 180" />
          <path d="M700 174V166M720 161V153M740 151V143M760 145V137M780 142V134M800 142V134M820 145V137M840 151V143M860 161V153M880 174V166" />
          <path d="M720 187Q790 140 860 187" />
        </g>
      ) : null}
    </svg>
  )
}
