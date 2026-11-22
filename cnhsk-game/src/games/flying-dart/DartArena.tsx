import { Scene } from '../../shared/ui/arcade'

const SHARDS = ['nw', 'ne', 'sw', 'se'] as const

/** The bear stands still; on a hit its dart flies to the character, which breaks apart. */
export function DartArena({ text, state }: { text: string; state: 'aim' | 'hit' | 'wrong' }) {
  return (
    <div className={`arena dart-arena dart-${state}`}>
      <Scene variant="dart" />
      <div className="dart-target">
        {state === 'hit'
          ? SHARDS.map((shard) => <span key={shard} aria-hidden="true" className={`dart-shard dart-shard-${shard}`}>{text}</span>)
          : <strong key={text} className="break-words">{text}</strong>}
      </div>
      {state === 'hit' && (
        <svg className="dart-blade" viewBox="0 0 64 20" aria-hidden="true">
          <path className="dart-steel" d="M0 10 34 1 40 10 34 19Z" />
          <path className="dart-grip" d="M39 7h17v6H39z" />
          <path className="dart-tail" d="m56 10 8-7v14z" />
        </svg>
      )}
    </div>
  )
}
