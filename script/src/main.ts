import { generateWenkuIndex } from './GenerateWenkuIndex.js';
import { es, mongo } from './config.js';

async function run() {
  try {
    const col = mongo.db('main').collection('wenku-metadata');
    const r1 = await col.updateMany(
      { r18: true, level: undefined },
      { $set: { level: '成人向' } }
    );
    const r2 = await col.updateMany(
      { r18: false, level: undefined },
      { $set: { level: '一般向' } }
    );
    console.log(r1.modifiedCount);
    console.log(r2.modifiedCount);
    await generateWenkuIndex();
  } finally {
    await mongo.close();
  }
}

run();
