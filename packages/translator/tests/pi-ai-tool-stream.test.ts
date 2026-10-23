import { anthropicMessagesApi } from '@earendil-works/pi-ai/api/anthropic-messages.lazy';
import { openAICompletionsApi } from '@earendil-works/pi-ai/api/openai-completions.lazy';
import { openAIResponsesApi } from '@earendil-works/pi-ai/api/openai-responses.lazy';
import type { Model, StreamOptions, Tool } from '@earendil-works/pi-ai';
import { describe, expect, it } from 'vitest';

import { createPiAiSession } from '../src/delta/pi-ai-transport';
import type { PiAiProtocol } from '../src/translator/pi-ai-api';

const providers = {
  'openai-completions': openAICompletionsApi(),
  'openai-responses': openAIResponsesApi(),
  'anthropic-messages': anthropicMessagesApi(),
};
const protocols = Object.keys(providers) as PiAiProtocol[];
const tool: Tool = {
  name: 'translate',
  description: 'Return translated lines',
  parameters: { type: 'object', properties: { text: { type: 'string' } } },
};
const argumentsJson = ['{"text":"first"}', '{"text":"second"}'];

function model(api: PiAiProtocol): Model<PiAiProtocol> {
  return {
    id: 'test-model',
    name: 'test-model',
    api,
    provider: 'auto-novel',
    baseUrl: 'https://example.test/v1',
    input: ['text'],
    reasoning: false,
    cost: { input: 0, output: 0, cacheRead: 0, cacheWrite: 0 },
    contextWindow: 32768,
    maxTokens: 1024,
  };
}

function events(protocol: PiAiProtocol): Record<string, unknown>[] {
  if (protocol === 'openai-completions') {
    const chunk = (delta: unknown, finish_reason: string | null = null) => ({
      id: 'chat-1',
      choices: [{ index: 0, delta, finish_reason }],
    });
    return [
      chunk({ reasoning_content: 'thinking' }),
      ...argumentsJson.flatMap((args, index) => [
        chunk({
          tool_calls: [
            {
              index,
              id: `call-${index}`,
              type: 'function',
              function: { name: 'translate', arguments: args.slice(0, 10) },
            },
          ],
        }),
        chunk({
          tool_calls: [{ index, function: { arguments: args.slice(10) } }],
        }),
      ]),
      {
        ...chunk({}, 'tool_calls'),
        usage: { prompt_tokens: 7, completion_tokens: 11, total_tokens: 18 },
      },
    ];
  }
  if (protocol === 'anthropic-messages') {
    return [
      {
        type: 'message_start',
        message: {
          id: 'msg-1',
          type: 'message',
          role: 'assistant',
          content: [],
          model: 'test-model',
          stop_reason: null,
          usage: { input_tokens: 7, output_tokens: 0 },
        },
      },
      {
        type: 'content_block_start',
        index: 0,
        content_block: { type: 'thinking', thinking: '', signature: '' },
      },
      {
        type: 'content_block_delta',
        index: 0,
        delta: { type: 'thinking_delta', thinking: 'thinking' },
      },
      { type: 'content_block_stop', index: 0 },
      ...argumentsJson.flatMap((args, index) => [
        {
          type: 'content_block_start',
          index: index + 1,
          content_block: {
            type: 'tool_use',
            id: `call-${index}`,
            name: 'translate',
            input: {},
          },
        },
        {
          type: 'content_block_delta',
          index: index + 1,
          delta: { type: 'input_json_delta', partial_json: args.slice(0, 10) },
        },
        {
          type: 'content_block_delta',
          index: index + 1,
          delta: { type: 'input_json_delta', partial_json: args.slice(10) },
        },
        { type: 'content_block_stop', index: index + 1 },
      ]),
      {
        type: 'message_delta',
        delta: { stop_reason: 'tool_use' },
        usage: { output_tokens: 11 },
      },
      { type: 'message_stop' },
    ];
  }
  const item = (index: number, args: string) => ({
    type: 'function_call',
    id: `fc-${index}`,
    call_id: `call-${index}`,
    name: 'translate',
    arguments: args,
  });
  return [
    {
      type: 'response.output_item.added',
      output_index: 0,
      item: { type: 'reasoning', id: 'r-1', summary: [] },
    },
    {
      type: 'response.reasoning_summary_text.delta',
      output_index: 0,
      summary_index: 0,
      delta: 'thinking',
    },
    {
      type: 'response.output_item.done',
      output_index: 0,
      item: {
        type: 'reasoning',
        id: 'r-1',
        summary: [{ type: 'summary_text', text: 'thinking' }],
      },
    },
    ...argumentsJson.flatMap((args, index) => [
      {
        type: 'response.output_item.added',
        output_index: index + 1,
        item: item(index, ''),
      },
      {
        type: 'response.function_call_arguments.delta',
        output_index: index + 1,
        delta: args.slice(0, 10),
      },
      {
        type: 'response.function_call_arguments.delta',
        output_index: index + 1,
        delta: args.slice(10),
      },
      {
        type: 'response.function_call_arguments.done',
        output_index: index + 1,
        arguments: args,
      },
      {
        type: 'response.output_item.done',
        output_index: index + 1,
        item: item(index, args),
      },
    ]),
    {
      type: 'response.completed',
      response: {
        id: 'resp-1',
        status: 'completed',
        usage: { input_tokens: 7, output_tokens: 11, total_tokens: 18 },
      },
    },
  ];
}

function fetchEvents(
  protocol: PiAiProtocol,
  data = events(protocol),
): typeof fetch {
  return async () =>
    new Response(
      new ReadableStream({
        pull(controller) {
          const event = data.shift();
          if (!event) return controller.close();
          const frame = `${protocol === 'openai-completions' ? '' : `event: ${event.type}\n`}data: ${JSON.stringify(event)}\n\n`;
          controller.enqueue(new TextEncoder().encode(frame));
        },
      }),
      { headers: { 'content-type': 'text/event-stream' } },
    );
}

async function collect(
  protocol: PiAiProtocol,
  options: StreamOptions = {},
  data = events(protocol),
) {
  const stream = providers[protocol].stream(
    model(protocol),
    {
      messages: [{ role: 'user', content: 'source', timestamp: 0 }],
      tools: [tool],
    },
    {
      apiKey: 'test-key',
      maxRetries: 0,
      fetch: fetchEvents(protocol, data),
      ...options,
    },
  );
  const partialArguments: unknown[] = [];
  const endedArguments: unknown[] = [];
  let thinking = '';
  for await (const event of stream) {
    if (event.type === 'toolcall_delta') {
      const block = event.partial.content[event.contentIndex];
      if (block.type === 'toolCall')
        partialArguments.push(structuredClone(block.arguments));
    }
    if (event.type === 'toolcall_end')
      endedArguments.push(structuredClone(event.toolCall.arguments));
    if (event.type === 'thinking_delta') thinking += event.delta;
  }
  return {
    message: await stream.result(),
    partialArguments,
    endedArguments,
    thinking,
  };
}

describe.each(protocols)('%s deferred tool argument parsing', (protocol) => {
  it('skips partial argument objects while preserving final calls, thinking and usage', async () => {
    const result = await collect(protocol, {
      parsePartialToolArguments: false,
    });
    expect(result.partialArguments.length).toBeGreaterThanOrEqual(4);
    expect(
      result.partialArguments.every((args) => JSON.stringify(args) === '{}'),
    ).toBe(true);
    expect(result.endedArguments).toEqual([
      { text: 'first' },
      { text: 'second' },
    ]);
    expect(result.thinking).toBe('thinking');
    expect(result.message.stopReason).toBe('toolUse');
    expect(result.message.usage).toMatchObject({ input: 7, output: 11 });
    expect(
      result.message.content.filter((block) => block.type === 'toolCall'),
    ).toEqual([
      expect.objectContaining({ arguments: { text: 'first' } }),
      expect.objectContaining({ arguments: { text: 'second' } }),
    ]);
    expect(JSON.stringify(result.message)).not.toMatch(
      /partialArgs|partialJson/,
    );
  });

  it('keeps parsing partial arguments by default', async () => {
    const result = await collect(protocol);
    expect(result.partialArguments).toContainEqual({ text: 'first' });
    expect(result.endedArguments).toEqual([
      { text: 'first' },
      { text: 'second' },
    ]);
  });

  it('preserves Delta final tool calls, thinking callbacks and usage presence', async () => {
    const thinking: string[] = [];
    const result = await createPiAiSession({
      protocol,
      endpoint: 'https://example.test',
      key: 'test-key',
      model: 'test-model',
      messages: [{ role: 'user', content: 'source', timestamp: 0 }],
      tools: [tool],
      fetch: fetchEvents(protocol),
      onThinking: (delta) => thinking.push(delta),
    });
    expect(result.message.stopReason).toBe('toolUse');
    expect(
      result.message.content
        .filter((block) => block.type === 'toolCall')
        .map((block) => block.arguments),
    ).toEqual([{ text: 'first' }, { text: 'second' }]);
    expect(thinking.join('')).toBe('thinking');
    expect(result.message.usage).toMatchObject({ input: 7, output: 11 });
    expect(result.usagePresence).toMatchObject({ input: true, output: true });
  });

  it('still rejects an interrupted provider stream', async () => {
    const data = events(protocol);
    const firstArguments = data.findIndex((event) =>
      protocol === 'openai-completions'
        ? JSON.stringify(event).includes('tool_calls')
        : protocol === 'anthropic-messages'
          ? (event.delta as { type?: string } | undefined)?.type ===
            'input_json_delta'
          : event.type === 'response.function_call_arguments.delta',
    );
    data.splice(firstArguments + 1);
    const result = await collect(
      protocol,
      { parsePartialToolArguments: false },
      data,
    );
    expect(result.message.stopReason).toBe('error');
    expect(result.message.errorMessage).toBeTruthy();
    expect(JSON.stringify(result.message)).not.toMatch(
      /partialArgs|partialJson/,
    );
  });

  it('preserves final JSON repair when the token limit cuts off tool arguments', async () => {
    const makeData = () => {
      const data = events(protocol);
      if (protocol === 'openai-completions') {
        return [
          ...data.slice(0, 2),
          { choices: [{ index: 0, delta: {}, finish_reason: 'length' }] },
        ];
      }
      if (protocol === 'anthropic-messages') {
        return [
          ...data.slice(0, 6),
          { type: 'content_block_stop', index: 1 },
          {
            type: 'message_delta',
            delta: { stop_reason: 'max_tokens' },
            usage: { output_tokens: 11 },
          },
          { type: 'message_stop' },
        ];
      }
      return [
        ...data.slice(0, 5),
        {
          type: 'response.output_item.done',
          output_index: 1,
          item: {
            type: 'function_call',
            id: 'fc-0',
            call_id: 'call-0',
            name: 'translate',
            arguments: argumentsJson[0].slice(0, 10),
          },
        },
        {
          type: 'response.incomplete',
          response: {
            status: 'incomplete',
            incomplete_details: { reason: 'max_output_tokens' },
          },
        },
      ];
    };
    const normal = await collect(protocol, {}, makeData());
    const deferred = await collect(
      protocol,
      { parsePartialToolArguments: false },
      makeData(),
    );
    expect(normal.message.stopReason).toBe('length');
    expect(deferred.message.stopReason).toBe('length');
    expect(deferred.endedArguments).toEqual(normal.endedArguments);
    expect(deferred.endedArguments).toHaveLength(1);
    expect(deferred.endedArguments[0]).toMatchObject({
      text: expect.any(String),
    });
  });

  it('still reports an aborted stream', async () => {
    const controller = new AbortController();
    const result = await collect(protocol, {
      parsePartialToolArguments: false,
      signal: controller.signal,
      onProviderStreamEvent: () => {
        controller.abort();
      },
    });
    expect(result.message.stopReason).toBe('aborted');
  });
});
