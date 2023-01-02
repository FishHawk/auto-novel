import { describe, expect, it, vi } from 'vitest';

import got from 'got';

vi.doMock('ky', () => {
  return { default: got };
});

import { BaiduQueryProcessor } from '../src/translator/baidu';
import { BaiduWebTranslator } from '../src/translator/baidu-web';

const TEXT = `国境の長いトンネルを抜けると雪国であった。夜の底が白くなった。信号所に汽車が止まった。
内側の座席から娘が立って来て、島村の前のガラス窓を落とした。雪の冷気が流れ込んだ。娘は窓いっぱいに乗り出して、遠くへ叫ぶように、
「駅長さん、駅長さん」
明かりをさげてゆっくり吹きを踏んできた男は、襟巻で鼻の上まで包み、耳に帽子の毛皮を垂れていた。
もうそんな寒さかと島村は外を眺めると鉄道の官舎らしいバラックが山裾に寒々と散らばっているだけで、雪の色はそこまで行かぬうちに闇に飲まれていた。
「駅長さん、私です、御機嫌よろしゅうございます」
「ああ、葉子さんじゃないか。お帰りかい。また寒くなったよ」
「弟が今度こちらに勤めさせていただいておりますのですってね。お世話さまですわ」
「こんなところ、今に寂しくて参るだろうよ。若いのに可哀想だな」
「ほんの子供ですから、駅長さんからよく教えてやっていただいて、よろしくお願いいたしますわ」
「よろしい。元気で働いてるよ。これからいそがしくなる。去年は大雪だったよ。よく雪崩れてね、汽車が立往生するんで、村も炊出しがいそがしかったよ」
「駅長さんずいぶん厚着に見えますわ。弟の手紙には、まだチョッキも着ていないようなことを書いてありましたけれど」
私は着物を四枚重ねだ。若い者は寒いと酒ばがり飲んでいるよ。それでごろごろあすこにぶっ倒れてるのさ、風邪を引いてね」
駅長は宿舎の方へ手の明かりを振り向けた。
「弟もお酒をいただきますでしょうか」
「いや」
「駅長さんもうお帰りですの？」
「私は怪我をして、医者に通ってるんだ」
「まあ。いけませんわ」
和服に外套の駅長は寒い立話をさっさと切り上げたいらしく、もう後姿を見せながら、
「それじゃまあ大事にいらっしゃい」
「駅長さん、弟は今出ておりませんの？」と葉子は雪の上を目探しして、
「駅長さん、弟をよく見てやって、お願いです」
悲しいほど美しい声であった。高い響きのまま夜の雪から木魂して来そうだった。
汽車が動き出しても、彼女は窓から胸を入れなかった。そうして線路の下を歩いている駅長に追いつくと、
駅長さあん、今度の休みの日に家へお帰りって、弟に言ってやって下さあい」
「はあい」と、駅長が声を張り上げた。
葉子は窓をしめて、赤らんだ頬に両手をあてた
`;

describe('baidu', () => {
  it('processor', () => {
    const query_list = ['1', '2\n'.repeat(4), '3\n'.repeat(3)];
    const processor = new BaiduQueryProcessor(query_list);
    const result_list = processor.recover(processor.get());
    expect(result_list).toStrictEqual(query_list);
  });

  it.skip('web short text', async () => {
    const translator = await BaiduWebTranslator.createInstance('jp', 'zh');
    const query_list = TEXT.split('\n');
    const translated = await translator.translate(query_list);
    expect(translated).toHaveLength(query_list.length);
  });

  it.skip('web long text', async () => {
    const translator = await BaiduWebTranslator.createInstance('jp', 'zh');
    const query_list = TEXT.repeat(10).split('\n');
    const translated = await translator.translate(query_list);
    expect(translated).toHaveLength(query_list.length);
  });

  // it('snapshot', () => {
  //   expect({ foo: 'bar' }).toMatchSnapshot()
  // })
});
