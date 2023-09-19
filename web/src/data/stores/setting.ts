import { defineStore } from 'pinia';

export interface Setting {
  isDark: boolean;
  tocSortReverse: boolean;
  openAiAccessTokens: string[];
  downloadFilenameType: 'jp' | 'zh';
}

export const useSettingStore = defineStore('setting', {
  state: () =>
    <Setting>{
      isDark: false,
      tocSortReverse: false,
      openAiAccessTokens: [],
      downloadFilenameType: 'zh',
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
  },
  persist: true,
});
