import ky from 'ky';

const api = ky.create({ prefixUrl: window.origin + '/api' });

export default api;
