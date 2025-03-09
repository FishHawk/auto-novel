import { BaseFile } from './base';

export class Txt extends BaseFile {
  type = 'txt' as const;
  text: string = '';

  private async parseFile(file: File) {
    const buffer = await file.arrayBuffer();

    const tryDecode = async (label: string) => {
      const decoder = new TextDecoder(label, { fatal: true });
      try {
        const decoded = decoder.decode(buffer);
        return decoded;
      } catch (e) {
        if (e instanceof TypeError) return undefined;
        throw e;
      }
    };

    let text: string | undefined;
    for (const label of ['utf-8', 'gbk']) {
      text = await tryDecode(label);
      if (text !== undefined) break;
    }
    if (text === undefined) {
      throw '未知编码';
    }

    // 修复换行符格式
    text = text.replaceAll('\r\n', '\n').replaceAll('\r', '\n');

    this.text = text;
  }

  static async fromFile(file: File) {
    const txt = new Txt(file.name, file);
    await txt.parseFile(file);
    return txt;
  }

  static async fromText(name: string, text: string) {
    const txt = new Txt(name);
    txt.text = text;
    return txt;
  }

  async clone() {
    if (!this.rawFile)
      throw new Error('Cannot clone manually constructed file.');
    return Txt.fromFile(this.rawFile);
  }

  async toBlob() {
    return new Blob([this.text], {
      type: 'text/plain',
    });
  }
}
