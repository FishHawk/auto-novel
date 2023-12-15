import { KyInstance } from 'ky/distribution/types/ky';
import { Options } from 'ky/distribution/types/options';
import { v4 as uuidv4 } from 'uuid';

import { parseEventStream } from './common';

export class OpenAiApiWeb {
  type: 'web';
  client: KyInstance;
  model: OpenAiApiWeb.SupportedModel;

  constructor(
    client: KyInstance,
    endpoint: string,
    accessToken: string,
    model: OpenAiApiWeb.SupportedModel
  ) {
    this.type = 'web';
    this.client = client.create({
      prefixUrl: endpoint,
      headers: {
        Authorization: `Bearer ${accessToken}`,
        'Content-Type': 'application/json',
      },
      timeout: 100_000,
    });
    this.model = model;
  }

  async createConversation(
    json: Conversation.Params,
    options?: Options
  ): Promise<Generator<Conversation.Chunk>> {
    const text = await this.client
      .post('conversation', {
        json: {
          action: 'next',
          parent_message_id: uuidv4(),
          model: this.model,
          ...json,
        },
        headers: { accept: 'text/event-stream' },
        ...options,
      })
      .text();
    return parseEventStream<Conversation.Chunk>(text);
  }

  getConversation(conversationId: string): Promise<Conversation> {
    return this.client
      .get(`conversation/${conversationId}`)
      .json<Conversation>();
  }
}

export namespace OpenAiApiWeb {
  export type SupportedModel = 'text-davinci-002-render-sha';
  export const isSupportedModel = (model: string): model is SupportedModel =>
    model === 'text-davinci-002-render-sha';

  export const message = (
    role: 'user' | 'assistant' | 'system',
    text: string
  ): Conversation.Message => ({
    id: uuidv4(),
    author: { role },
    content: { content_type: 'text', parts: [text] },
  });
}

export interface Conversation {
  title: string;
  create_time: number;
  update_time: number;
  mapping: {
    [key: string]: {
      id: string;
      message?: Conversation.Message;
      parent?: string;
      children: string[];
    };
  };
  moderation_results: any[];
  current_node: string;
}

export namespace Conversation {
  export interface Message {
    id: string;
    author: { role: 'user' | 'assistant' | 'system' };
    content: { content_type: 'text'; parts: string[] };
  }

  export interface Params {
    messages: Array<Message>;
    history_and_training_disabled: false;
  }

  export type Chunk = ChunkModeration | ChunkMessage | ChunkError | string;

  interface ChunkMessage {
    conversation_id: string;
    error: null;
    message: Message;
  }

  interface ChunkModeration {
    conversation_id: string;
    message_id: string;
    moderation_response: {
      blocked: boolean;
      flagged: boolean;
      moderation_id: string;
    };
  }

  interface ChunkError {
    detail:
      | {
          message: string;
          type: string;
          param: null;
          code: string;
        }
      | string;
  }
}
