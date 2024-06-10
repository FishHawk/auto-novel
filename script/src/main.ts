import { generateWebIndex } from './GenerateWebIndex.js';
import { es, mongo } from './config.js';

async function run() {
  try {
    await generateWebIndex();
  } finally {
    await mongo.close();
  }
}

run();
