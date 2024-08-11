import { MONGO } from './init/DbMongo.js';
import { ensureMongoIndex } from './init/EnsureMongoIndex.js';

async function run() {
  await ensureMongoIndex();
  await MONGO.client.close();
}

run();
