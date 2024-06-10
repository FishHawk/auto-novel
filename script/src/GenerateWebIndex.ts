import { es, mongo } from './config.js';

export const generateWebIndex = async () => {
  const index = 'web.2024-06-10';

  try {
    await es.indices.delete({ index });
  } catch {}

  await es.indices.create(
    {
      index,
      mappings: {
        properties: {
          providerId: { type: 'keyword' },
          titleJp: { type: 'text', analyzer: 'icu_analyzer' },
          titleZh: { type: 'text', analyzer: 'icu_analyzer' },
          authors: { type: 'keyword' },
          type: { type: 'keyword' },
          attentions: { type: 'keyword' },
          keywords: { type: 'keyword' },
          tocSize: { type: 'integer' },
          visited: { type: 'integer' },
          hasGpt: { type: 'boolean' },
          hasSakura: { type: 'boolean' },
          updateAt: { type: 'date' },
        },
      },
    },
    { ignore: [400] }
  );

  const col = mongo.db('main').collection('metadata');
  const total = await col.estimatedDocumentCount();
  const novels = col.find().project({
    providerId: 1,
    bookId: 1,
    titleJp: 1,
    titleZh: 1,
    authors: 1,
    type: 1,
    attentions: 1,
    keywords: 1,
    visited: 1,
    gpt: 1,
    sakura: 1,
    toc: 1,
    updateAt: 1,
  });

  const dataset: any[] = [];

  const processDataset = async () => {
    const operations = dataset.flatMap((doc) => [
      { index: { _index: index } },
      doc,
    ]);
    await es.bulk({ refresh: true, operations });
  };

  let i = 1;
  for await (const it of novels) {
    const novelEs = {
      id: `${it.providerId}.${it.bookId}`,
      providerId: it.providerId,
      novelId: it.bookId,
      authors: it.authors.map((a: any) => a.name),
      titleJp: it.titleJp,
      titleZh: it.titleZh,
      type: it.type ?? '连载中',
      attentions: it.attentions ?? [],
      keywords: it.keywords ?? [],
      hasGpt: (it.gpt ?? 0) > 0,
      hasSakura: (it.sakura ?? 0) > 0,
      visited: it.visited ?? 0,
      tocSize: it.toc.filter((it: any) => it.chapterId).length,
      updateAt: it.updateAt,
    };
    dataset.push(novelEs);

    if (dataset.length === 100) {
      console.log(`${i * 100}/${total}`);
      i += 1;
      await processDataset();
      dataset.length = 0;
    }
  }
  if (dataset.length > 0) {
    await processDataset();
  }

  const stat = await es.count({ index });
  console.log(`完成 Mongo:${total} ES:${stat.count}`);
};
