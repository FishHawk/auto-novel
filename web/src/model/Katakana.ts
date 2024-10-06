export interface KataKanaConfig {
  mode: 'traditional' | 'ai';
  aiTranslationType: 'openai' | 'local';
  api_key: 'sk-no-key-required';
  base_url: 'http://127.0.0.1:8080';
  model_name: 'glm-4-9b-chat';
  max_workers: 4;
  request_timeout: 120;
  translate_surface_mode: 1;
  translate_context_mode: 1;
  history: VolumeHistory[];
}

export interface VolumeHistory {
  source: 'tmp' | 'local';
  filename: string;
  katakanas: Map<string, KataKanaInfo>;
  date: number;
}

export interface KataKanaInfo {
  wordTranslations: {
    translations: string[];
    translationNotes: string;
  };
  count: number;
  intelligentSummary: string;
  gender: string;
  summary: string;
  originalContextText: string;
  translatedContextText: string;
}
