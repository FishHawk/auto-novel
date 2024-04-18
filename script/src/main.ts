import fs from 'node:fs';
import os from 'node:os';
import { mongo } from './mongo.js';

const exportDir = `${os.homedir()}/export`;

const writeWebChapter = (
  providerId: string,
  novelId: string,
  chapterId: string,
  text: string
) => {
  try {
    const dir = `${exportDir}/${providerId}/${novelId}`;
    if (!fs.existsSync(dir)) {
      fs.mkdirSync(dir, { recursive: true });
    }
    fs.writeFileSync(`${dir}/${chapterId}`, text);
    return true;
  } catch (err) {
    return false;
  }
};

const writeFailedLog = (text: string) => {
  try {
    const dir = exportDir;
    if (!fs.existsSync(dir)) {
      fs.mkdirSync(dir, { recursive: true });
    }
    fs.appendFileSync(`${dir}/failed`, text + '\n');
  } catch (err) {
    console.log(`log error: ${err}`);
  }
};

const exportWebChapter = async () => {
  const database = mongo.db('main');
  const chapters = database.collection('episode');

  const total = await chapters.estimatedDocumentCount({});

  const cursor = chapters.find().project({
    _id: 0,
    providerId: 1,
    bookId: 1,
    episodeId: 1,
    paragraphsJp: 1,
  });
  let index = 0;
  for await (const doc of cursor) {
    if (index % 1000 === 0) {
      const progress = ((index * 100) / total).toFixed(2);
      console.log(`${progress}% ${index}/${total}`);
    }
    const providerId: string = doc['providerId'];
    const novelId: string = doc['bookId'];
    const chapterId: string = doc['episodeId'];
    const text = (doc['paragraphsJp'] as string[]).join('\n');
    const success = writeWebChapter(providerId, novelId, chapterId, text);
    if (!success) {
      writeFailedLog(`${providerId}/${novelId}/${chapterId}`);
    }
    index += 1;
  }
  await cursor.close();
};

async function run() {
  try {
    await exportWebChapter();
  } finally {
    await mongo.close();
  }
}

run();
