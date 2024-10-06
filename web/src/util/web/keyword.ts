export const mapper = [
  ['ハーレム', '后宫', '後宮'],
  ['シリアス', '严肃', '嚴肅'],
  ['ほのぼの', '温暖', '溫暖'],
  ['バトル', '战斗', '戰鬥'],
  ['ラブコメ', '爱情喜剧', '愛情喜劇'],
  ['ハッピーエンド', 'HappyEnd', 'HappyEnd'],
  ['バッドエンド', 'BadEnd', 'BadEnd'],
  ['嘘コク', '假告白', '假告白'],
  ['ギャグ', '搞笑', '搞笑'],
  ['チート', '作弊', '作弊'],
  ['ファンタジー', '奇幻', '奇幻'],
  ['スクールラブ', '校园爱情', '校園愛情'],
  ['ダーク', '黑暗', '黑暗'],
  ['ミステリー', '推理', '推理'],
  ['ヒーロー', '主角', '主角'],
  ['ヒロイン', '女主角', '女主角'],
  ['ダンジョン', '迷宫', '迷宮'],
  ['ざまぁ', '活该', '活該'],
  ['ざまあ', '活该', '活該'],
  ['ディストピア', '反乌托邦', '反烏托邦'],
  ['アイドル', '偶像', '偶像'],
  ['成り上がり', '暴发户', '暴發戶'],
  ['ライトノベル', '轻小说', '輕小說'],
  ['セフレ', '性伙伴', '性夥伴'],
  ['ホームドラマ', '家庭剧', '家庭劇'],
  ['パラレルワールド', '平行世界', '平行世界'],
  ['ヤンデレ', '病娇', '病嬌'],
  ['ツンデレ', '傲娇', '傲嬌'],
  ['ゲーム', '游戏', '遊戲'],
  ['コミカライズ', '漫画化', '漫畫化'],
  ['アニメ化', '动画化', '動畫化'],
  ['スキル', '技能', '技能'],
  ['ボーイズラブ', '耽美', '耽美'],
  ['ガールズラブ', '百合', '百合'],
  ['いじめ', '欺凌', '欺凌'],
  ['レイプ', '强奸', '強姦'],
  ['ロリ', '萝莉', '蘿莉'],
  ['コメディ', '喜剧', '喜劇'],
  ['カクヨムオンリー', 'kakuyomu原创', 'kakuyomu原創'],
  ['転移', '转移', '轉移'],
  ['性描写有り', '性描写', '性描寫'],
  ['暴力描写有り', '暴力描写', '暴力描寫'],
  ['残酷描写有り', '残酷描写', '殘酷描寫'],
];

export const tryTranslateKeyword = (keyword: string) => {
  mapper.forEach(([jp, zh, _]) => (keyword = keyword.replace(jp, zh)));
  return keyword;
};
