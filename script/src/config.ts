import { Client } from '@elastic/elasticsearch';
import { MongoClient } from 'mongodb';

export const es = new Client({ node: 'http://localhost:9200' });

export const mongo = new MongoClient('mongodb://localhost:27017/main');
