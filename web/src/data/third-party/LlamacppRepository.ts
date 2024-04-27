import ky, { Options } from 'ky';

export const createLlamacppRepository = (endpoint: string) => {
  const client = ky.create({
    prefixUrl: endpoint,
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    timeout: 600_000,
    retry: 0,
  });

  const createCompletion = (json: LlamaCompletion, options?: Options) =>
    client
      .post('completion', { json, ...options })
      .json<LlamaCompletionResponse>();

  return {
    createCompletion,
  };
};

interface LlamaCompletion {
  prompt: string;
  n_predict?: number;
  temperature?: number;
  top_k?: number;
  top_p?: number;
  repeat_penalty?: number;
  frequency_penalty?: number;
  n_probs?: number;
  min_keep?: number;
  seed?: number;
}

interface LlamaCompletionResponse {
  completion_probabilities?: Array<{
    content: string;
    probs: Array<{
      prob: number;
      tok_str: string;
    }>;
  }>;
  content: string;
  model: string;
  prompt: string;
  generation_settings: object;
  stopped_eos: boolean;
  stopped_limit: boolean;
  truncated: boolean;
}
