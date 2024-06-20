import { Locator } from '@/data';
import { Favored } from '@/model/User';

type BookshelfStore = {
  web: Favored[];
  wenku: Favored[];
  local: Favored[];
};

export const useBookshelfStore = defineStore('Bookshelf', {
  state: () =>
    <BookshelfStore>{
      web: [{ id: 'default', title: '默认收藏夹' }],
      wenku: [{ id: 'default', title: '默认收藏夹' }],
      local: [{ id: 'default', title: '默认收藏夹' }],
    },
  actions: {
    async loadFavoredList() {
      const { isSignedIn } = Locator.userDataRepository();
      if (isSignedIn.value) {
        const favoredList = await Locator.userRepository.listFavored();
        this.web = favoredList.favoredWeb;
        this.wenku = favoredList.favoredWenku;
      }
    },
    async createFavored(type: 'web' | 'wenku' | 'local', title: string) {
      if (type === 'web') {
        await Locator.userRepository.createFavoredWeb({ title });
      } else if (type === 'wenku') {
        await Locator.userRepository.createFavoredWenku({ title });
      } else {
        // 未完成
      }
      await this.loadFavoredList();
    },
    async updateFavored(
      type: 'web' | 'wenku' | 'local',
      id: string,
      title: string,
    ) {
      if (type === 'web') {
        await Locator.userRepository.updateFavoredWeb(id, { title });
      } else if (type === 'wenku') {
        await Locator.userRepository.updateFavoredWenku(id, { title });
      } else {
        // 未完成
      }
      const favored = this[type].find((it) => it.id === id);
      if (favored) {
        favored.title = title;
      }
    },
    async deleteFavored(type: 'web' | 'wenku' | 'local', id: string) {
      if (type === 'web') {
        await Locator.userRepository.deleteFavoredWeb(id);
      } else if (type === 'wenku') {
        await Locator.userRepository.deleteFavoredWenku(id);
      } else {
        // 未完成
      }
      this[type] = this[type].filter((it) => it.id !== id);
    },
  },
});
