import { Options } from 'ky';
import { KyInstance } from 'ky/distribution/types/ky';

export class LlamaApi {
  client: KyInstance;

  constructor(client: KyInstance, endpoint: string) {
    this.client = client.create({
      prefixUrl: endpoint,
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
      },
      timeout: 600_000,
    });
  }

  createCompletion = (json: LlamaCompletion, options?: Options) =>
    this.client
      .post('completion', { json, ...options })
      .json<LlamaCompletionResponse>();
}

interface LlamaCompletion {
  prompt: string;
  temperature: number;
  top_k: number;
  top_p: number;
  repeat_penalty: number;
  frequency_penalty: number;
}

interface LlamaCompletionResponse {
  content: string;
  model: string;
  stopped_eos: boolean;
  stopped_limit: boolean;
  truncated: boolean;
}
