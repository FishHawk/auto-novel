import { ES } from './DbEs.js';
import { MONGO } from './DbMongo.js';

export const generateEsIndexWeb = async () => {
  const es = ES.client;
  const index = ES.WEB_INDEX;

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

  const col = MONGO.col(MONGO.WEB_NOVEL);
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

  const dataset: { id: string; doc: any }[] = [];

  const processDataset = async () => {
    const operations = dataset.flatMap(({ id, doc }) => [
      { index: { _index: index, _id: id } },
      doc,
    ]);
    await es.bulk({ operations });
  };

  let i = 1;
  for await (const it of novels) {
    const doc = {
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
    dataset.push({
      id: `${it.providerId}.${it.bookId}`,
      doc,
    });

    if (dataset.length === 300) {
      console.log(`${i * 300}/${total}`);
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
