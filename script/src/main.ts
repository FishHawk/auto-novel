import { generateWebIndex } from './GenerateWebIndex.js';
import { generateWenkuIndex } from './GenerateWenkuIndex.js';
import { es, mongo } from './config.js';

async function run() {
  try {
    await generateWenkuIndex();
  } finally {
    await mongo.close();
  }
}

run();
