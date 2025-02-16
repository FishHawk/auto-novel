import { Epub, ParsedFile, Srt, Txt } from '@/util/file';

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

export namespace Toolbox {
  export const apply = (files: ParsedFile[], handles: FileHandles) => {
    for (const file of files) {
      const handle = handles[file.type];
      if (handle) handle(file as any);
    }
  };

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
