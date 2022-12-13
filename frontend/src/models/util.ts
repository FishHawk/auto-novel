import { MessageApiInjection } from 'naive-ui/es/message/src/MessageProvider';

export type Result<T, E = undefined> =
  | { ok: true; value: T }
  | { ok: false; error: E | undefined };

export const Ok = <T>(data: T): Result<T, never> => {
  return { ok: true, value: data };
};

export const Err = <E>(error?: E): Result<never, E> => {
  return { ok: false, error };
};

export function handleError(
  message: MessageApiInjection,
  error: any,
  prefix: string
) {
  if (error.response) {
    message.error(`${prefix} ${error.response.status}:${error.response.data}`);
  } else if (error.request) {
    message.error(`${prefix} 没有收到回复`);
  } else {
    message.error(`${prefix} ${error.message}`);
  }
}
