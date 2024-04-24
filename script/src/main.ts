import os from 'node:os';

import { exportSakuraIncorrectCase } from './ExportSakuraIncorrectCase.js';
import { exportWebChapter } from './ExportWebChapter.js';
import { mongo } from './mongo.js';

async function run() {
  try {
    const exportDir = `${os.homedir()}/data/incorrect`;
    await exportSakuraIncorrectCase(exportDir);
  } finally {
    await mongo.close();
  }
}

run();
