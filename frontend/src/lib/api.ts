import type { OrderType, Side } from '../types';

export interface NewOrder {
  side: Side;
  type: OrderType;
  quantity: number;
  price?: number;
  stopPrice?: number;
}

export interface CommandAck {
  accepted: boolean;
  orderId: number;
}

async function postJson<T>(url: string, body: unknown): Promise<T> {
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    const detail = await res.text();
    throw new Error(detail || `${res.status} ${res.statusText}`);
  }
  return res.json() as Promise<T>;
}

export function submitOrder(order: NewOrder): Promise<CommandAck> {
  return postJson<CommandAck>('/api/orders', order);
}

export async function cancelOrder(id: number): Promise<void> {
  await fetch(`/api/orders/${id}`, { method: 'DELETE' });
}

export function startSimulator(rate: number): Promise<{ running: boolean; rate: number }> {
  return postJson(`/api/sim/start?rate=${rate}`, {});
}

export function stopSimulator(): Promise<{ running: boolean }> {
  return postJson('/api/sim/stop', {});
}
