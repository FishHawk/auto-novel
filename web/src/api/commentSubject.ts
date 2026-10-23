/** Raw novel subject keys; the API client handles URL encoding. */
export const subjectKeys = {
  novel: {
    web: (providerId: string, novelId: string) =>
      `web-${providerId}-${novelId}`,
    wenku: (novelId: string) => `wenku-${novelId}`,
  },
} as const;
