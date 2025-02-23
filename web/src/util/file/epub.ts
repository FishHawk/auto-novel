type EpubResource = {
  id: string;
  href: string;
  blob: Blob;
};

const templateMimetype = 'application/epub+zip';
const templateContainer = `<?xml version="1.0" encoding="utf-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>`;

export class Epub {
  type = 'epub' as const;
  name: string;
  opfDir: string;
  opf: Document;
  resources: EpubResource[];

  constructor(
    name: string,
    opfDir: string,
    opf: Document,
    resources: EpubResource[],
  ) {
    this.name = name;
    this.opfDir = opfDir;
    this.opf = opf;
    this.resources = resources;
  }

  static async fromFile(file: File) {
    const { BlobReader, BlobWriter, ZipReader, TextWriter } = await import(
      '@zip.js/zip.js'
    );
    const reader = new ZipReader(new BlobReader(file));
    const entries = new Map(
      (await reader.getEntries()).map((obj) => [obj.filename, obj] as const),
    );

    const readEntryAsDocument = async (filename: string) => {
      const entry = entries.get(filename);
      if (entry === undefined) return undefined;
      const text = await entry.getData!(new TextWriter());
      const parser = new DOMParser();
      return parser.parseFromString(text, 'application/xhtml+xml');
    };

    const opfPath = (await readEntryAsDocument('META-INF/container.xml'))
      ?.getElementsByTagName('rootfile')
      .item(0)
      ?.getAttribute('full-path');
    if (!opfPath) throw '无法获取OPF路径';

    const opfDir = opfPath.substring(0, opfPath.lastIndexOf('/') + 1);
    const opf = await readEntryAsDocument(opfPath);
    if (opf === undefined) throw '无法读取OPF文件';

    const manifest = opf.getElementsByTagName('manifest').item(0);
    if (manifest === null) throw '无法读取manifest';

    const resources: EpubResource[] = [];
    for (const item of Array.from(manifest.getElementsByTagName('item'))) {
      const id = item.getAttribute('id');
      if (id === null) throw 'manifest的item缺少id字段';
      const href = item.getAttribute('href');
      if (href === null) throw 'manifest的item缺少href字段';
      const mediaType = item.getAttribute('media-type');
      if (mediaType === null) throw 'manifest的item缺少media-type字段';

      const entry = entries.get(opfDir + href);
      if (entry === undefined) throw '文件缺失';
      console.log(opfDir + href);

      const blob = new Blob([await entry.getData!(new BlobWriter())], {
        type: mediaType,
      });
      resources.push({ id, href, blob });
    }
    return new Epub(file.name, opfDir, opf, resources);
  }

  async toBlob() {
    const { BlobReader, BlobWriter, ZipWriter, TextReader } = await import(
      '@zip.js/zip.js'
    );
    const zipBlobWriter = new BlobWriter();
    const writer = new ZipWriter(zipBlobWriter);

    const writeText = (path: string, text: string) =>
      writer.add(path, new TextReader(text));
    const writeDoc = (path: string, doc: Document) =>
      writer.add(path, new TextReader(doc.documentElement.outerHTML));
    const writeBlob = (path: string, blob: Blob) =>
      writer.add(path, new BlobReader(blob));

    await writeText('mimetype', templateMimetype);
    await writeText(
      'META-INF/container.xml',
      templateContainer.replace('OEBPS/', this.opfDir),
    );
    await writeDoc(this.opfDir + 'content.opf', this.opf);

    for (const res of this.resources) {
      await writeBlob(res.href, res.blob);
    }

    await writer.close();

    const zipBlob = await zipBlobWriter.getData();
    return zipBlob;
  }

  async *iter(...mediaTypes: string[]) {
    for (const res of this.resources) {
      if (mediaTypes.includes(res.blob.type)) {
        yield res;
      }
    }
  }

  async *iterDoc() {
    const parser = new DOMParser();
    for await (const res of this.iter('application/xhtml+xml')) {
      const text = await res.blob.text();
      const doc = parser.parseFromString(text, 'application/xhtml+xml');
      yield [res, doc] as [EpubResource, Document];
    }
  }

  async *iterImage() {
    return this.iter(
      'image/gif',
      'image/jpeg',
      'image/png',
      'image/svg+xml',
      'image/webp',
    );
  }

  async getText() {
    const contents: string[] = [];
    for await (const [_, doc] of this.iterDoc()) {
      Array.from(doc.getElementsByClassName('rt')).forEach((node) =>
        node.parentNode!!.removeChild(node),
      );
      contents.push(doc.body.textContent ?? '');
    }
    return contents.join('\n');
  }

  // async updateLinks(
  //   old_path: string,
  //   new_path: string,
  //   new_media_type?: string,
  // ) {
  //   // 处理opf的更新
  //   this.opf.querySelectorAll(`manifest > item`).forEach((item) => {
  //     const href = item.getAttribute('href');
  //     if (href !== null) {
  //       if (
  //         new URL(href, `files:/${this.opfPath}`).href === `files:/${old_path}`
  //       ) {
  //         // 用URL获取href的绝对路径
  //         item.setAttribute('href', getRelativePath(this.opfPath, new_path));
  //         if (new_media_type) {
  //           item.setAttribute('media-type', new_media_type);
  //         }
  //         console.log(item.getAttribute('href'));
  //       }
  //     }
  //   });
  //   // 处理其它资源的更新
  //   for await (const [res, doc] of this.iterDoc()) {
  //     doc
  //       .querySelectorAll(`manifest > link[href], img[src]`)
  //       .forEach((item) => {
  //         let href = item.getAttribute('href');
  //         if (href !== null) {
  //           if (
  //             new URL(href, `files:/${res.href}`).href === `files:/${old_path}`
  //           ) {
  //             item.setAttribute('href', getRelativePath(res.href, new_path));
  //           }
  //         }
  //         let src = item.getAttribute('src');
  //         if (src !== null) {
  //           if (
  //             new URL(src, `files:/${res.href}`).href === `files:/${old_path}`
  //           ) {
  //             item.setAttribute('src', getRelativePath(res.href, new_path));
  //           }
  //         }
  //       });
  //   }
  // }
}

/**
 * 通过两文件的绝对路径获取相对路径
 *
 * 示例 fromPath: `'OEBPS/Text/001.html'`, toPath: `'OEBPS/Image/001.png'`, 则输出为 `"../Image/001.png"`
 */
export const getRelativePath = (fromPath: string, toPath: string) => {
  const fromPaths = new URL('files:/' + fromPath).pathname.split('/');
  const toPaths = new URL('files:/' + toPath).pathname.split('/');
  // Remove empty segments caused by leading slashes
  if (fromPaths[0] === '') fromPaths.shift();
  if (toPaths[0] === '') toPaths.shift();
  // Find the common base path
  let commonLength = 0;
  while (
    commonLength < fromPaths.length &&
    commonLength < toPaths.length &&
    fromPaths[commonLength] === toPaths[commonLength]
  ) {
    commonLength++;
  }
  // Calculate the relative path
  const upLevels = fromPaths.length - commonLength - 1;
  const downLevels = toPaths.slice(commonLength);
  const relativePath = '../'.repeat(upLevels) + downLevels.join('/');
  return relativePath;
};
