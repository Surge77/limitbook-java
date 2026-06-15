import type { BookLevel, BookSnapshot } from '../types';
import { formatQty, ticksToPrice } from '../lib/format';

const DEPTH = 12;

function cumulative(levels: BookLevel[]): number[] {
  const out: number[] = [];
  let sum = 0;
  for (const l of levels) {
    sum += l.quantity;
    out.push(sum);
  }
  return out;
}

interface RowProps {
  level: BookLevel;
  cum: number;
  max: number;
  side: 'bid' | 'ask';
}

function Row({ level, cum, max, side }: RowProps) {
  const pct = Math.max(2, (cum / max) * 100);
  const barColor = side === 'ask' ? 'bg-red-500/15' : 'bg-green-500/15';
  const priceColor = side === 'ask' ? 'text-red-400' : 'text-green-400';
  return (
    <div className="relative grid grid-cols-3 px-3 py-[3px] text-xs tabular">
      <div className={`absolute inset-y-0 right-0 ${barColor}`} style={{ width: `${pct}%` }} />
      <span className={`relative z-10 ${priceColor}`}>{ticksToPrice(level.price)}</span>
      <span className="relative z-10 text-right text-slate-300">{formatQty(level.quantity)}</span>
      <span className="relative z-10 text-right text-slate-500">{formatQty(cum)}</span>
    </div>
  );
}

export function OrderBook({ book }: { book: BookSnapshot }) {
  const asks = book.asks.slice(0, DEPTH);
  const bids = book.bids.slice(0, DEPTH);
  const askCum = cumulative(asks);
  const bidCum = cumulative(bids);
  const max = Math.max(1, ...askCum, ...bidCum);

  const bestBid = bids[0]?.price ?? -1;
  const bestAsk = asks[0]?.price ?? -1;
  const spread = bestBid > 0 && bestAsk > 0 ? bestAsk - bestBid : -1;

  return (
    <div className="flex h-full flex-col">
      <div className="grid grid-cols-3 px-3 pb-1 text-[10px] uppercase tracking-wider text-slate-500">
        <span>Price</span>
        <span className="text-right">Size</span>
        <span className="text-right">Total</span>
      </div>

      <div className="flex flex-1 flex-col justify-end overflow-hidden">
        {asks
          .map((l, i) => ({ l, cum: askCum[i] }))
          .reverse()
          .map(({ l, cum }) => (
            <Row key={`a-${l.price}`} level={l} cum={cum} max={max} side="ask" />
          ))}
      </div>

      <div className="flex items-center justify-between border-y border-slate-800 bg-slate-900/50 px-3 py-1.5 text-xs">
        <span className="tabular text-slate-200">{ticksToPrice(book.lastTradePrice)}</span>
        <span className="text-[10px] uppercase tracking-wider text-slate-500">Spread</span>
        <span className="tabular text-slate-400">{spread > 0 ? ticksToPrice(spread) : '—'}</span>
      </div>

      <div className="flex flex-1 flex-col overflow-hidden">
        {bids.map((l, i) => (
          <Row key={`b-${l.price}`} level={l} cum={bidCum[i]} max={max} side="bid" />
        ))}
      </div>
    </div>
  );
}
