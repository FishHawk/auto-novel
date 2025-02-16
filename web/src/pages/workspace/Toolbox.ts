import { Epub, ParsedFile, Txt } from '@/util/file';

const convertEpubToTxt = (epub: Epub) => {
  return new Txt(epub.name.replace(/\.epub$/i, '.txt'), epub.getText());
};

export namespace Toolbox {
  export const convertToTxt = (files: ParsedFile[]) => {
    return files.map((file) => {
      if (file.type === 'epub') {
        return convertEpubToTxt(file);
      } else {
        return file;
      }
    });
  };
}
