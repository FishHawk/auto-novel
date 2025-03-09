import { BaseFile } from './base';

const MIMETYPE_PATH = 'mimetype';
const MIMETYPE_TEMPLATE = 'application/epub+zip';

const CONTAINER_PATH = 'META-INF/container.xml';
const CONTAINER_TEMPLATE = `<?xml version="1.0" encoding="utf-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>`;

const getEl = (doc: Document, tagName: string) =>
  doc.getElementsByTagName(tagName).item(0);

const MIME = {
  IMAGE: [
    'image/gif',
    'image/jpeg',
    'image/png',
    'image/svg+xml',
    'image/webp',
  ],
  CSS: ['text/css'],
};

type EpubItem = {
  id: string;
  href: string;
  mediaType: string;
  overlay: string | null;
  properties: string | null;
  fallback: string | null;
} & ({ doc: Document } | { blob: Blob });

export class Epub extends BaseFile {
  type = 'epub' as const;
  private packagePath: string = '';
  packageDoc!: Document;
  private items = new Map<string, EpubItem>();

  private resolve(path: string) {
    const dir = this.packagePath.substring(
      0,
      this.packagePath.lastIndexOf('/') + 1,
    );
    return dir + path;
  }

  private updateHref(oldHref: string, newHref: string) {
    // TODO
  }

  // ==============================
  // 读取文件内容
  // ==============================

  private parseContainer(doc: Document) {
    const rootfile = getEl(doc, 'rootfile');
    if (!rootfile) throw new Error('Container does not have rootfile');
    const packagePath = rootfile.getAttribute('full-path');
    if (!packagePath) throw new Error('Container does not have package path');
    this.packagePath = packagePath;
  }

  private parsePackage(doc: Document) {
    const metadata = getEl(doc, 'metadata');
    if (!metadata) throw new Error('Package does not have metadata');
    const manifest = getEl(doc, 'manifest');
    if (!manifest) throw new Error('Package does not have manifest');
    const spine = getEl(doc, 'spine');
    if (!spine) throw new Error('Package does not have spine');

    this.packageDoc = doc;
    this.parseManifest(manifest);
  }

  private parseManifest(el: Element) {
    for (const itemEl of Array.from(el.getElementsByTagName('item'))) {
      const id = itemEl.getAttribute('id');
      if (!id) throw new Error('Manifest item does not have id');
      const href = itemEl.getAttribute('href');
      if (!href) throw new Error('Manifest item does not have href');
      const mediaType = itemEl.getAttribute('media-type');
      if (!mediaType) throw new Error('Manifest item does not have media type');

      const itemCommon = {
        id,
        href,
        mediaType,
        overlay: itemEl.getAttribute('media-overlay'),
        properties: itemEl.getAttribute('properties'),
        fallback: itemEl.getAttribute('fallback'),
      };

      if (mediaType === 'application/xhtml+xml') {
        this.items.set(id, { ...itemCommon, doc: undefined as any });
      } else {
        this.items.set(id, { ...itemCommon, blob: undefined as any });
      }
    }
  }

  private async parseFile(file: File) {
    const { BlobReader, BlobWriter, ZipReader, TextWriter } = await import(
      '@zip.js/zip.js'
    );
    const reader = new ZipReader(new BlobReader(file));
    const entries = new Map(
      (await reader.getEntries()).map((obj) => [obj.filename, obj] as const),
    );

    const readDoc = async (path: string) => {
      const entry = entries.get(path);
      if (!entry) throw new Error(`Entry not found: ${path}`);
      const text = await entry.getData!(new TextWriter());
      const parser = new DOMParser();
      return parser.parseFromString(text, 'application/xhtml+xml');
    };

    const readBlob = async (path: string, type: string) => {
      const entry = entries.get(path);
      if (!entry) throw new Error(`Entry not found: ${path}`);
      const data = await entry.getData!(new BlobWriter());
      return new Blob([data], { type });
    };

    this.parseContainer(await readDoc(CONTAINER_PATH));
    this.parsePackage(await readDoc(this.packagePath));

    for (const item of this.items.values()) {
      const path = this.resolve(item.href);
      if ('doc' in item) {
        item.doc = await readDoc(path);
      } else {
        item.blob = await readBlob(path, item.mediaType);
      }
    }
  }

  static async fromFile(file: File) {
    const epub = new Epub(file.name, file);
    await epub.parseFile(file);
    return epub;
  }

  async clone() {
    if (!this.rawFile)
      throw new Error('Cannot clone manually constructed file.');
    return Epub.fromFile(this.rawFile);
  }

  // ==============================
  // 将内容写入文件
  // ==============================

  private fixHrefExtension() {
    const getFileExtension = (path: string) => {
      const match = path.match(/\.([a-zA-Z0-9]+)$/);
      if (match) return match[1];
      return '';
    };

    const mimeToExtensions: { [key: string]: string[] } = {
      'image/gif': ['.gif'],
      'image/jpeg': ['.jpg', '.jpeg', '.jpe', '.jif', '.jfif'],
      'image/png': ['.png'],
      'image/svg+xml': ['.svg'],
      'image/webp': ['.webp'],
    };
    for (const item of this.items.values()) {
      const mime = item.mediaType;
      if (mime in mimeToExtensions) {
        const extensions = mimeToExtensions[mime];
        const ext = getFileExtension(item.href);
        if (!extensions.includes(ext)) {
          const newHref = item.href.replace(/\.([a-zA-Z0-9]+)$/, extensions[0]);
          this.updateHref(item.href, newHref);
          item.href = newHref;
        }
      }
    }
  }

  private updatePackage() {
    const items = [...this.items.values()].map((item) => {
      const itemEl = document.createElement('item');
      itemEl.setAttribute('id', item.id);
      itemEl.setAttribute('href', item.href);
      itemEl.setAttribute('media-type', item.mediaType);
      if (item.overlay) itemEl.setAttribute('media-overlay', item.overlay);
      if (item.properties) itemEl.setAttribute('properties', item.properties);
      if (item.fallback) itemEl.setAttribute('fallback', item.fallback);
      return itemEl;
    });
    const manifest = getEl(this.packageDoc, 'manifest')!;
    manifest.replaceChildren(...items);
  }

  async toBlob() {
    this.fixHrefExtension();
    this.updatePackage();

    const { BlobReader, BlobWriter, ZipWriter, TextReader } = await import(
      '@zip.js/zip.js'
    );

    const zipBlobWriter = new BlobWriter();
    const writer = new ZipWriter(zipBlobWriter);

    const writeText = (path: string, text: string) =>
      writer.add(path, new TextReader(text));
    const writeBlob = (path: string, blob: Blob) =>
      writer.add(path, new BlobReader(blob));
    const writeDoc = (path: string, doc: Document) =>
      writeText(path, new XMLSerializer().serializeToString(doc));

    await writeText(MIMETYPE_PATH, MIMETYPE_TEMPLATE);
    await writeText(
      CONTAINER_PATH,
      CONTAINER_TEMPLATE.replace('OEBPS/content.opf', this.packagePath),
    );
    await writeDoc(this.packagePath, this.packageDoc);

    for (const item of this.items.values()) {
      const path = this.resolve(item.href);
      if ('doc' in item) {
        await writeDoc(path, item.doc);
      } else {
        await writeBlob(path, item.blob);
      }
    }

    await writer.close();

    const zipBlob = await zipBlobWriter.getData();
    return zipBlob;
  }

  // ==============================
  // API
  // ==============================

  iterDoc() {
    return [...this.items.values()].filter((item) => 'doc' in item);
  }
  iterBlob(mediaTypes: string[]) {
    return [...this.items.values()]
      .filter((item) => 'blob' in item)
      .filter((item) => mediaTypes.includes(item.mediaType));
  }
  iterImage() {
    return this.iterBlob(MIME.IMAGE);
  }

  updateImage(id: string, blob: Blob) {
    if (!MIME.IMAGE.includes(blob.type)) return;

    const item = this.items.get(id);
    if (!item || !('blob' in item) || !MIME.IMAGE.includes(item.mediaType))
      return;

    item.mediaType = blob.type;
    item.blob = blob;
  }

  cleanStyle() {
    for (const item of this.iterBlob(MIME.CSS)) {
      item.blob = new Blob([''], { type: item.mediaType });
    }
  }

  getText() {
    const contents: string[] = [];
    for (const item of this.iterDoc()) {
      Array.from(item.doc.getElementsByClassName('rt')).forEach((node) =>
        node.parentNode!!.removeChild(node),
      );
      contents.push(item.doc.body.textContent ?? '');
    }
    return contents.join('\n');
  }
}
