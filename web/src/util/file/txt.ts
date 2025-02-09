export class Txt {
  type = 'txt' as const;
  name: string;
  text: string;

  private constructor(name: string, text: string) {
    this.name = name;
    this.text = text;
  }

  static async fromFile(file: File) {
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

    return new Txt(file.name, text);
  }

  async toBlob() {
    return new Blob([this.text], {
      type: 'text/plain',
    });
  }
}
