import { Epub, ParsedFile, Txt } from '@/util/file';

const convertEpubToTxt = async (epub: Epub) => {
  return new Txt(epub.name.replace(/\.epub$/i, '.txt'), await epub.getText());
};

export namespace Toolbox {
  export const convertToTxt = async (files: ParsedFile[]) => {
    const newFiles: ParsedFile[] = [];
    for (const file of files) {
      if (file.type === 'epub') {
        newFiles.push(await convertEpubToTxt(file));
      } else {
        newFiles.push(file);
      }
    }
    return newFiles;
  };
}
