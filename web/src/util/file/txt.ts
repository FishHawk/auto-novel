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

export namespace Txt {
  export const readContent = async (file: File) => {
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

    let content: string | undefined;
    for (const label of ['utf-8', 'gbk']) {
      content = await tryDecode(label);
      if (content !== undefined) break;
    }
    if (content === undefined) {
      throw '未知编码';
    }

    return content;
  };

  export const writeContent = (lines: string[]) => {
    return new Blob([lines.join('\n')], {
      type: 'text/plain',
    });
  };
}
