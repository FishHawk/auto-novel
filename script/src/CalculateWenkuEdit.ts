import { ObjectId } from "mongodb";

import { mongo } from "./config.js";

export const calculateWenkuEdit = async () => {
  const database = mongo.db("main");
  const col = database.collection("operation-history");
  const operations = col
    .find({
      "operation.___type": "wenku-edit",
    })
    .project({
      operator: 1,
    });

  const userIdToCount: Record<string, number> = {};

  for await (const op of operations) {
    const userId: string = op["operator"].toHexString();
    if (!(userId in userIdToCount)) {
      userIdToCount[userId] = 0;
    }
    userIdToCount[userId] += 1;
  }

  let records = [];
  const userCol = database.collection("user");
  for (const userId in userIdToCount) {
    const user = (await userCol.findOne({ _id: new ObjectId(userId) }))!!;
    records.push({
      username: user["username"],
      count: userIdToCount[userId],
    });
  }
  records = records.sort((a, b) => a.count - b.count);
  const total = records.reduce((pv, it) => pv + it.count, 0);

  for (const { username, count } of records) {
    console.log(`${(count / total).toFixed(4)}\t${count}\t${username}`);
  }
};
