import { TranslateTaskDesc, TranslateTaskParams } from '@/data/translator/api';

export const parseTask = (task: string) => {
  const [taskString, queryString] = task.split('?');
  const { start, end, expire, toc } = Object.fromEntries(
    new URLSearchParams(queryString) as any
  );

  let desc: TranslateTaskDesc;
  if (taskString.startsWith('web/')) {
    const [type, providerId, novelId] = taskString.split('/');
    desc = { type: type as any, providerId, novelId };
  } else if (taskString.startsWith('wenku/')) {
    const [type, novelId, volumeId] = taskString.split('/');
    desc = { type: type as any, novelId, volumeId };
  } else if (
    taskString.startsWith('personal/') ||
    taskString.startsWith('personal2/')
  ) {
    const [_type, volumeId] = taskString.split('/');
    desc = { type: 'personal', volumeId };
  } else {
    throw 'quit';
  }

  const parseIntWithDefault = (str: string, defaultValue: number) => {
    const num = parseInt(str, 10);
    return isNaN(num) ? defaultValue : num;
  };

  const params: TranslateTaskParams = {
    translateExpireChapter: expire === 'true',
    overriteToc: toc === 'true',
    syncFromProvider: false,
    startIndex: parseIntWithDefault(start, 0),
    endIndex: parseIntWithDefault(end, 65535),
  };

  return { desc, params };
};

export const parseTaskUrl = (task: string) => {
  const { desc } = parseTask(task);
  if (desc.type === 'web') {
    return `/novel/${desc.providerId}/${desc.novelId}`;
  } else if (desc.type === 'wenku') {
    return `/wenku/${desc.novelId}`;
  } else {
    return undefined;
  }
};
