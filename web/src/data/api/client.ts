import ky from 'ky';

let client = ky.create({ prefixUrl: '/api', timeout: 60000 });
let authToken: string | undefined = undefined;

export { client };

export const updateToken = (token?: string) => {
  authToken = token;
  let headers;
  if (token !== undefined) {
    headers = { Authorization: 'Bearer ' + token };
  } else {
    headers = {};
  }
  client = client.extend({
    headers,
  });
};

export const uploadFile = (
  url: string,
  name: string,
  file: File,
  onProgress: (p: number) => void,
) => {
  return new Promise<string>(function (resolve, reject) {
    const formData = new FormData();
    formData.append(name, file);

    const xhr = new XMLHttpRequest();
    xhr.open('POST', url);
    xhr.setRequestHeader('Authorization', 'Bearer ' + authToken);
    xhr.onload = () => {
      if (xhr.status === 200) {
        resolve(xhr.responseText);
      } else {
        reject(new Error(xhr.responseText));
      }
    };
    xhr.upload.addEventListener('progress', (e) => {
      const percent = e.lengthComputable ? (e.loaded / e.total) * 100 : 0;
      onProgress(Math.ceil(percent));
    });
    xhr.send(formData);
  });
};
