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
