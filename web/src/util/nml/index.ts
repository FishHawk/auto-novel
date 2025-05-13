import { Epub } from '@/util/file';

export interface ReaderChapter {
  id: string;
  title: string;
  content: string[];
}

export interface ReaderVolume {
  id: string;
  title: string;
  chapters: ReaderChapter[];
}

const xhtmlToText = (node: Node): string => {
  let text = '';
  const xhtmlToTextInner = (node: Node) => {
    if (node.nodeType === Node.TEXT_NODE) {
      text += node.textContent?.replaceAll(/^[ \n]+|[ \n]+$/g, '') || '';
    } else if (node instanceof Element) {
      const tagName = node.tagName.toLowerCase();
      const isBlock = [
        'p',
        'div',
        'h1',
        'h2',
        'h3',
        'h4',
        'h5',
        'h6',
        'br',
      ].includes(tagName);
      const isSkip = ['rp', 'rt'].includes(tagName);
      if (isSkip) return;
      if (isBlock) text += '\n';
      node.childNodes.forEach((child) => {
        xhtmlToTextInner(child);
      });
    }
  };
  xhtmlToTextInner(node);
  return text;
};

export const parseEpub = (epub: Epub): ReaderVolume => {
  const chapters: ReaderChapter[] = [];

  const traverseToc = (navItems: typeof epub.navItems) => {
    for (const item of navItems) {
      if (item.href) {
        chapters.push({
          id: item.href,
          title: item.text,
          content: [],
        });
      }
      if (item.children.length > 0) {
        traverseToc(item.children);
      }
    }
  };
  traverseToc(epub.navItems);

  let index = -1;
  for (const item of epub.iterDocInSpine()) {
    if (index + 1 < chapters.length && item.href == chapters[index + 1].id) {
      index += 1;
    }
    if (index >= 0) {
      const doc = item.doc.cloneNode(true) as Document;
      Array.from(item.doc.getElementsByTagName('rt')).forEach((node) =>
        node.parentNode!.removeChild(node),
      );
      Array.from(doc.getElementsByTagName('rp')).forEach((node) =>
        node.parentNode!.removeChild(node),
      );

      console.log(
        Array.from(doc.body.getElementsByTagName('p'))
          .map((el) => el.innerText)
          .filter((it) => it.trim().length !== 0),
      );
      const text = xhtmlToText(doc.body);
      console.log(text);
      if (text) {
        chapters[index].content.push(...text.split('\n'));
      }
    }
  }
  console.log(chapters);

  return {
    id: epub.name,
    title: epub.name,
    chapters,
  };
};
