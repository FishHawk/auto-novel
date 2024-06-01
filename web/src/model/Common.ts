export type GenericNovelId =
  | { type: 'web'; providerId: string; novelId: string }
  | { type: 'wenku'; novelId: string }
  | { type: 'local'; volumeId: string };

export namespace GenericNovelId {
  export const web = (providerId: string, novelId: string): GenericNovelId => ({
    type: 'web',
    providerId,
    novelId,
  });

  export const wenku = (novelId: string): GenericNovelId => ({
    type: 'wenku',
    novelId,
  });

  export const local = (volumeId: string): GenericNovelId => ({
    type: 'local',
    volumeId,
  });

  export const toString = (gnid: GenericNovelId) => {
    if (gnid.type === 'web') {
      return `web/${gnid.providerId}/${gnid.novelId}`;
    } else if (gnid.type === 'wenku') {
      return `wenku/${gnid.novelId}`;
    } else {
      return `local/${gnid.volumeId}`;
    }
  };
}
