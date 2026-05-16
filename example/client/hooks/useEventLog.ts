import { useCallback, useState } from "react";

const MAX_LINES = 40;

/**
 * Time-stamped append-only log used to visualise lib event flow.
 * Capped at {@link MAX_LINES} most-recent lines.
 */
export function useEventLog() {
  const [lines, setLines] = useState<string[]>([]);

  const append = useCallback((line: string) => {
    const ts = new Date().toISOString().slice(11, 19);
    setLines((prev) => [`${ts}  ${line}`, ...prev].slice(0, MAX_LINES));
  }, []);

  const clear = useCallback(() => setLines([]), []);

  return { lines, append, clear };
}
