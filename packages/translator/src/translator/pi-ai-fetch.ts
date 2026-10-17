import type { FetchFunction } from '@earendil-works/pi-ai';
import type { PiAiCompletionRequest } from './pi-ai-api';

export function createPiAiFetch(
  request: Pick<
    PiAiCompletionRequest,
    'protocol' | 'endpoint' | 'key' | 'fetch'
  >,
): FetchFunction {
  const fetch = request.fetch ?? globalThis.fetch;
  const isOpenRouterMessages =
    request.protocol === 'anthropic-messages' &&
    new URL(request.endpoint).hostname === 'openrouter.ai';

  return (input, init) => {
    const headers = new Headers(
      init?.headers ?? (input instanceof Request ? input.headers : undefined),
    );
    // SDK metadata is not part of the API protocol and can fail gateway CORS.
    const sdkHeaders: string[] = [];
    headers.forEach((_, name) => {
      if (name.startsWith('x-stainless-') || name === 'user-agent') {
        sdkHeaders.push(name);
      }
    });
    for (const name of sdkHeaders) headers.delete(name);

    if (isOpenRouterMessages) {
      // OpenRouter requires Bearer auth and does not allow these Anthropic headers.
      headers.set('authorization', `Bearer ${request.key}`);
      headers.delete('x-api-key');
      headers.delete('anthropic-version');
      headers.delete('anthropic-dangerous-direct-browser-access');
      headers.delete('anthropic-beta');
    }

    return fetch(input, { ...init, headers });
  };
}
