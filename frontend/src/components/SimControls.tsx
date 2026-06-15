import { useEffect, useState } from 'react';
import { getSimStatus, startSimulator, stopSimulator } from '../lib/api';
import { formatInt } from '../lib/format';

export function SimControls() {
  const [rate, setRate] = useState(3000);
  const [running, setRunning] = useState(false);

  // Sync with the server on mount so the panel reflects an already-running sim.
  useEffect(() => {
    getSimStatus()
      .then((s) => {
        setRunning(s.running);
        if (s.running && s.rate > 0) setRate(s.rate);
      })
      .catch(() => {});
  }, []);

  const start = async () => {
    const s = await startSimulator(rate);
    setRunning(true);
    setRate(s.rate);
  };
  const stop = async () => {
    await stopSimulator();
    setRunning(false);
  };

  return (
    <div className="flex flex-col gap-2 p-3">
      <div className="flex items-center justify-between">
        <span className="text-[10px] uppercase tracking-wider text-slate-500">Rate</span>
        <span className="tabular text-xs text-slate-400">{formatInt(rate)} ord/s</span>
      </div>
      <input
        type="range"
        min={100}
        max={100000}
        step={100}
        value={rate}
        onChange={(e) => {
          const r = Number(e.target.value);
          setRate(r);
          if (running) startSimulator(r);
        }}
        className="w-full accent-sky-500"
      />
      <div className="flex gap-2">
        <button
          onClick={start}
          disabled={running}
          className="flex-1 rounded bg-sky-600 py-1.5 text-xs font-semibold text-white hover:bg-sky-500 disabled:opacity-40"
        >
          {running ? 'Running' : 'Start'}
        </button>
        <button
          onClick={stop}
          disabled={!running}
          className="flex-1 rounded bg-slate-700 py-1.5 text-xs font-semibold text-slate-200 hover:bg-slate-600 disabled:opacity-40"
        >
          Stop
        </button>
      </div>
    </div>
  );
}
