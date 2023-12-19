export function* parseEventStream<T>(text: string) {
  for (const line of text.split('\n')) {
    if (line == '[DONE]') {
      return;
    } else if (!line.trim() || line.startsWith(': ping')) {
      continue;
    } else {
      try {
        const obj: T = JSON.parse(line.replace(/^data\:/, '').trim());
        yield obj;
      } catch {
        continue;
      }
    }
  }
}

export const safeJson = (text: string) => {
  try {
    return JSON.parse(text);
  } catch (err) {
    return undefined;
  }
};
