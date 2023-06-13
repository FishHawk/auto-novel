import { defineStore } from 'pinia';

export interface Setting {
  tocSortReverse: boolean;
}

export const useSettingStore = defineStore('setting', {
  state: () => <Setting>{ tocSortReverse: false },
  persist: true,
});
