import { Document } from 'mongodb';
import fs from 'node:fs';

import { mongo } from './mongo.js';

const formatDuration = (durationInMs: number) => {
  const pad = (num: number) => `${num < 10 ? '0' : ''}${num}`;

  const durationInS = Math.floor(durationInMs / 1000);
  const hours = Math.floor(durationInS / 3600);
  const minutes = Math.floor((durationInS % 3600) / 60);
  const seconds = Math.floor(durationInS % 60);
  return `${pad(hours)}:${pad(minutes)}:${pad(seconds)}`;
};

const writeSakuraIncorrectCase = (exportDir: string, doc: Document) => {
  try {
    const dir = exportDir;
    if (!fs.existsSync(dir)) {
      fs.mkdirSync(dir, { recursive: true });
    }
    fs.writeFileSync(
      `${dir}/${doc['_id']}.json`,
      `${JSON.stringify(doc, null, 4)}`
    );
    return true;
  } catch (err) {
    return false;
  }
};

export const exportSakuraIncorrectCase = async (exportDir: string) => {
  const database = mongo.db('main');
  const cases = database.collection('sakura-incorrect-case');

  const total = await cases.estimatedDocumentCount({});

  const cursor = cases.find();
  let index = 0;
  const startTimestamp = Date.now();
  for await (const doc of cursor) {
    const success = writeSakuraIncorrectCase(exportDir, doc);
    if (!success) {
      console.log('fail');
    }
    index += 1;

    if (index % 1000 === 0) {
      const progress = ((index * 100) / total).toFixed(2);
      const duration = Date.now() - startTimestamp;
      console.log(
        `${progress}% ${index}/${total} ${formatDuration(duration)} ${doc._id}`
      );
    }
  }
  await cursor.close();
};
