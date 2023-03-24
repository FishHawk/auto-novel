export type Result<T, E = { message: string }> =
  | { ok: true; value: T }
  | { ok: false; error: E };

export const Ok = <T>(data: T): Result<T, never> => {
  return { ok: true, value: data };
};

export const Err = <E>(error: E): Result<never, E> => {
  return { ok: false, error };
};

export type ResultState<T> = Result<T> | undefined;

export async function runCatching<T>(
  callback: Promise<T>
): Promise<Result<T, { message: string }>> {
  const it = await callback.then((it) => Ok(it)).catch((error) => Err(error));
  if (!it.ok) {
    if (it.error.response) {
      it.error.message = await it.error.response.text();
    } else if (it.error.request) {
      it.error.message = `没有收到回复`;
    }
  }
  return it;
}
