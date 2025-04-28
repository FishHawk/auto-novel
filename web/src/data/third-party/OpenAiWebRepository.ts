import ky, { KyInstance, Options } from 'ky';

import { parseEventStream } from '@/util';

export const createOpenAiWebRepository = (
  endpoint: string,
  accessToken: string,
) => {
  const client = ky.create({
    prefixUrl: endpoint,
    headers: {
      Authorization: `Bearer ${accessToken}`,
      'Content-Type': 'application/json',
    },
    timeout: 100_000,
    retry: 0,
  });

  const createConversation = (
    json: Conversation.Params,
    options?: Options,
  ): Promise<Generator<ConversationChunk>> =>
    client
      .post('conversation', {
        json,
        headers: { accept: 'text/event-stream' },
        ...options,
      })
      .text()
      .then((text) => {
        if (text.includes('wss_url')) {
          const wssUrl = JSON.parse(text).wss_url;
          return new Promise<string>((resolve, reject) => {
            const ws = new WebSocket(wssUrl);
            const messages: string[] = [];
            ws.onmessage = (event) => {
              if (event.type === 'message') {
                const base64Body = JSON.parse(event.data).body;
                const bodyByte = atob(base64Body);
                if (bodyByte.length > 0) {
                  messages.push(bodyByte);
                }
                if (bodyByte.includes('[DONE]')) {
                  ws.close();
                }
              }
            };
            ws.onerror = (error) => {
              reject(error);
            };
            ws.onclose = () => {
              resolve(messages.join('\n'));
            };
          });
        } else {
          return text;
        }
      })
      .then(parseEventStream<ConversationChunk>);

  const getConversation = (
    conversationId: string,
    options?: Options,
  ): Promise<Conversation> =>
    client.get(`conversation/${conversationId}`, options).json<Conversation>();

  return {
    createConversation,
    getConversation,
  };
};

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
  moderation_results: unknown[];
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
