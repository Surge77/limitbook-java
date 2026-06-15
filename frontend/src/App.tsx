import { useMarketData } from './hooks/useMarketData';
import { OrderBook } from './components/OrderBook';
import { TradeTape } from './components/TradeTape';
import { DepthChart } from './components/DepthChart';
import { PriceChart } from './components/PriceChart';
import { OrderEntry } from './components/OrderEntry';
import { MetricsPanel } from './components/MetricsPanel';
import { SimControls } from './components/SimControls';
import type { BookSnapshot, Metrics } from './types';

const EMPTY_BOOK: BookSnapshot = { bids: [], asks: [], lastTradePrice: 0, sequence: 0 };
const EMPTY_METRICS: Metrics = {
  ordersPerSec: 0,
  tradesPerSec: 0,
  p50Us: 0,
  p99Us: 0,
  spread: -1,
  ordersTotal: 0,
  tradesTotal: 0,
};

const WS_URL = `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws`;

function Card({
  title,
  children,
  className = '',
}: {
  title?: string;
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <div className={`flex flex-col overflow-hidden rounded-lg border border-slate-800 bg-slate-900/40 ${className}`}>
      {title && (
        <div className="border-b border-slate-800 px-3 py-2 text-xs font-semibold tracking-wide text-slate-300">
          {title}
        </div>
      )}
      <div className="min-h-0 flex-1">{children}</div>
    </div>
  );
}

export default function App() {
  const { frame, status } = useMarketData(WS_URL);
  const book = frame?.book ?? EMPTY_BOOK;
  const trades = frame?.trades ?? [];
  const metrics = frame?.metrics ?? EMPTY_METRICS;

  const dot = status === 'open' ? 'bg-green-500' : status === 'connecting' ? 'bg-amber-500' : 'bg-red-500';

  return (
    <div className="flex h-screen flex-col gap-3 p-3">
      <header className="flex items-center justify-between">
        <div className="flex items-baseline gap-3">
          <h1 className="text-lg font-bold text-slate-100">LimitBook</h1>
          <span className="text-xs text-slate-500">order-matching engine · live</span>
        </div>
        <div className="flex items-center gap-2 text-xs text-slate-400">
          <span className={`h-2 w-2 rounded-full ${dot}`} />
          {status}
        </div>
      </header>

      <div className="grid min-h-0 flex-1 grid-cols-[320px_1fr_300px] gap-3">
        <Card title="Order Book">
          <OrderBook book={book} />
        </Card>

        <div className="grid min-h-0 grid-rows-[1fr_1fr_auto] gap-3">
          <Card>
            <PriceChart lastTradePrice={book.lastTradePrice} />
          </Card>
          <Card>
            <DepthChart book={book} />
          </Card>
          <Card title="Metrics">
            <MetricsPanel metrics={metrics} />
          </Card>
        </div>

        <div className="grid min-h-0 grid-rows-[auto_auto_1fr] gap-3">
          <Card title="Order Entry">
            <OrderEntry />
          </Card>
          <Card title="Simulator">
            <SimControls />
          </Card>
          <Card title="Trades">
            <TradeTape trades={trades} />
          </Card>
        </div>
      </div>
    </div>
  );
}
