import { describe, expect, it } from 'vitest';

import { InputSegmenter } from '../src/data/translator/base';

const input = `
　一人の少女があの日を思い出す。



　バンフィールド伯爵家の本星の空を覆い尽くすのは、数多くの巨大な宇宙戦艦だった。



　戦艦の周囲には、人型で十八メートル前後のロボットたちが飛んでいた。



　ロボットの名前は機動騎士。



　戦争のための道具であるそれらが、自分たちの上空を飛んでいるのに周囲は歓声を上げていた。



　上空に向かって手を振り、中には笑いながら涙を流している人もいる。



　愛し合っている恋人や家族と抱きしめ合い、喜びを噛みしめている人々も多かった。



　彼らはバンフィールド伯爵家が保有する軍隊である。



　戦争から戻り、凱旋している最中だった。`.split('\n');

describe('translator', () => {
  it('seg', () => {
    const segmenter = new InputSegmenter(input, 80);
    let list: string[] = [];
    for (const seg of segmenter.segment()) {
      list = list.concat(seg);
      console.log(seg);
    }
    const output = segmenter.recover(list);
    expect(output).toStrictEqual(input);
  });
});
