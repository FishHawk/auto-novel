import { MenuOption } from 'naive-ui';
import { h } from 'vue';
import { RouterLink } from 'vue-router';

const menuOption = (text: string, href: string): MenuOption => ({
  label: () => h(RouterLink, { to: href }, text),
  key: href,
});

export const menuOptions = [
  menuOption('网络小说', '/novel-list'),
  menuOption('成为小说家：流派', '/novel-rank/syosetu/1'),
  menuOption('成为小说家：综合', '/novel-rank/syosetu/2'),
  menuOption('成为小说家：异世界转移/转生', '/novel-rank/syosetu/3'),
  menuOption('Kakuyomu：流派', '/novel-rank/kakuyomu/1'),
];
