import { ES } from './DbEs.js';
import { MONGO } from './DbMongo.js';

export const generateEsIndexWenku = async () => {
  const es = ES.client;
  const index = ES.WENKU_INDEX;

  try {
    await es.indices.delete({ index });
  } catch {}

  await es.indices.create(
    {
      index,
      mappings: {
        properties: {
          title: { type: 'text', analyzer: 'icu_analyzer' },
          titleZh: { type: 'text', analyzer: 'icu_analyzer' },
          authors: { type: 'keyword' },
          artists: { type: 'keyword' },
          keywords: { type: 'keyword' },
          level: { type: 'keyword' },
          publisher: { type: 'keyword' },
          imprint: { type: 'keyword' },
          latestPublishAt: { type: 'date' },
          updateAt: { type: 'date' },
        },
      },
    },
    { ignore: [400] }
  );

  const col = MONGO.col(MONGO.WENKU_NOVEL);
  const total = await col.countDocuments();
  const novels = col.find().project({
    title: 1,
    titleZh: 1,
    cover: 1,
    authors: 1,
    artists: 1,
    keywords: 1,
    publisher: 1,
    imprint: 1,
    level: 1,
    latestPublishAt: 1,
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
  for await (const doc of novels) {
    const id = doc._id.toHexString();
    delete doc._id;
    dataset.push({ id, doc });

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
