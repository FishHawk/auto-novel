import { BlobReader, TextWriter, ZipReader } from '@zip.js/zip.js';
import * as path from 'path';
import * as xml2js from 'xml2js';

class EpubTool {
  private epub_name: string;
  private epub_file: File;
  private epub_type: string = '';
  private temp_dir: string = '';
  private namelist: string[] = [];
  private mime_map: Record<string, string> = {};
  private id_to_h_m_p: Record<string, any> = {};
  private manifest_list: any[] = [];
  private id_to_href: Record<string, any> = {};
  private href_to_id: Record<string, any> = {};
  private text_list: any[] = [];
  private css_list: any[] = [];
  private image_list: any[] = [];
  private font_list: any[] = [];
  private audio_list: any[] = [];
  private video_list: any[] = [];
  private spine_list: any[] = [];
  private other_list: any[] = [];
  private errorOPF_log: string[] = [];
  private epubBlob: Blob | null = null;
  private opf: string = '';
  private opfpath: string = '';
  private etree_opf: any = {};
  private tocid: string = '';
  private tocpath: string = '';
  private metadata: Record<string, string> = {};

  constructor(epub_name: string, epub_file: File) {
    this.epub_name = epub_name;
    this.epub_file = epub_file;
    this._init_mime_map();
  }

  // Initialize the ZipReader to load the EPUB file from the provided File object
  public async loadEpub(): Promise<void> {
    this.epubBlob = this.epub_file; // Directly assign the File object to the Blob
    const zipReader = new ZipReader(new BlobReader(this.epubBlob));
    const entries = await zipReader.getEntries();
    this.namelist = entries.map((entry) => entry.filename); // Get all filenames in the zip
    await zipReader.close();
  }

  private _init_mime_map(): void {
    this.mime_map = {
      '.bm': 'image/bmp',
      '.bmp': 'image/bmp',
      '.css': 'text/css',
      '.epub': 'application/epub+zip',
      '.gif': 'image/gif',
      '.htm': 'application/xhtml+xml',
      '.html': 'application/xhtml+xml',
      '.jpeg': 'image/jpeg',
      '.jpg': 'image/jpeg',
      '.js': 'application/javascript',
      '.m4a': 'audio/mp4',
      '.m4v': 'video/mp4',
      '.mp3': 'audio/mpeg',
      '.mp4': 'video/mp4',
      '.ncx': 'application/x-dtbncx+xml',
      '.oga': 'audio/ogg',
      '.ogg': 'audio/ogg',
      '.ogv': 'video/ogg',
      '.opf': 'application/oebps-package+xml',
      '.otf': 'font/otf',
      '.pls': 'application/pls+xml',
      '.png': 'image/png',
      '.smil': 'application/smil+xml',
      '.svg': 'image/svg+xml',
      '.tif': 'image/tiff',
      '.tiff': 'image/tiff',
      '.ttc': 'font/collection',
      '.ttf': 'font/ttf',
      '.ttml': 'application/ttml+xml',
      '.txt': 'text/plain',
      '.vtt': 'text/vtt',
      '.webm': 'video/webm',
      '.webp': 'image/webp',
      '.woff': 'font/woff',
      '.woff2': 'font/woff2',
      '.xhtml': 'application/xhtml+xml',
      '.xml': 'application/oebps-page-map+xml',
      '.xpgt': 'application/vnd.adobe-page-template+xml',
    };
  }

  public async _init_opf(): Promise<void> {
    const containerEntry = this.namelist.find(
      (name) => name === 'META-INF/container.xml',
    );
    if (!containerEntry) {
      throw new Error('无法发现 container.xml');
    }

    const zipReader = new ZipReader(new BlobReader(this.epubBlob!));
    const entries = await zipReader.getEntries();
    const containerEntryObj = entries.find(
      (entry) => entry.filename === 'META-INF/container.xml',
    );

    if (containerEntryObj) {
      const containerXmlBlob = await containerEntryObj?.getData?.(
        new TextWriter(),
      );
      const containerXml = containerXmlBlob as string;

      const rf = containerXml.match(
        /<rootfile[^>]*full-path="(?i:(.*?\.opf))"/,
      );
      if (rf) {
        this.opfpath = rf[1];
        const opfEntryObj = entries.find(
          (entry) => entry.filename === this.opfpath,
        );

        if (opfEntryObj) {
          const opfBlob = await opfEntryObj?.getData?.(new TextWriter());
          this.opf = opfBlob as string;
          return;
        }
      }

      // Fallback: Search for OPF file in namelist
      for (const bkpath of this.namelist) {
        if (bkpath.toLowerCase().endsWith('.opf')) {
          this.opfpath = bkpath;
          const opfEntryObj = entries.find(
            (entry) => entry.filename === bkpath,
          );

          if (opfEntryObj) {
            const opfBlob = await opfEntryObj?.getData?.(new TextWriter());
            this.opf = opfBlob as string;
            return;
          }
        }
      }
    }
    throw new Error('无法发现 opf 文件');
  }

  public async _parse_opf(): Promise<void> {
    // Parse OPF XML using xml2js
    const parser = new xml2js.Parser();
    this.etree_opf['package'] = await parser.parseStringPromise(this.opf);

    for (const child of this.etree_opf['package']['package']) {
      const tag = child.tagName.replace(/{.*?}/, ''); // Remove namespace
      this.etree_opf[tag] = child;
    }

    await this._parse_metadata();
    await this._parse_manifest();
    await this._parse_spine();
    await this._clear_duplicate_id_href();
    await this._check_opf_href_avaliable_and_case();
    await this._add_files_not_in_opf();

    this.manifest_list = [];
    for (const id in this.id_to_h_m_p) {
      const [href, mime, properties] = this.id_to_h_m_p[id];
      this.manifest_list.push([id, href, mime, properties]);
    }

    const epub_type = this.etree_opf['package']['package'].version;

    if (epub_type && ['2.0', '3.0'].includes(epub_type)) {
      this.epub_type = epub_type;
    } else {
      throw new Error('此脚本不支持该EPUB类型');
    }

    const tocid = this.etree_opf['spine']?.$?.toc || '';
    this.tocid = tocid;
    const opf_dir = path.dirname(this.opfpath);

    for (const [id, href, mime, properties] of this.manifest_list) {
      if (mime === 'application/xhtml+xml') {
        this.text_list.push([id, href, properties]);
      } else if (mime === 'text/css') {
        this.css_list.push([id, href, properties]);
      } else if (mime.startsWith('image/')) {
        this.image_list.push([id, href, properties]);
      } else if (
        mime.startsWith('font/') ||
        href.toLowerCase().endsWith('.ttf') ||
        href.toLowerCase().endsWith('.otf') ||
        href.toLowerCase().endsWith('.woff')
      ) {
        this.font_list.push([id, href, properties]);
      } else if (mime.startsWith('audio/')) {
        this.audio_list.push([id, href, properties]);
      } else if (mime.startsWith('video/')) {
        this.video_list.push([id, href, properties]);
      } else if (this.tocid && id === this.tocid) {
        this.tocpath = opf_dir ? `${opf_dir}/${href}` : href;
      } else {
        this.other_list.push([id, href, mime, properties]);
      }
    }

    this._check_manifest_and_spine();
  }

  private _parse_metadata(): void {
    this.metadata = {
      title: '',
      creator: '',
      language: '',
      subject: '',
      source: '',
      identifier: '',
      cover: '',
    };

    for (const meta of this.etree_opf['metadata']) {
      const tag = meta.tag.replace(/{.*?}/, ''); // Remove namespace

      if (
        [
          'title',
          'creator',
          'language',
          'subject',
          'source',
          'identifier',
        ].includes(tag)
      ) {
        this.metadata[tag] = meta['#text'] || ''; // Use #text to access the content in xml2js
      } else if (tag === 'meta') {
        const name = meta.$?.name;
        const content = meta.$?.content;
        if (name && content) {
          this.metadata['cover'] = content;
        }
      }
    }
  }

  private _parse_manifest(): void {
    this.id_to_h_m_p = {};
    this.id_to_href = {};
    this.href_to_id = {};

    for (const item of this.etree_opf['manifest']) {
      const id = item.$.id;
      const href = decodeURIComponent(item.$.href); // Use decodeURIComponent instead of unquote in TS
      const mime = item.$['media-type'];
      const properties = item.$.properties || '';

      this.id_to_h_m_p[id] = [href, mime, properties];
      this.id_to_href[id] = href.toLowerCase();
      this.href_to_id[href.toLowerCase()] = id;
    }
  }

  private _parse_spine(): void {
    this.spine_list = [];

    for (const itemref of this.etree_opf['spine']) {
      const sid = itemref.$.idref;
      const linear = itemref.$.linear || '';
      const properties = itemref.$.properties || '';
      this.spine_list.push([sid, linear, properties]);
    }
  }

  private _clear_duplicate_id_href(): void {
    const id_used = this.spine_list.map((x) => x[0]);
    if (this.metadata['cover']) {
      id_used.push(this.metadata['cover']);
    }

    const del_id: string[] = [];

    for (const [id, href] of Object.entries(this.id_to_href)) {
      if (this.href_to_id[href] !== id) {
        if (id_used.includes(id) && !id_used.includes(this.href_to_id[href])) {
          if (!del_id.includes(this.href_to_id[href])) {
            del_id.push(this.href_to_id[href]);
          }
          this.href_to_id[href] = id;
        } else if (
          id_used.includes(id) &&
          id_used.includes(this.href_to_id[href])
        ) {
          continue;
        } else {
          if (!del_id.includes(id)) {
            del_id.push(id);
          }
        }
      }
    }

    for (const id of del_id) {
      this.errorOPF_log.push(`duplicate_id: ${id}`);
      delete this.id_to_href[id];
      delete this.id_to_h_m_p[id];
    }
  }

  private async _check_opf_href_avaliable_and_case() {
    // Placeholder for checking hrefs
  }

  private async _add_files_not_in_opf() {
    // Placeholder for adding non-OPF files
  }

  private async _check_manifest_and_spine() {
    // Placeholder for manifest and spine consistency check
  }
}
