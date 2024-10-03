import * as path from 'path-browserify';

export class EpubTool {
  public epub_name: string;
  public epub_file: File;
  public epub_type: string = '';
  public temp_dir: string = '';
  public namelist: string[] = [];
  public mime_map: Record<string, string> = {};
  public id_to_h_m_p: Record<string, any> = {};
  public manifest_list: any[] = [];
  public id_to_href: Record<string, any> = {};
  public href_to_id: Record<string, any> = {};
  public text_list: any[] = [];
  public css_list: any[] = [];
  public image_list: any[] = [];
  public font_list: any[] = [];
  public audio_list: any[] = [];
  public video_list: any[] = [];
  public spine_list: any[] = [];
  public other_list: any[] = [];
  public errorOPF_log: Array<[string, any]> = [];
  public errorLink_log: Record<string, any> = {};
  public epubBlob: Blob | null = null;
  public opf: string = '';
  public opfpath: string = '';
  public etree_opf: any = {};
  public tocid: string = '';
  public tocpath: string = '';
  public metadata: Record<string, string> = {};

  constructor(epub_name: string, epub_file: File) {
    this.epub_name = epub_name;
    this.epub_file = epub_file;
    this.initialize();
  }

  public async initialize(): Promise<void> {
    await this._init_mime_map();
    await this.loadEpub();
    await this._init_opf();
    await this._parse_opf();
  }

  // Initialize the ZipReader to load the EPUB file from the provided File object
  public async loadEpub(): Promise<void> {
    const { BlobReader, ZipReader } = await import('@zip.js/zip.js');
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
    const { BlobReader, TextWriter, ZipReader } = await import(
      '@zip.js/zip.js'
    );

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
    // Parse OPF XML using DOMParser
    const parser = new DOMParser();
    const opfDoc = parser.parseFromString(this.opf, 'application/xml');
    this.etree_opf['package'] = opfDoc.documentElement;

    for (const child of Array.from(this.etree_opf['package'].children)) {
      const element = child as Element;
      const tag = element.tagName.replace(/{.*?}/, ''); // Remove namespace
      this.etree_opf[tag] = element;
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

    const epub_type = this.etree_opf['package'].getAttribute('version');

    if (epub_type && ['2.0', '3.0'].includes(epub_type)) {
      this.epub_type = epub_type;
    } else {
      throw new Error('此脚本不支持该EPUB类型');
    }

    const tocid = this.etree_opf['spine']?.getAttribute('toc') || '';
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

    await this._check_manifest_and_spine();
  }

  private async _parse_metadata(): Promise<void> {
    this.metadata = {
      title: '',
      creator: '',
      language: '',
      subject: '',
      source: '',
      identifier: '',
      cover: '',
    };

    const metadata = this.etree_opf['metadata'];
    if (metadata && Array.isArray(metadata)) {
      for (const meta of metadata) {
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
  }

  private async _parse_manifest(): Promise<void> {
    this.id_to_h_m_p = {};
    this.id_to_href = {};
    this.href_to_id = {};

    const manifest = this.etree_opf['manifest'];
    if (manifest && Array.isArray(manifest)) {
      for (const item of manifest) {
        const id = item.$.id;
        const href = decodeURIComponent(item.$.href); // Use decodeURIComponent instead of unquote in TS
        const mime = item.$['media-type'];
        const properties = item.$.properties || '';

        this.id_to_h_m_p[id] = [href, mime, properties];
        this.id_to_href[id] = href.toLowerCase();
        this.href_to_id[href.toLowerCase()] = id;
      }
    }
  }

  private async _parse_spine(): Promise<void> {
    this.spine_list = [];

    const spine = this.etree_opf['spine'];
    if (spine && Array.isArray(spine)) {
      for (const itemref of spine) {
        const sid = itemref.$.idref;
        const linear = itemref.$.linear || '';
        const properties = itemref.$.properties || '';
        this.spine_list.push([sid, linear, properties]);
      }
    }
  }

  private async _clear_duplicate_id_href(): Promise<void> {
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
      this.errorOPF_log.push(['duplicate_id', id]);
      delete this.id_to_href[id];
      delete this.id_to_h_m_p[id];
    }
  }

  private async _check_opf_href_avaliable_and_case(): Promise<void> {
    const del_id: string[] = [];
    const del_href: string[] = [];
    const corrected_id_hrefs: [string, string][] = [];
    const error_log: [string, string | null][] = [];

    const lowerPath_to_archivePath = Object.fromEntries(
      this.namelist.map((x: string) => [x.toLowerCase(), x]),
    );

    for (const id in this.id_to_h_m_p) {
      const [href, mime, prop] = this.id_to_h_m_p[id];
      const bookPath = this.get_bookpath(href, this.opfpath);
      const archivePath =
        lowerPath_to_archivePath[(await bookPath).toLowerCase()];

      if (!archivePath) {
        del_id.push(id);
        del_href.push(href.toLowerCase());
        error_log.push([href, null]);
      } else if ((await bookPath) !== archivePath) {
        const corrected_href = this.get_relpath(this.opfpath, archivePath);
        corrected_id_hrefs.push([id, await corrected_href]);
        error_log.push([href, await corrected_href]);
      }
    }

    for (const [id, corrected_href] of corrected_id_hrefs) {
      const [href, mime, prop] = this.id_to_h_m_p[id];
      this.id_to_h_m_p[id] = [corrected_href, mime, prop];
    }

    for (const id of del_id) {
      delete this.id_to_href[id];
      delete this.id_to_h_m_p[id];
    }

    for (const href_l of del_href) {
      delete this.href_to_id[href_l];
    }

    if (error_log.length > 0) {
      this.errorLink_log[this.opfpath] = error_log;
    }
  }

  private async _add_files_not_in_opf(): Promise<void> {
    const hrefs_not_in_opf: string[] = [];
    const validExtensions = [
      '.html',
      '.xhtml',
      '.css',
      '.jpg',
      '.jpeg',
      '.bmp',
      '.gif',
      '.png',
      '.webp',
      '.svg',
      '.ttf',
      '.otf',
      '.js',
      '.mp3',
      '.mp4',
      '.smil',
    ];

    for (const archive_path of this.namelist) {
      // Check if archive_path ends with any of the valid extensions
      if (
        validExtensions.some((ext) => archive_path.toLowerCase().endsWith(ext))
      ) {
        const opf_href = this.get_relpath(this.opfpath, archive_path);
        if (!this.href_to_id.hasOwnProperty(opf_href.toLowerCase())) {
          hrefs_not_in_opf.push(opf_href);
        }
      }
    }

    const allocate_id = (href: string): string => {
      let basename = path.basename(href);
      let new_id = /^[a-zA-Z]/.test(basename[0]) ? basename : 'x' + basename;
      let pre = new_id.substring(0, new_id.lastIndexOf('.'));
      let suf = new_id.substring(new_id.lastIndexOf('.'));
      let pre_ = pre;
      let i = 0;

      while (this.id_to_href.hasOwnProperty(pre_ + suf)) {
        i++;
        pre_ = pre + '_' + i;
      }

      return pre_ + suf;
    };

    for (const href of hrefs_not_in_opf) {
      const new_id = allocate_id(href);
      this.id_to_href[new_id] = href.toLowerCase();
      this.href_to_id[href.toLowerCase()] = new_id;

      const ext = path.extname(href).toLowerCase();
      const mime = this.mime_map[ext] || 'text/plain';
      this.id_to_h_m_p[new_id] = [href, mime, ''];
    }
  }

  private async _check_manifest_and_spine(): Promise<void> {
    const spine_idrefs = this.spine_list.map(([idref, _, __]) => idref);

    for (const idref of spine_idrefs) {
      if (!this.id_to_h_m_p[idref]) {
        this.errorOPF_log.push(['invalid_idref', idref]);
      }
    }
    for (const [mid, opf_href, mime, properties] of this.manifest_list) {
      if (mime === 'application/xhtml+xml' && !spine_idrefs.includes(mid)) {
        this.errorOPF_log.push(['xhtml_not_in_spine', mid]);
      }
    }
  }

  private get_relpath(from_path: string, to_path: string): string {
    const fromParts = from_path.split(/[\\/]/);
    const toParts = to_path.split(/[\\/]/);

    while (
      fromParts.length > 0 &&
      toParts.length > 0 &&
      fromParts[0] === toParts[0]
    ) {
      fromParts.shift();
      toParts.shift();
    }

    // Calculate relative path by adding "../" for each remaining part in from_path
    const relPath = '../'.repeat(fromParts.length - 1) + toParts.join('/');
    return relPath;
  }
  private async get_bookpath(
    relative_path: string,
    refer_bkpath: string,
  ): Promise<string> {
    const relativeParts = relative_path.split(/[\\/]/);
    const referParts = refer_bkpath.split(/[\\/]/);

    let back_step = 0;
    while (relativeParts[0] === '..') {
      back_step++;
      relativeParts.shift(); // Remove the ".." from relative_path
    }
    if (referParts.length > 1) {
      referParts.pop();
    }
    if (back_step < 1) {
      return referParts.concat(relativeParts).join('/');
    }
    if (back_step > referParts.length) {
      return relativeParts.join('/');
    }
    while (back_step > 0 && referParts.length > 0) {
      referParts.pop();
      back_step--;
    }
    return referParts.concat(relativeParts).join('/');
  }

  public async restructure(file: File): Promise<Blob> {
    const { BlobReader, BlobWriter, TextWriter, ZipReader, ZipWriter } =
      await import('@zip.js/zip.js');

    const reader = new ZipReader(new BlobReader(file));
    const entriesMap = new Map<string, any>(
      (await reader.getEntries()).map((entry) => [entry.filename, entry]),
    );

    const zipBlobWriter = new BlobWriter();
    const writer = new ZipWriter(zipBlobWriter);

    // Helper function to read files as text
    const readFileAsText = async (filename: string): Promise<string> => {
      const entry = entriesMap.get(filename);
      if (!entry) throw new Error(`File ${filename} not found in EPUB`);
      const text = await entry.getData!(new TextWriter());
      return text;
    };

    // Helper function to get the relative book path
    const get_bookpath = (
      relative_path: string,
      refer_bkpath: string,
    ): string => {
      const relativeParts = relative_path.split(/[\\/]/);
      const referParts = refer_bkpath.split(/[\\/]/);
      let back_step = 0;

      while (relativeParts[0] === '..') {
        back_step++;
        relativeParts.shift();
      }

      if (referParts.length > 1) {
        referParts.pop();
      }

      if (back_step > referParts.length) {
        return relativeParts.join('/');
      }

      while (back_step > 0) {
        referParts.pop();
        back_step--;
      }

      return referParts.concat(relativeParts).join('/');
    };

    // Read and update mimetype and META-INF/container.xml
    const mimetypeBlob = await entriesMap.get('mimetype').getData!(
      new BlobWriter(),
    );
    await writer.add('mimetype', new BlobReader(mimetypeBlob)); // Add mimetype

    let metainfData = await readFileAsText('META-INF/container.xml');
    metainfData = metainfData.replace(
      /<rootfile[^>]*media-type="application\/oebps-[^>]*\/>/g,
      '<rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>',
    );

    await writer.add(
      'META-INF/container.xml',
      new BlobReader(new Blob([metainfData], { type: 'text/xml' })),
    ); // Add updated container.xml

    // Define mappings for re-pathing resources (text, css, images, etc.)
    const re_path_map: Record<string, Record<string, string>> = {
      text: {},
      css: {},
      image: {},
      font: {},
      audio: {},
      video: {},
      other: {},
    };
    const basename_log: Record<string, string[]> = {
      text: [],
      css: [],
      image: [],
      font: [],
      audio: [],
      video: [],
      other: [],
    };
    const lowerPath_to_originPath: Record<string, string> = {};

    // Helper function to auto-rename files
    const auto_rename = (
      id: string,
      href: string,
      ftype: keyof typeof re_path_map,
    ): string => {
      const [filename, ext] = [path.parse(href).name, path.extname(href)];
      let filename_ = filename;
      let num = 0;
      while (basename_log[ftype].includes(filename_ + ext)) {
        num++;
        filename_ = `${filename}_${num}`;
      }
      const basename = filename_ + ext;
      basename_log[ftype].push(basename);
      return basename;
    };

    const check_link = (
      filename: string,
      bkpath: string,
      href: string,
      target_id = '',
    ): string | null => {
      if (
        !href ||
        href.startsWith('http://') ||
        href.startsWith('https://') ||
        href.startsWith('res:/') ||
        href.startsWith('file:/') ||
        href.startsWith('data:')
      ) {
        return null;
      }
      const lowerBkpath = bkpath.toLowerCase();

      if (lowerBkpath in lowerPath_to_originPath) {
        if (bkpath !== lowerPath_to_originPath[lowerBkpath]) {
          this.errorLink_log[filename] = this.errorLink_log[filename] || [];
          this.errorLink_log[filename].push([
            href + target_id,
            lowerPath_to_originPath[lowerBkpath],
          ]);
          bkpath = lowerPath_to_originPath[lowerBkpath];
        }
      } else {
        this.errorLink_log[filename] = this.errorLink_log[filename] || [];
        this.errorLink_log[filename].push([href + target_id, null]);
        return null;
      }
      return bkpath;
    };

    // Iterate over text, CSS, images, etc., and re-path resources
    const processResources = async (
      list: any[],
      ftype: keyof typeof re_path_map,
      folder: string,
    ) => {
      for (const [id, href] of list) {
        const bkpath = get_bookpath(href, this.opfpath);
        const basename = auto_rename(id, href, ftype);
        re_path_map[ftype][bkpath] = basename;
        lowerPath_to_originPath[bkpath.toLowerCase()] = bkpath;

        const dataBlob = await entriesMap.get(bkpath).getData!(
          new BlobWriter(),
        );
        await writer.add(
          `OEBPS/${folder}/${basename}`,
          new BlobReader(dataBlob),
        );
      }
    };

    // await processResources(this.text_list, 'text', 'Text');
    // await processResources(this.css_list, 'css', 'Styles');
    // await processResources(this.image_list, 'image', 'Images');
    // await processResources(this.font_list, 'font', 'Fonts');
    // await processResources(this.audio_list, 'audio', 'Audio');
    // await processResources(this.video_list, 'video', 'Video');

    // Handle TOC updates if available
    if (this.tocpath) {
      let toc = await readFileAsText(this.tocpath);
      toc = toc.replace(/src=([\'\"])(.*?)\1/g, (match, p1, href) => {
        href = decodeURIComponent(href.trim());
        const [cleanHref, targetId] = href.includes('#')
          ? href.split('#')
          : [href, ''];
        const bkpath = get_bookpath(cleanHref, this.tocpath);
        const newBkpath =
          check_link(this.tocpath, bkpath, href, targetId) || '';
        const filename = path.basename(newBkpath);
        return `src="Text/${filename}"${targetId}`;
      });
      await writer.add(
        'OEBPS/toc.ncx',
        new BlobReader(new Blob([toc], { type: 'text/xml' })),
      );
    }

    console.log(re_path_map['text']);
    for (const [xhtml_bkpath, new_name] of Object.entries(
      re_path_map['text'],
    )) {
      let text = await entriesMap.get(xhtml_bkpath).getData!(new TextWriter());

      if (!text.startsWith('<?xml')) {
        text = '<?xml version="1.0" encoding="utf-8"?>\n' + text;
      }
      if (!/<!DOCTYPE html/.test(text)) {
        text = text.replace(
          /(<\?xml.*?>)\n*/,
          '$1\n<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN"\n  "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd">\n',
        );
      }
      text = text.replace(
        /(<[^>]*href=([\'\"]))(.*?)(\2[^>]*>)/g,
        (match: any, p1: string, p2: string, href: string) => {
          href = decodeURIComponent(href).trim();
          let target_id = '';
          if (href.includes('#')) {
            [href, target_id] = href.split('#');
            target_id = '#' + target_id;
          }
          const bkpath = get_bookpath(href, xhtml_bkpath);
          const newBkpath =
            check_link(xhtml_bkpath, bkpath, href, target_id) || bkpath;

          if (
            href.toLowerCase().endsWith('.jpg') ||
            href.toLowerCase().endsWith('.jpeg') ||
            href.toLowerCase().endsWith('.png') ||
            href.toLowerCase().endsWith('.bmp') ||
            href.toLowerCase().endsWith('.gif') ||
            href.toLowerCase().endsWith('.webp')
          ) {
            const filename = re_path_map['image'][newBkpath];
            return p1 + '../Images/' + filename + p2;
          } else if (href.toLowerCase().endsWith('.css')) {
            const filename = re_path_map['css'][newBkpath];
            return `<link href="../Styles/${filename}" type="text/css" rel="stylesheet"/>`;
          } else if (
            href.toLowerCase().endsWith('.xhtml') ||
            href.toLowerCase().endsWith('.html')
          ) {
            const filename = re_path_map['text'][newBkpath];
            return p1 + filename + target_id + p2;
          } else {
            return match;
          }
        },
      );
      text = text.replace(
        /(<[^>]* src=([\'\"]))(.*?)(\2[^>]*>)/g,
        (match: any, p1: string, p2: string, href: string) => {
          href = decodeURIComponent(href).trim();
          const bkpath = get_bookpath(href, xhtml_bkpath);
          const newBkpath = check_link(xhtml_bkpath, bkpath, href) || bkpath;

          if (
            href.toLowerCase().endsWith('.jpg') ||
            href.toLowerCase().endsWith('.jpeg') ||
            href.toLowerCase().endsWith('.png') ||
            href.toLowerCase().endsWith('.bmp') ||
            href.toLowerCase().endsWith('.gif') ||
            href.toLowerCase().endsWith('.webp') ||
            href.toLowerCase().endsWith('.svg')
          ) {
            const filename = re_path_map['image'][newBkpath];
            return p1 + '../Images/' + filename + p2;
          } else if (href.toLowerCase().endsWith('.mp3')) {
            const filename = re_path_map['audio'][newBkpath];
            return p1 + '../Audio/' + filename + p2;
          } else if (href.toLowerCase().endsWith('.mp4')) {
            const filename = re_path_map['video'][newBkpath];
            return p1 + '../Video/' + filename + p2;
          } else if (href.toLowerCase().endsWith('.js')) {
            const filename = re_path_map['other'][newBkpath];
            return p1 + '../Misc/' + filename + p2;
          } else {
            return match;
          }
        },
      );
      text = text.replace(
        /(url\([\'\"]?)(.*?)([\'\"]?\))/g,
        (match: string, p1: string, url: string) => {
          url = decodeURIComponent(url).trim();
          const bkpath = get_bookpath(url, xhtml_bkpath);
          const newBkpath = check_link(xhtml_bkpath, bkpath, url) || bkpath;

          if (
            url.toLowerCase().endsWith('.ttf') ||
            url.toLowerCase().endsWith('.otf')
          ) {
            const filename = re_path_map['font'][newBkpath];
            return p1 + '../Fonts/' + filename + match.slice(-1);
          } else if (
            url.toLowerCase().endsWith('.jpg') ||
            url.toLowerCase().endsWith('.jpeg') ||
            url.toLowerCase().endsWith('.png') ||
            url.toLowerCase().endsWith('.bmp') ||
            url.toLowerCase().endsWith('.gif') ||
            url.toLowerCase().endsWith('.webp') ||
            url.toLowerCase().endsWith('.svg')
          ) {
            const filename = re_path_map['image'][newBkpath];
            return p1 + '../Images/' + filename + match.slice(-1);
          } else {
            return match;
          }
        },
      );

      // Start of Selection
      console.log(`Adding file OEBPS/Text/${new_name}`);
      await writer.add(
        'OEBPS/Text/' + new_name,
        new BlobReader(new Blob([text], { type: 'text/xml' })),
      );
    }

    for (const [css_bkpath, new_name] of Object.entries(re_path_map['css'])) {
      try {
        let css = await entriesMap.get(css_bkpath).getData!(new TextWriter());
        css = css.replace(
          /@import ([\'\"])(.*?)\1|@import url\([\'\"]?(.*?)[\'\"]?\)/g,
          (match: any, p1: any, href: string) => {
            href = decodeURIComponent(href || '').trim();
            if (!href.toLowerCase().endsWith('.css')) return match;
            const filename = path.basename(href);
            return `@import "${filename}"`;
          },
        );
        css = css.replace(
          /(url\([\'\"]?)(.*?)([\'\"]?\))/g,
          (match: string, p1: string, url: string) => {
            url = decodeURIComponent(url).trim();
            const bkpath = get_bookpath(url, css_bkpath);
            const newBkpath = check_link(css_bkpath, bkpath, url) || bkpath;

            if (
              url.toLowerCase().endsWith('.ttf') ||
              url.toLowerCase().endsWith('.otf')
            ) {
              const filename = re_path_map['font'][newBkpath];
              return p1 + '../Fonts/' + filename + match.slice(-1);
            } else if (
              url.toLowerCase().endsWith('.jpg') ||
              url.toLowerCase().endsWith('.jpeg') ||
              url.toLowerCase().endsWith('.png') ||
              url.toLowerCase().endsWith('.bmp') ||
              url.toLowerCase().endsWith('.gif') ||
              url.toLowerCase().endsWith('.webp') ||
              url.toLowerCase().endsWith('.svg')
            ) {
              const filename = re_path_map['image'][newBkpath];
              return p1 + '../Images/' + filename + match.slice(-1);
            } else {
              return match;
            }
          },
        );
        // Start of Selection
        console.log(`Replacing file OEBPS/Styles/${new_name}`);

        await writer.add(
          'OEBPS/Styles/' + new_name,
          new BlobReader(new Blob([css], { type: 'text/xml' })),
        );
      } catch (err) {
        console.log(err);
        continue;
      }
    }
    for (const [img_bkpath, new_name] of Object.entries(re_path_map['image'])) {
      try {
        const imgBlob = await entriesMap.get(img_bkpath).getData!(
          new BlobWriter(),
        );
        await writer.add('OEBPS/Images/' + new_name, new BlobReader(imgBlob));
      } catch {
        continue;
      }
    }
    for (const [font_bkpath, new_name] of Object.entries(re_path_map['font'])) {
      try {
        const fontBlob = await entriesMap.get(font_bkpath).getData!(
          new BlobWriter(),
        );
        await writer.add('OEBPS/Fonts/' + new_name, new BlobReader(fontBlob));
      } catch {
        continue;
      }
    }
    for (const [audio_bkpath, new_name] of Object.entries(
      re_path_map['audio'],
    )) {
      try {
        const audioBlob = await entriesMap.get(audio_bkpath).getData!(
          new BlobWriter(),
        );
        await writer.add('OEBPS/Audio/' + new_name, new BlobReader(audioBlob));
      } catch {
        continue;
      }
    }
    for (const [video_bkpath, new_name] of Object.entries(
      re_path_map['video'],
    )) {
      try {
        const videoBlob = await entriesMap.get(video_bkpath).getData!(
          new BlobWriter(),
        );
        await writer.add('OEBPS/Video/' + new_name, new BlobReader(videoBlob));
      } catch {
        continue;
      }
    }
    for (const [misc_bkpath, new_name] of Object.entries(
      re_path_map['other'],
    )) {
      try {
        const miscBlob = await entriesMap.get(misc_bkpath).getData!(
          new BlobWriter(),
        );
        await writer.add('OEBPS/Misc/' + new_name, new BlobReader(miscBlob));
      } catch {
        continue;
      }
    }

    let manifest_text = '<manifest>';

    for (const [id, href, mime, prop] of this.manifest_list) {
      const bkpath = get_bookpath(href, this.opfpath);
      const prop_attr = prop ? ` properties="${prop}"` : '';
      let folder = '';

      if (mime === 'application/xhtml+xml') {
        const filename = re_path_map['text'][bkpath];
        manifest_text += `    <item id="${id}" href="Text/${filename}" media-type="${mime}"${prop_attr}/>\n`;
      } else if (mime === 'text/css') {
        const filename = re_path_map['css'][bkpath];
        manifest_text += `    <item id="${id}" href="Styles/${filename}" media-type="${mime}"${prop_attr}/>\n`;
      } else if (mime.startsWith('image/')) {
        const filename = re_path_map['image'][bkpath];
        manifest_text += `    <item id="${id}" href="Images/${filename}" media-type="${mime}"${prop_attr}/>\n`;
      } else if (
        mime.startsWith('font/') ||
        href.toLowerCase().endsWith('.ttf') ||
        href.toLowerCase().endsWith('.otf') ||
        href.toLowerCase().endsWith('.woff')
      ) {
        const filename = re_path_map['font'][bkpath];
        manifest_text += `    <item id="${id}" href="Fonts/${filename}" media-type="${mime}"${prop_attr}/>\n`;
      } else if (mime.startsWith('audio/')) {
        const filename = re_path_map['audio'][bkpath];
        manifest_text += `    <item id="${id}" href="Audio/${filename}" media-type="${mime}"${prop_attr}/>\n`;
      } else if (mime.startsWith('video/')) {
        const filename = re_path_map['video'][bkpath];
        manifest_text += `    <item id="${id}" href="Video/${filename}" media-type="${mime}"${prop_attr}/>\n`;
      } else if (id === this.tocid) {
        manifest_text += `    <item id="${id}" href="toc.ncx" media-type="application/x-dtbncx+xml"/>\n`;
      } else {
        const filename = re_path_map['other'][bkpath];
        manifest_text += `    <item id="${id}" href="Misc/${filename}" media-type="${mime}"${prop_attr}/>\n`;
      }
    }

    manifest_text += '  </manifest>';

    // Replace the old manifest in the OPF
    let opf = await readFileAsText(this.opfpath); // Assuming readFileAsText is already defined
    opf = opf.replace(/<manifest.*?>.*?<\/manifest>/s, manifest_text);

    // Modify reference paths in the OPF
    opf = opf.replace(
      /(<reference[^>]*href=([\'\"]))(.*?)(\2[^>]*\/>)/g,
      (match: string, p1: string, p2: string, href: string) => {
        href = decodeURIComponent(href).trim();
        const basename = path.basename(href); // Use split and pop to get the basename
        if (!basename.endsWith('.ncx')) {
          return `${p1}../Text/${basename}${p2}`;
        }
        return match;
      },
    );

    await writer.add(
      'OEBPS/content.opf',
      new BlobReader(new Blob([opf], { type: 'text/xml' })),
    );

    await writer.close();
    await reader.close();

    // Return the final EPUB Blob
    const zipBlob = await zipBlobWriter.getData();
    return zipBlob;
  }
}
