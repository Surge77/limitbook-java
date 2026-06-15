import { useState } from 'react';
import type { OrderType, Side } from '../types';
import { cancelOrder, submitOrder } from '../lib/api';
import { TICK_SIZE } from '../lib/format';

const ORDER_TYPES: OrderType[] = ['LIMIT', 'MARKET', 'IOC', 'FOK', 'POST_ONLY'];

export function OrderEntry() {
  const [side, setSide] = useState<Side>('BUY');
  const [type, setType] = useState<OrderType>('LIMIT');
  const [price, setPrice] = useState('100.00');
  const [qty, setQty] = useState('10');
  const [status, setStatus] = useState<string | null>(null);
  const [cancelId, setCancelId] = useState('');

  const priced = type !== 'MARKET';

  const submit = async () => {
    try {
      const order = {
        side,
        type,
        quantity: Number(qty),
        price: priced ? Math.round(Number(price) / TICK_SIZE) : undefined,
      };
      const ack = await submitOrder(order);
      setStatus(`✓ ${side} ${type} accepted — id ${ack.orderId}`);
    } catch (e) {
      setStatus(`✕ ${(e as Error).message}`);
    }
  };

  const cancel = async () => {
    if (!cancelId) return;
    await cancelOrder(Number(cancelId));
    setStatus(`✓ cancel sent — id ${cancelId}`);
    setCancelId('');
  };

  const tabBtn = (active: boolean, color: string) =>
    `flex-1 rounded py-1.5 text-xs font-semibold transition ${
      active ? color : 'bg-slate-800 text-slate-400 hover:bg-slate-700'
    }`;

  return (
    <div className="flex flex-col gap-2 p-3">
      <div className="flex gap-2">
        <button className={tabBtn(side === 'BUY', 'bg-green-600 text-white')} onClick={() => setSide('BUY')}>
          BUY
        </button>
        <button className={tabBtn(side === 'SELL', 'bg-red-600 text-white')} onClick={() => setSide('SELL')}>
          SELL
        </button>
      </div>

      <select
        value={type}
        onChange={(e) => setType(e.target.value as OrderType)}
        className="rounded bg-slate-800 px-2 py-1.5 text-xs text-slate-200"
      >
        {ORDER_TYPES.map((t) => (
          <option key={t} value={t}>
            {t}
          </option>
        ))}
      </select>

      <label className="text-[10px] uppercase tracking-wider text-slate-500">Price</label>
      <input
        value={price}
        onChange={(e) => setPrice(e.target.value)}
        disabled={!priced}
        className="tabular rounded bg-slate-800 px-2 py-1.5 text-xs text-slate-200 disabled:opacity-40"
      />

      <label className="text-[10px] uppercase tracking-wider text-slate-500">Quantity</label>
      <input
        value={qty}
        onChange={(e) => setQty(e.target.value)}
        className="tabular rounded bg-slate-800 px-2 py-1.5 text-xs text-slate-200"
      />

      <button
        onClick={submit}
        className={`mt-1 rounded py-2 text-sm font-bold text-white ${
          side === 'BUY' ? 'bg-green-600 hover:bg-green-500' : 'bg-red-600 hover:bg-red-500'
        }`}
      >
        Submit {side}
      </button>

      <div className="mt-1 flex gap-2">
        <input
          value={cancelId}
          onChange={(e) => setCancelId(e.target.value)}
          placeholder="order id"
          className="tabular w-full rounded bg-slate-800 px-2 py-1.5 text-xs text-slate-200"
        />
        <button onClick={cancel} className="rounded bg-slate-700 px-3 text-xs text-slate-200 hover:bg-slate-600">
          Cancel
        </button>
      </div>

      {status && <div className="tabular mt-1 text-[11px] text-slate-400">{status}</div>}
    </div>
  );
}
