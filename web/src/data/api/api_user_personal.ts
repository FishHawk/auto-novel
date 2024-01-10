import { runCatching } from '@/data/result';

import { client } from './client';

export interface PersonalVolume {
  volumeId: string;
  total: number;
  baidu: number;
  youdao: number;
  gpt: number;
  sakura: number;
  glossary: { [key: string]: string };
}

export interface PersonalVolumes {
  downloadToken: string;
  volumes: PersonalVolume[];
}

const listVolume = () =>
  runCatching(client.get('personal').json<PersonalVolumes>());

const createFileUrl = ({
  volumeId,
  lang,
  translationsMode,
  translations,
  downloadToken,
}: {
  volumeId: string;
  lang: 'zh' | 'zh-jp' | 'jp-zh';
  translationsMode: 'parallel' | 'priority';
  translations: ('sakura' | 'baidu' | 'youdao' | 'gpt')[];
  downloadToken: string;
}) => {
  let filename = `${lang}.${translationsMode === 'parallel' ? 'B' : 'Y'}`;
  translations.forEach((it) => (filename += it[0]));
  filename += '.';
  filename += volumeId;

  const params = new URLSearchParams({
    translationsMode,
    lang,
    filename,
    downloadToken,
  });
  translations.forEach((it) => params.append('translations', it));
  const url = `/api/personal/file/${volumeId}?${params}`;
  return { url, filename };
};

export const ApiUserPersonal = {
  listVolume,
  createFileUrl,
};
