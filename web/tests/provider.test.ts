import { describe, expect, it } from 'vitest';
import { parseUrl } from '../src/data/provider';

function test_parse_url(providerId: string, benches: [string, string][]) {
  for (const [url, novelId] of benches) {
    const parseResult = parseUrl(url);
    expect(parseResult?.providerId).toBe(providerId);
    expect(parseResult?.novelId).toBe(novelId);
  }
}

describe('provider', () => {
  it('kakuyomu', () => {
    test_parse_url('kakuyomu', [
      [
        'https://kakuyomu.jp/works/16817139555217983105',
        '16817139555217983105',
      ],
      [
        'https://kakuyomu.jp/works/16817139555217983105/episodes/16817139555286132564',
        '16817139555217983105',
      ],
    ]);
  });

  it('syosetu', () => {
    test_parse_url('syosetu', [
      ['https://ncode.syosetu.com/n9669bk', 'n9669bk'],
      ['https://ncode.syosetu.com/n9669BK', 'n9669bk'],
      ['https://novel18.syosetu.com/n9669BK', 'n9669bk'],
    ]);
  });

  it('novelup', () => {
    test_parse_url('novelup', [
      ['https://novelup.plus/story/206612087', '206612087'],
      ['https://novelup.plus/story/206612087?p=2', '206612087'],
    ]);
  });

  it('hameln', () => {
    test_parse_url('hameln', [['https://syosetu.org/novel/297874/', '297874']]);
  });

  it('pixiv', () => {
    test_parse_url('pixiv', [
      ['https://www.pixiv.net/novel/series/870363', '870363'],
      ['https://www.pixiv.net/novel/series/870363?p=5', '870363'],
      ['https://www.pixiv.net/novel/show.php?id=18827415', 's18827415'],
    ]);
  });

  it('alphapolis', () => {
    test_parse_url('alphapolis', [
      [
        'https://www.alphapolis.co.jp/novel/638978238/525733370',
        '638978238-525733370',
      ],
    ]);
  });

  it('novelism', () => {
    test_parse_url('novelism', [
      [
        'https://novelism.jp/novel/2m0xulekSsCxfixwam8d7g',
        '2m0xulekSsCxfixwam8d7g',
      ],
    ]);
  });

  it('unmatch', () => {
    const benches = ['https://www.google.com/', 'https://books.fishhawk.top/'];
    for (const url of benches) {
      const parseResult = parseUrl(url);
      expect(parseResult).toBeUndefined();
    }
  });
});
