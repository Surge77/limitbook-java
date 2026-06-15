import { useMemo } from 'react';
import type { BookLevel, BookSnapshot } from '../types';
import { ticksToPrice } from '../lib/format';

const W = 100;
const H = 100;

interface Pt {
  x: number;
  y: number;
}

function buildSide(levels: BookLevel[], minP: number, maxP: number, maxCum: number): Pt[] {
  if (maxP === minP || maxCum === 0) return [];
  const pts: Pt[] = [];
  let cum = 0;
  for (const l of levels) {
    cum += l.quantity;
    const x = ((l.price - minP) / (maxP - minP)) * W;
    const y = H - (cum / maxCum) * H;
    pts.push({ x, y });
  }
  return pts;
}

function areaPath(pts: Pt[], floorX: number): string {
  if (pts.length === 0) return '';
  const line = pts.map((p, i) => `${i === 0 ? 'M' : 'L'}${p.x.toFixed(2)},${p.y.toFixed(2)}`).join(' ');
  const last = pts[pts.length - 1];
  const first = pts[0];
  return `${line} L${last.x.toFixed(2)},${H} L${floorX.toFixed(2)},${H} L${first.x.toFixed(2)},${H} Z`;
}

export function DepthChart({ book }: { book: BookSnapshot }) {
  const { bidPath, askPath, midX, minP, maxP } = useMemo(() => {
    const bids = book.bids;
    const asks = book.asks;
    if (bids.length === 0 || asks.length === 0) {
      return { bidPath: '', askPath: '', midX: 50, minP: -1, maxP: -1 };
    }
    const minP = bids[bids.length - 1].price;
    const maxP = asks[asks.length - 1].price;
    const bidCumMax = bids.reduce((s, l) => s + l.quantity, 0);
    const askCumMax = asks.reduce((s, l) => s + l.quantity, 0);
    const maxCum = Math.max(bidCumMax, askCumMax, 1);

    const bidPts = buildSide(bids, minP, maxP, maxCum);
    const askPts = buildSide(asks, minP, maxP, maxCum);
    const mid = ((book.bids[0].price + book.asks[0].price) / 2 - minP) / (maxP - minP) * W;
    return {
      bidPath: areaPath(bidPts, bidPts[0]?.x ?? 0),
      askPath: areaPath(askPts, askPts[0]?.x ?? 0),
      midX: mid,
      minP,
      maxP,
    };
  }, [book]);

  return (
    <div className="flex h-full flex-col">
      <div className="px-3 pb-1 text-[10px] uppercase tracking-wider text-slate-500">Depth</div>
      <div className="relative flex-1">
        <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" className="h-full w-full">
          <path d={bidPath} fill="rgba(34,197,94,0.25)" stroke="rgb(34,197,94)" strokeWidth="0.6" />
          <path d={askPath} fill="rgba(239,68,68,0.25)" stroke="rgb(239,68,68)" strokeWidth="0.6" />
          {minP > 0 && (
            <line x1={midX} y1="0" x2={midX} y2={H} stroke="rgba(148,163,184,0.4)" strokeWidth="0.4" strokeDasharray="2 2" />
          )}
        </svg>
        <div className="pointer-events-none absolute inset-x-0 bottom-0 flex justify-between px-2 text-[9px] tabular text-slate-600">
          <span>{minP > 0 ? ticksToPrice(minP) : ''}</span>
          <span>{maxP > 0 ? ticksToPrice(maxP) : ''}</span>
        </div>
      </div>
    </div>
  );
}
