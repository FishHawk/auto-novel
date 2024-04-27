import ky, { Options } from 'ky';

import { parseEventStream } from '@/util';

export const createBaiduRepository = () => {
  const client = ky.create({
    prefixUrl: 'https://fanyi.baidu.com',
    credentials: 'include',
    retry: 0,
  });

  const sug = () => {
    const formData = new FormData();
    formData.append('kw', 'test');
    return client
      .post('sug', {
        body: formData,
      })
      .text();
  };

  const translate = (query: string, options: Options) => {
    return client
      .post('ait/text/translate', {
        headers: {
          accept: 'text/event-stream',
        },
        json: {
          from: 'jp',
          to: 'zh',
          query,
          corpusIds: [],
          domain: 'common',
          milliTimestamp: Date.now(),
          needPhonetic: false,
          qcSettings: ['1', '2', '3', '4', '5', '6', '7', '8', '9', '10', '11'],
          reference: '',
        },
        ...options,
      })
      .text()
      .then(parseEventStream<TranslateChunk>);
  };
  return {
    sug,
    translate,
  };
};

export type TranslateChunk = {
  errno: number;
  errmsg: string;
  data:
    | {
        event: 'Start' | 'StartTranslation' | 'TranslationSucceed' | 'Finished';
        message: string;
      }
    | {
        event: 'Translating';
        message: string;
        list: {
          id: string;
          paraIdx: number;
          src: string;
          dst: string;
          metadata: string;
        }[];
      };
};
