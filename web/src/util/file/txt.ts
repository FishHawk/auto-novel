const readContent = async (file: File) => {
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

const writeContent = (lines: string[]) => {
  return new Blob([lines.join('\n')], {
    type: 'text/plain',
  });
};

export const Txt = {
  readContent,
  writeContent,
};
