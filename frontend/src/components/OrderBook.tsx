import type { BookLevel, BookSnapshot } from '../types';
import { formatQty, ticksToPrice } from '../lib/format';

const DEPTH = 12;

interface GridRow {
  price: number;
  quantity: number;
  cum: number;
}

// Build a contiguous tick grid anchored on the best price so rows hold their
// position frame-to-frame (empty ticks render as gaps) instead of jumping.
function buildGrid(levels: BookLevel[], side: 'bid' | 'ask'): GridRow[] {
  if (levels.length === 0) return [];
  const byPrice = new Map<number, number>();
  for (const l of levels) byPrice.set(l.price, l.quantity);
  const best = levels[0].price;

  const rows: GridRow[] = [];
  let cum = 0;
  for (let i = 0; i < DEPTH; i++) {
    const price = side === 'ask' ? best + i : best - i;
    const quantity = byPrice.get(price) ?? 0;
    cum += quantity;
    rows.push({ price, quantity, cum });
  }
  return rows;
}

interface RowProps {
  row: GridRow;
  max: number;
  side: 'bid' | 'ask';
}

function Row({ row, max, side }: RowProps) {
  const pct = max > 0 ? (row.cum / max) * 100 : 0;
  const barColor = side === 'ask' ? 'bg-red-500/15' : 'bg-green-500/15';
  const priceColor = side === 'ask' ? 'text-red-400' : 'text-green-400';
  const empty = row.quantity === 0;
  return (
    <div className="relative grid grid-cols-3 px-3 py-[3px] text-xs tabular">
      <div className={`absolute inset-y-0 right-0 ${barColor}`} style={{ width: `${pct}%` }} />
      <span className={`relative z-10 ${priceColor}`}>{ticksToPrice(row.price)}</span>
      <span className="relative z-10 text-right text-slate-300">{empty ? '·' : formatQty(row.quantity)}</span>
      <span className="relative z-10 text-right text-slate-500">{empty ? '' : formatQty(row.cum)}</span>
    </div>
  );
}

export function OrderBook({ book }: { book: BookSnapshot }) {
  const asks = buildGrid(book.asks, 'ask');
  const bids = buildGrid(book.bids, 'bid');
  const max = Math.max(1, asks.at(-1)?.cum ?? 0, bids.at(-1)?.cum ?? 0);

  const bestBid = book.bids[0]?.price ?? -1;
  const bestAsk = book.asks[0]?.price ?? -1;
  const spread = bestBid > 0 && bestAsk > 0 ? bestAsk - bestBid : -1;

  return (
    <div className="flex h-full flex-col">
      <div className="grid grid-cols-3 px-3 pb-1 text-[10px] uppercase tracking-wider text-slate-500">
        <span>Price</span>
        <span className="text-right">Size</span>
        <span className="text-right">Total</span>
      </div>

      <div className="flex flex-1 flex-col justify-end overflow-hidden">
        {[...asks].reverse().map((row) => (
          <Row key={`a-${row.price}`} row={row} max={max} side="ask" />
        ))}
      </div>

      <div className="flex items-center justify-between border-y border-slate-800 bg-slate-900/50 px-3 py-1.5 text-xs">
        <span className="tabular text-slate-200">{ticksToPrice(book.lastTradePrice)}</span>
        <span className="text-[10px] uppercase tracking-wider text-slate-500">Spread</span>
        <span className="tabular text-slate-400">{spread > 0 ? ticksToPrice(spread) : '—'}</span>
      </div>

      <div className="flex flex-1 flex-col overflow-hidden">
        {bids.map((row) => (
          <Row key={`b-${row.price}`} row={row} max={max} side="bid" />
        ))}
      </div>
    </div>
  );
}
