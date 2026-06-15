import type { TradeMsg } from '../types';
import { formatQty, ticksToPrice } from '../lib/format';

export function TradeTape({ trades }: { trades: TradeMsg[] }) {
  return (
    <div className="flex h-full flex-col">
      <div className="grid grid-cols-3 px-3 pb-1 text-[10px] uppercase tracking-wider text-slate-500">
        <span>Price</span>
        <span className="text-right">Size</span>
        <span className="text-right">Side</span>
      </div>
      <div className="flex-1 overflow-y-auto">
        {trades.length === 0 && (
          <div className="px-3 py-2 text-xs text-slate-600">No trades yet</div>
        )}
        {trades.map((t) => {
          const buy = t.aggressorSide === 'BUY';
          return (
            <div key={t.seq} className="grid grid-cols-3 px-3 py-[2px] text-xs tabular">
              <span className={buy ? 'text-green-400' : 'text-red-400'}>{ticksToPrice(t.price)}</span>
              <span className="text-right text-slate-300">{formatQty(t.quantity)}</span>
              <span className={`text-right ${buy ? 'text-green-500' : 'text-red-500'}`}>
                {buy ? 'BUY' : 'SELL'}
              </span>
            </div>
          );
        })}
      </div>
    </div>
  );
}
