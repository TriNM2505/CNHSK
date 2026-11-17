import { useId } from 'react'

export function ImportReportArt() {
  const sceneId = useId()
  return (
    <svg
      aria-hidden="true"
      className="pointer-events-none absolute inset-0 size-full select-none text-brand-600"
      viewBox="0 0 900 260"
      fill="none"
      preserveAspectRatio="none"
    >
      <defs>
        <linearGradient id={`${sceneId}-hills`} x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="currentColor" stopOpacity="0" />
          <stop offset="100%" stopColor="currentColor" stopOpacity="0.12" />
        </linearGradient>
      </defs>
      <circle cx="784" cy="42" r="25" fill="currentColor" opacity="0.04" />
      <path
        d="M0 260V236L104 208L226 244L356 204L480 238L620 170L740 224L900 178V260Z"
        fill={`url(#${sceneId}-hills)`}
      />
      <path
        d="M0 258C160 226 244 272 430 242C614 212 744 260 900 234V260H0Z"
        fill="currentColor"
        opacity="0.05"
      />
      <g stroke="currentColor" opacity="0.12">
        <path d="M624 232Q704 156 784 232M624 226Q704 150 784 226" strokeWidth="2" />
        <path d="M644 212V204M668 194V186M692 186V178M716 186V178M740 194V186M764 212V204M654 232Q704 194 754 232" />
      </g>
    </svg>
  )
}
