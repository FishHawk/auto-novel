import { defineStore } from 'pinia';

export interface Setting {
  tocSortReverse: boolean;
  openAiAccessTokens: string[];
}

export const useSettingStore = defineStore('setting', {
  state: () => <Setting>{ tocSortReverse: false, openAiAccessTokens: [] },
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
