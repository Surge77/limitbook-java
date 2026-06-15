import type { Metrics } from '../types';
import { formatInt, ticksToPrice } from '../lib/format';

function Stat({ label, value, accent }: { label: string; value: string; accent?: string }) {
  return (
    <div className="flex flex-col">
      <span className="text-[10px] uppercase tracking-wider text-slate-500">{label}</span>
      <span className={`tabular text-sm ${accent ?? 'text-slate-200'}`}>{value}</span>
    </div>
  );
}

export function MetricsPanel({ metrics }: { metrics: Metrics }) {
  return (
    <div className="grid grid-cols-3 gap-3 p-3">
      <Stat label="Orders/s" value={formatInt(metrics.ordersPerSec)} accent="text-sky-400" />
      <Stat label="Trades/s" value={formatInt(metrics.tradesPerSec)} accent="text-amber-400" />
      <Stat label="Spread" value={metrics.spread > 0 ? ticksToPrice(metrics.spread) : '—'} />
      <Stat label="p50 latency" value={`${metrics.p50Us.toFixed(2)} µs`} accent="text-green-400" />
      <Stat label="p99 latency" value={`${metrics.p99Us.toFixed(2)} µs`} accent="text-green-400" />
      <Stat label="Trades total" value={formatInt(metrics.tradesTotal)} />
    </div>
  );
}
