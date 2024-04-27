import {
  TranslateTaskCallback,
  TranslateTaskDesc,
  TranslateTaskParams,
  TranslatorDesc,
} from '@/model/Translator';
import { keepPageAlive } from '@/util';

import { translateLocal } from './TranslateLocal';
import { translateWeb } from './TranslateWeb';
import { translateWenku } from './TranslateWenku';

export const translate = async (
  taskDesc: TranslateTaskDesc,
  taskParams: TranslateTaskParams,
  taskCallback: TranslateTaskCallback,
  translatorDesc: TranslatorDesc,
  signal?: AbortSignal
) => {
  await keepPageAlive();

  if (taskDesc.type === 'web') {
    return translateWeb(
      taskDesc,
      taskParams,
      taskCallback,
      translatorDesc,
      signal
    );
  } else if (taskDesc.type === 'wenku') {
    return translateWenku(
      taskDesc,
      taskParams,
      taskCallback,
      translatorDesc,
      signal
    );
  } else {
    return translateLocal(
      taskDesc,
      taskParams,
      taskCallback,
      translatorDesc,
      signal
    );
  }
};
