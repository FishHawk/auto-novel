import { MongoClient } from "mongodb";

const uri = 'mongodb://127.0.0.1:27017/main'

export const mongo = new MongoClient(uri);