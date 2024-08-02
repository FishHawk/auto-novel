import { removeWebNovel } from './RemoveWebNovel.js';
import { es, mongo } from './config.js';

async function run() {
  await removeWebNovel('syosetu', 'n0646ie');
  await removeWebNovel('kakuyomu', '16817330660019717771');
  await mongo.close();
}

run();
