import { useEffect, useRef } from 'react';
import {
  AreaSeries,
  createChart,
  type IChartApi,
  type ISeriesApi,
  type UTCTimestamp,
} from 'lightweight-charts';
import { TICK_SIZE } from '../lib/format';

export function PriceChart({ lastTradePrice }: { lastTradePrice: number }) {
  const containerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<IChartApi | null>(null);
  const seriesRef = useRef<ISeriesApi<'Area'> | null>(null);

  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;
    const chart = createChart(el, {
      layout: { background: { color: 'transparent' }, textColor: '#64748b', fontSize: 10 },
      grid: { vertLines: { visible: false }, horzLines: { color: 'rgba(148,163,184,0.08)' } },
      rightPriceScale: { borderColor: 'rgba(148,163,184,0.15)' },
      timeScale: { borderColor: 'rgba(148,163,184,0.15)', timeVisible: true, secondsVisible: true },
      handleScroll: false,
      handleScale: false,
    });
    const series = chart.addSeries(AreaSeries, {
      lineColor: '#38bdf8',
      topColor: 'rgba(56,189,248,0.3)',
      bottomColor: 'rgba(56,189,248,0.02)',
      lineWidth: 2,
      priceFormat: { type: 'price', precision: 2, minMove: 0.01 },
    });
    chartRef.current = chart;
    seriesRef.current = series;

    const resize = () => chart.applyOptions({ width: el.clientWidth, height: el.clientHeight });
    resize();
    const observer = new ResizeObserver(resize);
    observer.observe(el);
    return () => {
      observer.disconnect();
      chart.remove();
    };
  }, []);

  useEffect(() => {
    if (lastTradePrice <= 0 || !seriesRef.current) return;
    const time = Math.floor(Date.now() / 1000) as UTCTimestamp;
    seriesRef.current.update({ time, value: lastTradePrice * TICK_SIZE });
  }, [lastTradePrice]);

  return (
    <div className="flex h-full flex-col">
      <div className="px-3 pb-1 text-[10px] uppercase tracking-wider text-slate-500">Last Price</div>
      <div ref={containerRef} className="flex-1" />
    </div>
  );
}
