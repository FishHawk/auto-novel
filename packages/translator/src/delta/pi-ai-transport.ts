import { anthropicMessagesApi } from '@earendil-works/pi-ai/api/anthropic-messages.lazy';
import { openAICompletionsApi } from '@earendil-works/pi-ai/api/openai-completions.lazy';
import { openAIResponsesApi } from '@earendil-works/pi-ai/api/openai-responses.lazy';
import { normalizeContext } from '@earendil-works/pi-ai/utils/transcript';
import type {
  AssistantMessage,
  CacheRetention,
  FetchFunction,
  Message,
  Model,
  Tool,
} from '@earendil-works/pi-ai';
import type { PiAiProtocol } from '../translator/pi-ai-api';
import type { DeltaUsagePresence } from './types';
import { createPiAiFetch } from '../translator/pi-ai-fetch';

export type DeltaMessage = Message;
export type DeltaAssistantMessage = AssistantMessage;
export type DeltaTool = Tool;

export type { DeltaUsagePresence } from './types';

export interface PiAiSessionRequest {
  protocol: PiAiProtocol;
  endpoint: string;
  key: string;
  model: string;
  systemPrompt?: string;
  messages: DeltaMessage[];
  tools?: DeltaTool[];
  requestParams?: Record<string, unknown>;
  cacheRetention?: CacheRetention;
  sessionId?: string;
  signal?: AbortSignal;
  fetch?: FetchFunction;
  onThinking?: (delta: string) => void;
}

export interface PiAiSessionResult {
  message: DeltaAssistantMessage;
  usagePresence: DeltaUsagePresence;
}

export class PiAiSessionError extends Error {
  constructor(
    readonly protocol: PiAiProtocol,
    readonly status: number | undefined,
    message: string,
    /** Provider Retry-After converted to milliseconds. The runner owns retry policy. */
    readonly retryAfter?: number,
  ) {
    super(
      `${protocol}${status === undefined ? '' : ` HTTP ${status}`}：${message}`,
    );
    this.name = 'PiAiSessionError';
  }
}

const streams = {
  'openai-completions': openAICompletionsApi(),
  'openai-responses': openAIResponsesApi(),
  'anthropic-messages': anthropicMessagesApi(),
} as const;

/** Delta preserves the complete response for durable tool/history replay. */
export async function createPiAiSession(
  request: PiAiSessionRequest,
): Promise<PiAiSessionResult> {
  const model = createModel(request);
  const fetch = createPiAiFetch(request);
  const context = normalizeContext({
    systemPrompt: request.systemPrompt,
    messages: request.messages,
    tools: request.tools,
  });
  const usagePresence: DeltaUsagePresence = {
    input: false,
    output: false,
    cacheRead: false,
    cacheWrite: false,
  };
  let status: number | undefined;
  let retryAfter: number | undefined;
  const stream = streams[request.protocol].stream(model, context, {
    apiKey: request.key,
    signal: request.signal,
    // Delta consumes thinking deltas and final tool calls, so avoid reparsing
    // the growing tool JSON on every token.
    parsePartialToolArguments: false,
    maxRetries: 0,
    cacheRetention: request.cacheRetention ?? 'short',
    sessionId: request.sessionId,
    fetch: async (input, init) => {
      const response = await fetch(input, init);
      // Only failed HTTP responses constrain the runner’s retry policy.
      // A successful header followed by an interrupted stream is a transport failure.
      status = response.ok ? undefined : response.status;
      retryAfter = parseRetryAfter(response.headers.get('retry-after'));
      return response;
    },
    onPayload: (payload) => ({
      ...(payload as Record<string, unknown>),
      ...request.requestParams,
    }),
    onProviderStreamEvent: (event) => {
      recordUsagePresence(request.protocol, event, usagePresence);
    },
  });

  for await (const event of stream) {
    if (event.type === 'thinking_delta') request.onThinking?.(event.delta);
  }
  const message = await stream.result();
  if (message.stopReason === 'aborted' || request.signal?.aborted) {
    const error = new PiAiSessionError(
      request.protocol,
      status,
      message.errorMessage || 'Request aborted',
      retryAfter,
    );
    error.name = 'AbortError';
    throw error;
  }
  if (message.stopReason === 'error') {
    throw new PiAiSessionError(
      request.protocol,
      status,
      message.errorMessage || '模型请求失败',
      retryAfter,
    );
  }
  return { message, usagePresence };
}

function parseRetryAfter(value: string | null): number | undefined {
  if (!value) return undefined;
  const seconds = Number(value);
  if (Number.isFinite(seconds)) return Math.max(0, seconds * 1000);
  const date = Date.parse(value);
  return Number.isNaN(date) ? undefined : Math.max(0, date - Date.now());
}

// Raw provider fields establish presence; normalized pi-ai usage fills missing fields with zero.
function recordUsagePresence(
  protocol: PiAiProtocol,
  event: unknown,
  presence: DeltaUsagePresence,
) {
  const data = event as {
    usage?: Record<string, unknown>;
    choices?: { usage?: Record<string, unknown> }[];
    message?: { usage?: Record<string, unknown> };
    response?: { usage?: Record<string, unknown> };
  };
  const usage =
    protocol === 'openai-completions'
      ? data.usage ?? data.choices?.[0]?.usage
      : protocol === 'openai-responses'
        ? data.response?.usage
        : data.message?.usage ?? data.usage;
  if (!usage) return;
  const reported = (value: unknown) => typeof value === 'number';
  if (protocol === 'anthropic-messages') {
    presence.input ||= reported(usage.input_tokens);
    presence.output ||= reported(usage.output_tokens);
    presence.cacheRead ||= reported(usage.cache_read_input_tokens);
    presence.cacheWrite ||= reported(usage.cache_creation_input_tokens);
  } else {
    const chat = protocol === 'openai-completions';
    const details = (
      chat ? usage.prompt_tokens_details : usage.input_tokens_details
    ) as Record<string, unknown> | undefined;
    presence.input ||= reported(
      chat ? usage.prompt_tokens : usage.input_tokens,
    );
    presence.output ||= reported(
      chat ? usage.completion_tokens : usage.output_tokens,
    );
    presence.cacheRead ||=
      reported(details?.cached_tokens) ||
      (chat &&
        (reported(usage.prompt_cache_hit_tokens) ||
          reported(usage.cached_tokens)));
    presence.cacheWrite ||= reported(details?.cache_write_tokens);
  }
}

function createModel(request: PiAiSessionRequest): Model<PiAiProtocol> {
  const { protocol } = request;
  return {
    id: request.model,
    name: request.model,
    api: protocol,
    provider: 'auto-novel',
    baseUrl: normalizeEndpoint(request.endpoint, protocol),
    input: ['text'],
    cost: { input: 0, output: 0, cacheRead: 0, cacheWrite: 0 },
    reasoning: false,
    contextWindow: 0,
    maxTokens: 16_384,
    compat:
      protocol === 'openai-completions'
        ? {
            supportsStore: false,
            supportsDeveloperRole: false,
            supportsReasoningEffort: true,
            supportsUsageInStreaming: true,
            supportsLongCacheRetention: false,
            thinkingFormat: 'openai',
          }
        : protocol === 'openai-responses'
          ? {
              supportsDeveloperRole: false,
              supportsLongCacheRetention: false,
            }
          : undefined,
  };
}

// Keep the legacy text-only bridge unchanged, including its independent caching policy.
function normalizeEndpoint(endpoint: string, protocol: PiAiProtocol): string {
  const url = new URL(endpoint);
  let pathname = url.pathname.replace(/\/+$/, '');
  if (protocol === 'anthropic-messages') {
    pathname = pathname
      .replace(/\/(?:v1\/)?messages$/, '')
      .replace(/\/v1$/, '');
  } else {
    pathname = pathname.replace(/\/(?:chat\/completions|responses)$/, '');
    if (!pathname || pathname === '/') pathname = '/v1';
  }
  url.pathname = pathname || '/';
  return url.toString().replace(/\/$/, '');
}
