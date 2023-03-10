import { describe, expect, it } from 'vitest';

import { BaiduQueryProcessor } from '../src/data/translator/baidu';

describe('baidu', () => {
  it('processor', () => {
    const query_list = ['1', '2\n'.repeat(4), '3\n'.repeat(3)];
    const processor = new BaiduQueryProcessor(query_list);
    const result_list = processor.applyPostProcess(processor.getPreProcessed());
    expect(result_list).toStrictEqual(query_list);
  });
});
