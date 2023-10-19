import ky from 'ky';

let client = ky.create({ prefixUrl: window.origin + '/api' });

function updateToken(token?: string) {
  let headers;
  if (token !== undefined) {
    headers = { Authorization: 'Bearer ' + token };
  } else {
    headers = {};
  }
  client = client.extend({
    headers,
  });
}

export { updateToken, client };
