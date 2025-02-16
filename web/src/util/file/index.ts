import { Epub } from './epub';
import { Srt } from './srt';
import { Txt } from './txt';

export { Epub };
export { Srt };
export { Txt };
export type ParsedFile = Epub | Srt | Txt;

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
  if (allowExts.includes(ext)) {
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

/**
 * 通过两文件的绝对路径获取相对路径
 *
 * 示例 fromPath: `'OEBPS/Text/001.html'`, toPath: `'OEBPS/Image/001.png'`, 则输出为 `"../Image/001.png"`
 */
export const getRelativePath = (fromPath: string, toPath: string) => {
  const fromPaths = new URL('files:/' + fromPath).pathname.split('/');
  const toPaths = new URL('files:/' + toPath).pathname.split('/');
  // Remove empty segments caused by leading slashes
  if (fromPaths[0] === '') fromPaths.shift();
  if (toPaths[0] === '') toPaths.shift();
  // Find the common base path
  let commonLength = 0;
  while (
    commonLength < fromPaths.length &&
    commonLength < toPaths.length &&
    fromPaths[commonLength] === toPaths[commonLength]
  ) {
    commonLength++;
  }
  // Calculate the relative path
  const upLevels = fromPaths.length - commonLength - 1;
  const downLevels = toPaths.slice(commonLength);
  const relativePath = '../'.repeat(upLevels) + downLevels.join('/');
  return relativePath;
};
