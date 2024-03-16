import { Txt } from './txt';

interface SrtSubtitle {
  id: string;
  time: string;
  text: string[];
}

const dividerByEmptyLine = (lines: string[]) => {
  const blocks = [];
  let block = [];
  for (const line of lines) {
    if (line.length !== 0) {
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

const readContent = async (file: File) => {
  const content = await Txt.readContent(file);
  const blocks = dividerByEmptyLine(content.split('\n'));
  return blocks
    .filter((block) => block.length >= 3)
    .map(
      (block) =>
        <SrtSubtitle>{
          id: block[0],
          time: block[1],
          text: block.slice(2),
        }
    );
};

const writeContent = (subtitles: SrtSubtitle[]) => {
  const lines = subtitles.flatMap((s) => [s.id, s.time, ...s.text, '']);
  return Txt.writeContent(lines);
};

// There are extenions that are supported in some applications that allow the formatting of the "subtitle text".
// One could use various html formatting tags in the following manner:
//  ___________________________________________________
// | <b>text</b>                     | bold text       |
// |---------------------------------------------------|
// | <i>italics</i>                  | italicized text |
// |---------------------------------------------------|
// | <font color="#ff00ff"> </font>  | text color      |
//  ---------------------------------------------------
const cleanFormat = (text: string) =>
  text
    .replaceAll('<b>', '')
    .replaceAll('</b>', '')
    .replaceAll('<i>', '')
    .replaceAll('</i>', '')
    .replaceAll(/<font\s+color\s*=\s*(['"]?)(.*?)(['"]?)\s*>/g, '')
    .replaceAll('</font>', '');

export const Srt = {
  readContent,
  writeContent,
  cleanFormat,
};
