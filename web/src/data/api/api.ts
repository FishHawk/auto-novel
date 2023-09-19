import ky from 'ky';

let api = ky.create({ prefixUrl: window.origin + '/api' });

function updateToken(token?: string) {
  let headers;
  if (token !== undefined) {
    headers = { Authorization: 'Bearer ' + token };
  } else {
    headers = {};
  }
  api = api.extend({
    headers,
  });
}

export { api, updateToken };
