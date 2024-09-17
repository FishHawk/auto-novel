import { Epub } from './epub';
import { Srt } from './srt';
import { Txt } from './txt';

export { Epub };
export { Srt };
export { Txt };

export const getFullContent = async (file: File) => {
  if (file.name.endsWith('.txt') || file.name.endsWith('.srt')) {
    return await Txt.readContent(file);
  } else if (file.name.endsWith('.epub')) {
    const contents: string[] = [];
    await Epub.forEachXHtmlFile(file, (_path, doc) => {
      Array.from(doc.getElementsByClassName('rt')).forEach((node) =>
        node.parentNode!!.removeChild(node),
      );
      contents.push(doc.body.textContent ?? '');
    });
    return contents.join('\n');
  } else {
    return '';
  }
};
