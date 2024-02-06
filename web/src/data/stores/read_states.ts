import { defineStore } from 'pinia';

export interface ReadStates {
  wenkuUploadRule: number;
}

export const useReadStateStore = defineStore('readState', {
  state: () =>
    <ReadStates>{
      wenkuUploadRule: 0,
    },
  persist: true,
});
