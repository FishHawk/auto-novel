import { Err, Ok, Result, runCatching } from '@/data/result';

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

const createVolume = (
  volumeId: string,
  file: File,
  token: string,
  onProgress: (p: number) => void
) =>
  new Promise<Result<string>>(function (resolve, _reject) {
    const formData = new FormData();
    formData.append('jp', file as File);

    let xhr = new XMLHttpRequest();

    xhr.open('POST', `/api/personal/volume/${volumeId}`);

    xhr.setRequestHeader('Authorization', 'Bearer ' + token);
    xhr.onload = function () {
      if (xhr.status === 200) {
        resolve(Ok(''));
      } else {
        resolve(Err(xhr.responseText));
      }
    };
    xhr.upload.addEventListener('progress', (e) => {
      const percent = e.lengthComputable ? (e.loaded / e.total) * 100 : 0;
      onProgress(Math.ceil(percent));
    });
    xhr.send(formData);
  });

const deleteVolume = (volumeId: string) =>
  runCatching(client.delete(`personal/volume/${volumeId}`).text());

const updateGlossary = (volumeId: string, json: { [key: string]: string }) =>
  runCatching(
    client.put(`personal/volume/${volumeId}/glossary`, { json }).text()
  );

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
  createVolume,
  deleteVolume,
  updateGlossary,
  //
  createFileUrl,
};
