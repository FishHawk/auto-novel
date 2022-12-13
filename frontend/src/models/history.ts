export interface SearchHistory {
  url: string;
  title: string;
}

export interface LocalBoostProgress {
  total: number | undefined;
  finished: number;
  error: number;
}

export function getHistories(): SearchHistory[] {
  const historiesRaw = localStorage.getItem('histories');
  if (historiesRaw) {
    try {
      return JSON.parse(historiesRaw);
    } catch (e) {
      localStorage.removeItem('histories');
    }
  }
  return [];
}

export function addHistory(history: SearchHistory) {
  const histories = getHistories().filter((item) => item.url != history.url);
  histories.unshift(history);

  const histories_length_limit = 10;
  if (histories.length > histories_length_limit)
    histories.length = histories_length_limit;

  const parsed = JSON.stringify(histories);
  localStorage.setItem('histories', parsed);
}
