export const parseTask = (task: string) => {
  const [taskString, queryString] = task.split('?');
  const { start, end, expire } = Object.fromEntries(
    new URLSearchParams(queryString)
  );

  let desc: any;
  if (taskString.startsWith('web')) {
    const [type, providerId, novelId] = taskString.split('/');
    desc = { type, providerId, novelId };
  } else if (taskString.startsWith('wenku')) {
    const [type, novelId, volumeId] = taskString.split('/');
    desc = { type, novelId, volumeId };
  } else if (taskString.startsWith('personal')) {
    const [type, volumeId] = taskString.split('/');
    desc = { type, volumeId };
  } else {
    throw 'quit';
  }

  const parseIntWithDefault = (str: string, defaultValue: number) => {
    const num = parseInt(str, 10);
    return isNaN(num) ? defaultValue : num;
  };

  const params = {
    translateExpireChapter: expire === 'true',
    syncFromProvider: false,
    startIndex: parseIntWithDefault(start, 0),
    endIndex: parseIntWithDefault(end, 65535),
  };

  return { desc, params };
};
