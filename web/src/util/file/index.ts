import { Epub } from './epub';
import { Srt } from './srt';
import { Txt } from './txt';

export { Epub };
export { Srt };
export { Txt };

export const getFullContent = async (file: File) => {
  if (file.name.endsWith('.txt') || file.name.endsWith('.srt')) {
    const txt = await Txt.fromFile(file);
    return txt.text;
  } else if (file.name.endsWith('.epub')) {
    const epub = await Epub.fromFile(file);
    return epub.resources
      .filter((it) => it.type === 'doc')
      .map((it) => {
        Array.from(it.doc.getElementsByClassName('rt')).forEach((node) =>
          node.parentNode!!.removeChild(node),
        );
        return it.doc.body.textContent ?? '';
      })
      .join('\n');
  } else {
    return '';
  }
};

export const parseFile = async (
  file: File,
  allowExts = ['epub', 'txt', 'srt'],
) => {
  const ext = file.name.split('.').pop()?.toLowerCase();
  if (ext === undefined) throw '无法获取文件后缀名';
  if (ext in allowExts) {
    try {
      if (ext === 'txt') {
        return await Txt.fromFile(file);
      } else if (ext === 'epub') {
        return await Epub.fromFile(file);
      } else if (ext === 'srt') {
        return await Srt.fromFile(file);
      }
    } catch (e) {
      throw `无法解析${ext.toUpperCase()}文件，因为:${e}`;
    }
  }
  throw '不支持的文件格式';
};
