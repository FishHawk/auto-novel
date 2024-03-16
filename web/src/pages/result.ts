import { HTTPError, TimeoutError } from 'ky';

export type Result<T> =
  | { ok: true; value: T }
  | { ok: false; error: { message: string } };

export const Ok = <T>(data: T): Result<T> => {
  return { ok: true, value: data };
};

export const Err = (error: string): Result<never> => {
  return { ok: false, error: { message: error } };
};

export function runCatching<T>(callback: Promise<T>): Promise<Result<T>> {
  return callback
    .then((it) => Ok(it))
    .catch((error) => {
      if (error instanceof HTTPError) {
        let messageOverride: string | null = null;
        if (error.response.status === 429) {
          messageOverride = '操作额度耗尽，等明天再试吧';
        }
        return error.response
          .text()
          .then((message) =>
            Err(`[${error.response.status}]${messageOverride ?? message}`)
          );
      } else if (error instanceof TimeoutError) {
        return Err('请求超时');
      } else {
        return Err(`${error}`);
      }
    });
}
