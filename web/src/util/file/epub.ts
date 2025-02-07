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

  private constructor(
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
    if (!opfPath) return undefined;

    const opfDir = opfPath.substring(0, opfPath.lastIndexOf('/') + 1);
    const opf = await readEntryAsDocumentByFilename(opfPath);
    if (opf === undefined) return undefined;

    const resources: EpubResource[] = [];
    for (const entry of entries.values()) {
      if (entry.filename in ['mimetype', 'META-INF/container.xml']) {
        continue;
      }

      if (entry.filename.endsWith('xhtml')) {
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

export namespace Epub {
  export const forEachXHtmlFile = async (
    file: File,
    callback: (path: string, doc: Document) => void,
  ) => {
    const { BlobReader, TextWriter, ZipReader } = await import(
      '@zip.js/zip.js'
    );

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
      opf.querySelectorAll(
        "manifest > item[media-type='application/xhtml+xml']",
      ),
    ).map((it) => opfDir + it.getAttribute('href'))) {
      const doc = await readFileAsXHtml(xhtmlPath);
      callback(xhtmlPath, doc);
    }
    await reader.close();
  };

  export const modify = async (
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
}
