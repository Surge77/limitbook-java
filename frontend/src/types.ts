export type Side = 'BUY' | 'SELL';

export type OrderType =
  | 'LIMIT'
  | 'MARKET'
  | 'IOC'
  | 'FOK'
  | 'POST_ONLY'
  | 'STOP'
  | 'STOP_LIMIT';

export interface BookLevel {
  price: number;
  quantity: number;
  orderCount: number;
}

export interface BookSnapshot {
  bids: BookLevel[];
  asks: BookLevel[];
  lastTradePrice: number;
  sequence: number;
}

export interface TradeMsg {
  seq: number;
  price: number;
  quantity: number;
  takerOrderId: number;
  makerOrderId: number;
  takerAccountId: number;
  makerAccountId: number;
  aggressorSide: Side;
}

export interface Metrics {
  ordersPerSec: number;
  tradesPerSec: number;
  p50Us: number;
  p99Us: number;
  spread: number;
  ordersTotal: number;
  tradesTotal: number;
}

export interface MarketFrame {
  type: 'tick';
  book: BookSnapshot;
  trades: TradeMsg[];
  metrics: Metrics;
}
