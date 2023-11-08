import { Err, Ok, Result, runCatching } from '@/data/result';

import { VolumeJpDto } from './api_wenku_novel';
import { client } from './client';

export interface UserVolumes {
  downloadToken: string;
  volumes: VolumeJpDto[];
}

const listVolume = () => runCatching(client.get('personal').json<UserVolumes>());

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

export const ApiPersonalNovel = {
  listVolume,
  createVolume,
  deleteVolume,
};
