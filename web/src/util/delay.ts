const delay = (ms: number, signal?: AbortSignal) =>
  new Promise<void>((resolve, reject) => {
    let timeout: number;
    const abortHandler = () => {
      clearTimeout(timeout);
      reject(new DOMException('Aborted', 'AbortError'));
    };
    timeout = window.setTimeout(() => {
      resolve();
      signal?.removeEventListener('abort', abortHandler);
    }, ms);
    signal?.addEventListener('abort', abortHandler);
  });
