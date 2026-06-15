// 1 tick = $0.01 (see DESIGN.md). The engine speaks integer ticks; the UI shows dollars.
export const TICK_SIZE = 0.01;

export function ticksToPrice(ticks: number): string {
  if (ticks < 0) return '—';
  return (ticks * TICK_SIZE).toFixed(2);
}

export function formatQty(qty: number): string {
  return qty.toLocaleString('en-US');
}

export function formatInt(n: number): string {
  return Math.round(n).toLocaleString('en-US');
}
