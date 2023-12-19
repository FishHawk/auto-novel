import { KyInstance } from 'ky/distribution/types/ky';
import { Options } from 'ky/distribution/types/options';

import { parseEventStream } from './util';

export class OpenAiWeb {
  id: 'openai-web' = 'openai-web';
  client: KyInstance;

  constructor(client: KyInstance, endpoint: string, accessToken: string) {
    this.client = client.create({
      prefixUrl: endpoint,
      headers: {
        Authorization: `Bearer ${accessToken}`,
        'Content-Type': 'application/json',
      },
      timeout: 100_000,
    });
  }

  createConversation = (
    json: Conversation.Params,
    options?: Options
  ): Promise<Generator<ConversationChunk>> =>
    this.client
      .post('conversation', {
        json,
        headers: { accept: 'text/event-stream' },
        ...options,
      })
      .text()
      .then(parseEventStream<ConversationChunk>);

  getConversation = (
    conversationId: string,
    options?: Options
  ): Promise<Conversation> =>
    this.client
      .get(`conversation/${conversationId}`, options)
      .json<Conversation>();
}

interface Conversation {
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

type ConversationChunk =
  | {
      conversation_id: string;
      error: null;
      message: Conversation.Message;
    }
  | {
      conversation_id: string;
      message_id: string;
      moderation_response: {
        blocked: boolean;
        flagged: boolean;
        moderation_id: string;
      };
    }
  | {
      detail:
        | {
            message: string;
            type: string;
            param: null;
            code: string;
          }
        | string;
    }
  | string;

namespace Conversation {
  export interface Message {
    id: string;
    author: { role: 'user' | 'assistant' | 'system' };
    content: { content_type: 'text'; parts: string[] };
  }

  export interface Params {
    action: 'next';
    parent_message_id: string;
    model: string;
    messages: Array<Message>;
    history_and_training_disabled: false;
  }
}
