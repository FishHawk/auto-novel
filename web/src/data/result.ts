export type Result<T> =
  | { ok: true; value: T }
  | { ok: false; error: { message: string } };

export const Ok = <T>(data: T): Result<T> => {
  return { ok: true, value: data };
};

export const Err = (error: string): Result<never> => {
  return { ok: false, error: { message: error } };
};

export type ResultState<T> = Result<T> | undefined;

export function runCatching<T>(callback: Promise<T>): Promise<Result<T>> {
  return callback
    .then((it) => Ok(it))
    .catch((error) => {
      if (error.response) {
        return error.response.text().then((message: string) => Err(message));
      } else if (error.request) {
        return Err('没有收到回复');
      }
    });
}

export function mapOk<T, R>(result: Result<T>, fn: (value: T) => R): Result<R> {
  return result.ok === true ? { ok: true, value: fn(result.value) } : result;
}
