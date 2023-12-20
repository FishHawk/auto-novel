import * as zip from '@zip.js/zip.js';

const forEachXHtmlFile = async (
  file: File,
  callback: (path: string, doc: HTMLHtmlElement) => void
) => {
  // TODO: 修改成解析opf的方式
  const reader = new zip.ZipReader(new zip.BlobReader(file));
  const entries = (await reader.getEntries()).sort((a, b) =>
    a.filename.localeCompare(b.filename)
  );

  const options: zip.EntryGetDataOptions = {
    checkSignature: true,
    useWebWorkers: true,
    useCompressionStream: true,
  };

  for (const entry of entries) {
    // full path inside zip, E.g. item/xhtml/p-titlepage.xhtml
    const filename = entry.filename;
    const isXHtml = ['.xhtml', '.html', '.htm'].some((ext) =>
      filename.endsWith(ext)
    );

    if (isXHtml) {
      const data = await entry.getData!(new zip.BlobWriter(), options);
      const html = await data.text();
      const doc = document.createElement('html');
      doc.innerHTML = html;
      callback(filename, doc);
    }
  }
  reader.close();
};

export const getFullTextFromEpubFile = async (file: File) => {
  const fullContent: string[] = [];
  await forEachXHtmlFile(file, (path, doc) => {
    const text = extractTextFromXHtml(doc);
    fullContent.push(text.join('\n'));
  });
  console.log(fullContent.join('\n'));
  return fullContent.join('\n');
};

const extractTextFromXHtml = (html: HTMLHtmlElement) => {
  //FIXME(kuriko): find a better way to extract text from html
  //  Maybe use html-to-text package instead
  const ret: Array<string> = [];
  html.querySelectorAll('h1,h2,h3,h4,h5,h6,p,title').forEach((el) => {
    let html = el.innerHTML;
    html.replaceAll(/<rt>>/gm, '(');
    html.replaceAll(/<\/rt>>/gm, ')');
    el.innerHTML = html;
    let text = el.textContent || null;

    if (text && text.length > 0) {
      ret.push(text);
    } else if (text == null) {
      console.debug('Invalid node: ', el);
    }
  });

  return ret;
};
