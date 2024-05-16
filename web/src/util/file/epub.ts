const forEachXHtmlFile = async (
  file: File,
  callback: (path: string, doc: Document) => void,
) => {
  const { BlobReader, TextWriter, ZipReader } = await import('@zip.js/zip.js');

  const reader = new ZipReader(new BlobReader(file));
  const entries = new Map(
    (await reader.getEntries()).map((obj) => [obj.filename, obj]),
  );
  const readFileAsXHtml = async (filename: string) => {
    const entry = entries.get(filename);
    if (entry === undefined) throw Error('EPUB格式不合法');
    const text = await entry.getData!(new TextWriter());
    const parser = new DOMParser();
    const doc = parser.parseFromString(text, 'application/xhtml+xml');
    return doc;
  };

  const xmlContainer = await readFileAsXHtml('META-INF/container.xml');
  const opfPath = xmlContainer
    .getElementsByTagName('rootfile')
    .item(0)!!
    .getAttribute('full-path')!!;
  const opfDir = opfPath.substring(0, opfPath.lastIndexOf('/') + 1);

  const opf = await readFileAsXHtml(opfPath);
  for (const xhtmlPath of Array.from(
    opf.querySelectorAll("manifest > item[media-type='application/xhtml+xml']"),
  ).map((it) => opfDir + it.getAttribute('href'))) {
    const doc = await readFileAsXHtml(xhtmlPath);
    callback(xhtmlPath, doc);
  }
  await reader.close();
};

const modify = async (
  file: File,
  callback: (path: string, blobIn: Blob) => Promise<Blob>,
) => {
  const { BlobReader, BlobWriter, ZipReader, ZipWriter } = await import(
    '@zip.js/zip.js'
  );

  // 准备读Epub
  const reader = new ZipReader(new BlobReader(file));
  const entriesFilenameSet = new Set<string>();
  const entries = (await reader.getEntries())
    .filter((it) => !it.directory)
    // 如果文件名重复，则保留后压缩的文件
    .reverse()
    .filter((it) => {
      if (entriesFilenameSet.has(it.filename)) {
        return false;
      } else {
        entriesFilenameSet.add(it.filename);
        return true;
      }
    })
    .sort((e1, e2) => {
      const pathToNumber = (path: string) => {
        if (path === 'mimetype') return 3;
        else if (path === 'META-INF/container.xml') return 2;
        else if (path.endsWith('opf')) return 1;
        else return 0;
      };
      const n1 = pathToNumber(e1.filename);
      const n2 = pathToNumber(e2.filename);
      if (n1 === n2) {
        return e1.filename.localeCompare(e2.filename);
      } else {
        return n2 - n1;
      }
    });

  // 准备写Epub
  const zipBlobWriter = new BlobWriter();
  const writer = new ZipWriter(zipBlobWriter);

  // 遍历所有文件并修改
  for (const entry of entries) {
    const path = entry.filename;
    const blobIn = await entry.getData!(new BlobWriter());
    const blobOut = await callback(path, blobIn);
    await writer.add(path, new BlobReader(blobOut));
  }

  await reader.close();
  await writer.close();

  const zipBlob = await zipBlobWriter.getData();
  return zipBlob;
};

export const Epub = {
  forEachXHtmlFile,
  modify,
};
