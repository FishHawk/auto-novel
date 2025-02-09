import { RegexUtil } from '@/util';
import { Epub, Srt, Txt } from '@/util/file';

const fixOcrTxt = (txt: Txt) => {
  const endsCorrectly = (s: string) => {
    if (s.length === 0) {
      return true;
    }
    const lastChar = s.charAt(s.length - 1);
    if (
      RegexUtil.hasHanzi(lastChar) ||
      RegexUtil.hasKanaChars(lastChar) ||
      RegexUtil.hasHangulChars(lastChar) ||
      RegexUtil.hasEnglishChars(lastChar)
    ) {
      return false;
    } else {
      return true;
    }
  };

  const lines: string[] = [];
  let lineProcessing = '';
  for (let line of txt.text.split('\n')) {
    if (lineProcessing.length > 0) {
      line = lineProcessing + line.trim();
      lineProcessing = '';
    } else {
      line = line.trimEnd();
    }
    if (endsCorrectly(line)) {
      lines.push(line);
    } else {
      lineProcessing = line;
    }
  }
  if (lineProcessing.length > 0) {
    lines.push(lineProcessing);
  }
  txt.text = lines.join('\n');
};

const convertEpubToTxt = (epub: Epub) => {
  return new Txt(epub.name.replace(/\.epub$/i, '.txt'), epub.getText());
};

type UnpackFile = Txt | Epub | Srt;
type FileHandle<T> = (file: T) => void;
type FileHandles = {
  txt?: FileHandle<Txt>;
  epub?: FileHandle<Epub>;
  srt?: FileHandle<Srt>;
};

const toolboxActionWarp = (handles: FileHandles) => {
  return (files: UnpackFile[]) => {
    for (const file of files) {
      const handle = handles[file.type];
      if (handle) handle(file as any);
    }
  };
};

export namespace Toolbox {
  export const fixOcr = toolboxActionWarp({
    txt: fixOcrTxt,
  });

  export const convertToTxt = (files: UnpackFile[]) => {
    return files.map((file) => {
      if (file.type === 'epub') {
        return convertEpubToTxt(file);
      } else {
        return file;
      }
    });
  };
}
