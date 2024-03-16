import ky, { HTTPError, TimeoutError } from 'ky';

let client = ky.create({ prefixUrl: window.origin + '/api' });
export { client };

export const updateToken = (token?: string) => {
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

export const formatError = (error: any) => {
  if (error instanceof HTTPError) {
    let messageOverride: string | null = null;
    if (error.response.status === 429) {
      messageOverride = '操作额度耗尽，等明天再试吧';
    }
    return error.response
      .text()
      .then(
        (message) => `[${error.response.status}]${messageOverride ?? message}`
      );
  } else if (error instanceof TimeoutError) {
    return '请求超时';
  } else {
    return `${error}`;
  }
};
