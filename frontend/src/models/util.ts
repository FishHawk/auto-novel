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
  message.error(`${prefix} ${errorToString(error)}`);
}

export function errorToString(error: any) {
  if (error.response) {
    return `${error.response.status}:${error.response.statusText}`;
  } else if (error.request) {
    return `没有收到回复`;
  } else {
    return `${error.message}`;
  }
}
