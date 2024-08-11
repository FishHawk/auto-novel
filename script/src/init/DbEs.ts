import { Client } from '@elastic/elasticsearch';

const client = new Client({ node: 'http://localhost:9200' });

export const ES = {
  client,

  WEB_INDEX: 'web.2024-06-10',
  WENKU_INDEX: 'wenku.2024-05-15',
};
