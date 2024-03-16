import { DBSchema, IDBPDatabase, deleteDB, openDB } from 'idb';

export type { DBSchema } from 'idb';

export type Mutator<T> = (value: T) => T;

export const lazyOpenDb = <T extends DBSchema>(
  ...args: Parameters<typeof openDB<T>>
) => {
  let dbPromise: Promise<IDBPDatabase<T>> | undefined;

  const openDBNamed = () => openDB<T>(...args);
  const deleteDBNamed = () =>
    deleteDB(args[0]).finally(() => (dbPromise = undefined));

  const getDBNamed = async () => {
    if (dbPromise === undefined) {
      dbPromise = openDBNamed();
    }
    return await dbPromise;
  };

  return {
    with: <Args extends Array<any>, Return>(
      fn: (db: IDBPDatabase<T>, ...args: Args) => Promise<Return>
    ) => {
      return async (...args: Args): Promise<Return> => {
        return fn(await getDBNamed(), ...args);
      };
    },
    deleteDb: deleteDBNamed,
  };
};
