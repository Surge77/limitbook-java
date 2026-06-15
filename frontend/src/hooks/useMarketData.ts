import { useEffect, useRef, useState } from 'react';
import type { MarketFrame } from '../types';

const MAX_BACKOFF_MS = 10_000;
const BASE_BACKOFF_MS = 500;

export type ConnectionStatus = 'connecting' | 'open' | 'closed';

/**
 * Subscribes to the gateway market-data WebSocket with exponential-backoff
 * reconnect. Returns the latest coalesced frame plus connection status. The
 * `isClosed` ref prevents a reconnect loop after intentional unmount.
 */
export function useMarketData(url: string) {
  const [frame, setFrame] = useState<MarketFrame | null>(null);
  const [status, setStatus] = useState<ConnectionStatus>('connecting');
  const wsRef = useRef<WebSocket | null>(null);
  const backoff = useRef(BASE_BACKOFF_MS);
  const isClosed = useRef(false);

  useEffect(() => {
    isClosed.current = false;

    const connect = () => {
      if (isClosed.current) return;
      setStatus('connecting');
      const ws = new WebSocket(url);
      wsRef.current = ws;

      ws.onopen = () => {
        backoff.current = BASE_BACKOFF_MS;
        setStatus('open');
      };
      ws.onmessage = (event) => {
        try {
          setFrame(JSON.parse(event.data) as MarketFrame);
        } catch {
          // ignore malformed frame
        }
      };
      ws.onclose = () => {
        if (isClosed.current) return;
        setStatus('closed');
        backoff.current = Math.min(backoff.current * 2, MAX_BACKOFF_MS);
        setTimeout(connect, backoff.current);
      };
      ws.onerror = () => ws.close();
    };

    connect();
    return () => {
      isClosed.current = true;
      wsRef.current?.close();
    };
  }, [url]);

  return { frame, status };
}
