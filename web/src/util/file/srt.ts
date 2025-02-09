import { Txt } from './txt';

interface SrtSubtitle {
  id: string;
  time: string;
  text: string[];
}

export class Srt {
  type = 'srt' as const;
  name: string;
  subtitles: SrtSubtitle[];

  private constructor(name: string, subtitles: SrtSubtitle[]) {
    this.name = name;
    this.subtitles = subtitles;
  }

  static async fromFile(file: File) {
    const txt = await Txt.fromFile(file);
    const blocks = dividerByEmptyLine(txt.text.split('\n'));
    const subtitles = blocks
      .filter((block) => block.length >= 3)
      .map(
        (block) =>
          <SrtSubtitle>{
            id: block[0],
            time: block[1],
            text: block.slice(2),
          },
      );
    return new Srt(file.name, subtitles);
  }

  async toBlob() {
    const lines = this.subtitles.flatMap((s) => [s.id, s.time, ...s.text, '']);
    return new Blob([lines.join('\n')], {
      type: 'text/plain',
    });
  }

  // There are extenions that are supported in some applications that allow the formatting of the "subtitle text".
  // One could use various html formatting tags in the following manner:
  //  ___________________________________________________
  // | <b>text</b>                     | bold text       |
  // |---------------------------------------------------|
  // | <i>italics</i>                  | italicized text |
  // |---------------------------------------------------|
  // | <font color="#ff00ff"> </font>  | text color      |
  //  ---------------------------------------------------
  static cleanFormat(text: string) {
    return text
      .replaceAll('<b>', '')
      .replaceAll('</b>', '')
      .replaceAll('<i>', '')
      .replaceAll('</i>', '')
      .replaceAll(/<font\s+color\s*=\s*(['"]?)(.*?)(['"]?)\s*>/g, '')
      .replaceAll('</font>', '');
  }
}

const dividerByEmptyLine = (lines: string[]) => {
  const blocks = [];
  let block = [];
  for (const line of lines) {
    if (line.trim().length !== 0) {
      block.push(line);
    } else if (block.length > 0) {
      blocks.push(block);
      block = [];
    }
  }
  if (block.length > 0) {
    blocks.push(block);
  }
  return blocks;
};
