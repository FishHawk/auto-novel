import type { Entry } from '@zip.js/zip.js';
import { getRelativePath } from '.';

type DocResource = { path: string; type: 'doc'; doc: Document };
type ImageResource = { path: string; type: 'image'; blob: Blob; url: string };
type OtherResource = { path: string; type: 'other'; blob: Blob };
type EpubResource = DocResource | ImageResource | OtherResource;

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
  opfPath: string;
  opf: Document;
  resources: EpubResource[];

  constructor(
    name: string,
    opfDir: string,
    opfPath: string,
    opf: Document,
    resources: EpubResource[],
  ) {
    this.name = name;
    this.opfDir = opfDir;
    this.opfPath = opfPath;
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

    // 解析 OPF 文件的 manifest，构建文件路径到 media-type 的映射
    const manifest = opf.getElementsByTagName('manifest')[0];
    const mediaTypeMap = new Map<string, string>();
    if (manifest) {
      for (const item of Array.from(manifest.getElementsByTagName('item'))) {
        const href = item.getAttribute('href');
        const mediaType = item.getAttribute('media-type');
        if (href && mediaType) {
          const fullPath = opfDir + href;
          mediaTypeMap.set(fullPath, mediaType);
        }
      }
    }
    const resources: EpubResource[] = [];
    for (const entry of entries.values()) {
      if (
        ['mimetype', 'META-INF/container.xml'].includes(entry.filename) ||
        opfPath.endsWith(entry.filename)
      ) {
        continue;
      }
      if (entry.filename.match(/\.(xhtml|html)$/i)) {
        resources.push({
          path: entry.filename,
          type: 'doc',
          doc: await readEntryAsDocument(entry),
        });
      } else {
        const mediaType =
          mediaTypeMap.get(entry.filename) || 'application/octet-stream';
        const blob = new Blob([await entry.getData!(new BlobWriter())], {
          type: mediaType,
        });
        if (entry.filename.match(/\.(jpg|jpeg|png|webp)$/i)) {
          resources.push({
            path: entry.filename,
            type: 'image',
            blob,
            url: URL.createObjectURL(blob),
          });
        } else {
          resources.push({
            path: entry.filename,
            type: 'other',
            blob,
          });
        }
      }
    }

    return new Epub(file.name, opfDir, opfPath, opf, resources);
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

    await writeText(
      'META-INF/container.xml',
      templateContainer.replace('OEBPS/content.opf', this.opfPath),
    );
    await writeText('mimetype', templateMimetype);
    await writeDoc(this.opfPath, this.opf); //TODO：应该根据Resources自动构建opf

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

  getImages() {
    const results = [];
    for (const e of this.resources) {
      if (e.type === 'image') {
        results.push(e);
      }
    }
    // 根据文件名排序，非数字开头的文件优先，其次按自然数顺序排序
    const collator = new Intl.Collator(undefined, {
      numeric: true,
      sensitivity: 'base',
    });
    results.sort((a, b) => {
      const aStartsWithDigit = /^\d/.test(a.path.split('/').pop()!);
      const bStartsWithDigit = /^\d/.test(b.path.split('/').pop()!);
      if (aStartsWithDigit && !bStartsWithDigit) return 1;
      if (!aStartsWithDigit && bStartsWithDigit) return -1;
      return collator.compare(a.path, b.path);
    });
    return results;
  }

  /**
   * 当改变一个文件的路径或文件名时，调用这个方法来更新opf中的路径，以及所有的<link>中href和所有<img>中的src
   * @param old_path 旧文件路径
   * @param new_path 新文件路径
   * @param new_media_type 新文件类型
   */
  updateLinks(old_path: string, new_path: string, new_media_type?: string) {
    // 处理opf的更新
    this.opf.querySelectorAll(`manifest > item`).forEach((item) => {
      const href = item.getAttribute('href');
      if (href !== null) {
        if (
          new URL(href, `files:/${this.opfPath}`).href === `files:/${old_path}`
        ) {
          // 用URL获取href的绝对路径
          item.setAttribute('href', getRelativePath(this.opfPath, new_path));
          if (new_media_type) {
            item.setAttribute('media-type', new_media_type);
          }
          console.log(item.getAttribute('href'));
        }
      }
    });
    // 处理其它资源的更新
    this.resources.forEach((e) => {
      if (e.type !== 'doc') return;
      e.doc
        .querySelectorAll(`manifest > link[href], img[src]`)
        .forEach((item) => {
          let href = item.getAttribute('href');
          if (href !== null) {
            if (
              new URL(href, `files:/${e.path}`).href === `files:/${old_path}`
            ) {
              item.setAttribute('href', getRelativePath(e.path, new_path));
            }
          }
          let src = item.getAttribute('src');
          if (src !== null) {
            if (
              new URL(src, `files:/${e.path}`).href === `files:/${old_path}`
            ) {
              item.setAttribute('src', getRelativePath(e.path, new_path));
            }
          }
        });
    });
  }

  /**
   *
   * @returns 深层复制
   */
  copy(): Epub {
    // 深复制基本属性
    const copiedOpf = this.opf.cloneNode(true) as Document;
    // 深复制 resources 数组
    const copiedResources: EpubResource[] = this.resources.map((res) => {
      if (res.type === 'doc') {
        return {
          path: res.path,
          type: res.type,
          doc: res.doc.cloneNode(true) as Document, // 深复制 Document
        };
      } else if (res.type === 'image') {
        const blob = res.blob.slice(0, res.blob.size);
        return {
          path: res.path,
          type: res.type,
          blob, // 创建一个新的 Blob 实例
          url: URL.createObjectURL(blob),
        };
      } else {
        return {
          path: res.path,
          type: res.type,
          blob: res.blob.slice(0, res.blob.size), // 创建一个新的 Blob 实例
        };
      }
    });
    // 返回一个新的 Epub 实例
    return new Epub(
      this.name,
      this.opfDir,
      this.opfPath,
      copiedOpf,
      copiedResources,
    );
  }
}
