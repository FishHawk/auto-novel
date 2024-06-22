export interface Favored {
  id: string;
  title: string;
}

export interface FavoredList {
  web: Favored[];
  wenku: Favored[];
  local: Favored[];
}
