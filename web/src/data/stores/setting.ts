import { defineStore } from 'pinia';

import { TranslatorId } from '@/data/translator/translator';

export interface Setting {
  isDark: boolean;
  tocSortReverse: boolean;
  openAiAccessTokens: string[];
  sakuraEndpoints: string[];
  downloadFilenameType: 'jp' | 'zh';

  isDownloadFormatSameAsReaderFormat: boolean;
  downloadFormat: {
    mode: 'jp' | 'zh' | 'mix' | 'mix-reverse';
    translationsMode: 'parallel' | 'priority';
    translations: TranslatorId[];
  };
}

export const useSettingStore = defineStore('setting', {
  state: () =>
    <Setting>{
      isDark: false,
      tocSortReverse: false,
      openAiAccessTokens: [],
      sakuraEndpoints: [],
      downloadFilenameType: 'zh',
      isDownloadFormatSameAsReaderFormat: true,
      downloadFormat: {
        mode: 'mix',
        translationsMode: 'priority',
        translations: ['sakura', 'gpt', 'youdao', 'baidu'],
      },
    },
  actions: {
    addToken(token: string) {
      this.deleteToken(token);
      this.openAiAccessTokens.unshift(token);
      if (this.openAiAccessTokens.length > 10) {
        this.openAiAccessTokens.length == 10;
      }
    },
    deleteToken(token: string) {
      this.openAiAccessTokens = this.openAiAccessTokens.filter(
        (t) => t !== token
      );
    },
    addSakuraEndpoint(endpoint: string) {
      this.deleteSakuraEndpoint(endpoint);
      this.sakuraEndpoints.unshift(endpoint);
      if (this.sakuraEndpoints.length > 10) {
        this.sakuraEndpoints.length == 10;
      }
    },
    deleteSakuraEndpoint(endpoint: string) {
      this.sakuraEndpoints = this.sakuraEndpoints.filter((t) => t !== endpoint);
    },
  },
  persist: true,
});
