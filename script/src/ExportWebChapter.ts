import { Document, ObjectId } from 'mongodb';
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

const writeFailedLog = (exportDir: string, text: string) => {
  const dir = exportDir;
  if (!fs.existsSync(dir)) {
    fs.mkdirSync(dir, { recursive: true });
  }
  fs.appendFileSync(`${dir}/failed`, text + '\n');
};

const writeWebChapter = (exportDir: string, doc: Document) => {
  const providerId: string = doc['providerId'];
  const novelId: string = doc['bookId'];
  const chapterId: string = doc['episodeId'];
  const text = (doc['paragraphsJp'] as string[]).join('\n');
  try {
    const dir = `${exportDir}/${providerId}/${novelId}`;
    if (!fs.existsSync(dir)) {
      fs.mkdirSync(dir, { recursive: true });
    }
    fs.writeFileSync(`${dir}/${chapterId}`, text);
  } catch (err) {
    writeFailedLog(exportDir, `${providerId}/${novelId}/${chapterId} ${err}`);
  }
};

export const exportWebChapter = async (exportDir: string) => {
  const database = mongo.db('main');
  const chapters = database.collection('episode');

  const total = await chapters.estimatedDocumentCount({});

  const lastObjectId: string | undefined = undefined;

  const cursor = chapters
    .find(
      lastObjectId === undefined ? {} : { _id: { $gt: new ObjectId('...') } }
    )
    .project({
      providerId: 1,
      bookId: 1,
      episodeId: 1,
      paragraphsJp: 1,
    });
  let index = 0;
  const startTimestamp = Date.now();
  for await (const doc of cursor) {
    writeWebChapter(exportDir, doc);
    index += 1;

    if (index % 1000 === 0) {
      const progress = ((index * 100) / total).toFixed(2);
      const duration = formatDuration(Date.now() - startTimestamp);
      const id = doc['_id'];
      console.log(`${progress}% ${index}/${total} ${duration} ${id}`);
    }
  }
  await cursor.close();
};
