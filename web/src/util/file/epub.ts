import type { Entry } from '@zip.js/zip.js';

type EpubResource = { path: string } & (
  | { type: 'doc'; doc: Document }
  | { type: 'image' | 'other'; blob: Blob }
);

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

    const readEntryAsDocument = async (entry: Entry) => {
      const text = await entry.getData!(new TextWriter());
      const parser = new DOMParser();
      return parser.parseFromString(text, 'application/xhtml+xml');
    };
    const readEntryAsDocumentByFilename = async (filename: string) => {
      const entry = entries.get(filename);
      if (entry === undefined) {
        return undefined;
      }
      return readEntryAsDocument(entry);
    };

    const opfPath = (
      await readEntryAsDocumentByFilename('META-INF/container.xml')
    )
      ?.getElementsByTagName('rootfile')
      .item(0)
      ?.getAttribute('full-path');
    if (!opfPath) throw '无法获取OPF路径';

    const opfDir = opfPath.substring(0, opfPath.lastIndexOf('/') + 1);
    const opf = await readEntryAsDocumentByFilename(opfPath);
    if (opf === undefined) throw '无法读取OPF文件';

    const resources: EpubResource[] = [];
    for (const entry of entries.values()) {
      if (entry.filename in ['mimetype', 'META-INF/container.xml']) {
        continue;
      }

      if (entry.filename.endsWith('xhtml') || entry.filename.endsWith('html')) {
        resources.push({
          path: entry.filename,
          type: 'doc',
          doc: await readEntryAsDocument(entry),
        });
      } else {
        resources.push({
          path: entry.filename,
          type: 'other',
          blob: await entry.getData!(new BlobWriter()),
        });
      }
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
      templateContainer.replace('OEBPS/content.opf', this.opfDir),
    );
    await writeDoc(this.opfDir, this.opf);

    for (const res of this.resources) {
      if (res.type === 'doc') {
        await writeDoc(res.path, res.doc);
      } else {
        await writeBlob(res.path, res.blob);
      }
    }

    await writer.close();

    const zipBlob = await zipBlobWriter.getData();
    return zipBlob;
  }

  getText() {
    const contents: string[] = [];
    for (const res of this.resources) {
      if (res.type === 'doc') {
        Array.from(res.doc.getElementsByClassName('rt')).forEach((node) =>
          node.parentNode!!.removeChild(node),
        );
        contents.push(res.doc.body.textContent ?? '');
      }
    }
    return contents.join('\n');
  }
}
