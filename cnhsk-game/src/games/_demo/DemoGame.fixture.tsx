import type { GameProps } from '../../shared/ui/GameShell'

export function DemoGame({ items, uiConfig, submitAnswer }: GameProps) {
  return (
    <div className="flex flex-wrap gap-3" aria-label="Game thử">
      <p className="w-full">{String(uiConfig.label ?? '')}</p>
      {items.map((item) => (
        <button
          type="button"
          key={item.no}
          className="min-h-touch rounded-sm border border-line px-3"
          onClick={() => submitAnswer(item.no, 'A')}
        >
          Trả lời {item.no}
        </button>
      ))}
    </div>
  )
}
