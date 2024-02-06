import { DBSchema, deleteDB, openDB } from 'idb';
import { v4 as uuidv4 } from 'uuid';

import { Epub } from '@/data/epub/epub';

import { Glossary } from '../type';
import { TranslatorId } from '../translator';

export interface ChapterTranslation {
  glossaryId: string;
  glossary: Glossary;
  paragraphs: string[];
}

interface VolumesDBSchema extends DBSchema {
  metadata: {
    key: string;
    value: {
      id: string;
      createAt: number;
      toc: {
        chapterId: string;
        baidu?: string;
        youdao?: string;
        gpt?: string;
        sakura?: string;
      }[];
      glossaryId: string;
      glossary: Glossary;
    };
  };
  file: {
    key: string;
    value: {
      id: string;
      file: File;
    };
  };
  chapter: {
    key: string;
    value: {
      id: string;
      volumeId: string;
      paragraphs: string[];
      baidu?: ChapterTranslation;
      youdao?: ChapterTranslation;
      gpt?: ChapterTranslation;
      sakura?: ChapterTranslation;
    };
    indexes: { byVolumeId: string };
  };
}

const openVolumesDB = () =>
  openDB<VolumesDBSchema>('volumes', 1, {
    upgrade(db, oldVersion, _newVersion, _transaction, _event) {
      if (oldVersion <= 0) {
        db.createObjectStore('metadata', { keyPath: 'id' });
        db.createObjectStore('file', { keyPath: 'id' });
        const store = db.createObjectStore('chapter', { keyPath: 'id' });
        store.createIndex('byVolumeId', 'volumeId');
      }
    },
  });

const deleteVolumesDb = () => deleteDB('volumes');

const withDb = <T extends Array<any>, U>(
  fn: (db: Awaited<ReturnType<typeof openVolumesDB>>, ...args: T) => Promise<U>
) => {
  return async (...args: T): Promise<U> => {
    const db = await openVolumesDB();
    return fn(db, ...args).finally(() => db.close());
  };
};

const listVolumes = withDb((db) => db.getAll('metadata'));

const saveVolume = withDb(async (db, file: File) => {
  const id = file.name;
  const chapters: { chapterId: string; paragraphs: string[] }[] = [];

  if ((await db.get('metadata', id)) !== undefined) {
    throw Error('小说已经存在');
  }

  if (id.endsWith('.txt')) {
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

    const jpLines = content.split('\n');
    const chunkSize = 1000;
    for (let i = 0; i < jpLines.length; i += chunkSize) {
      const paragraphs = jpLines.slice(i, i + chunkSize);
      chapters.push({ chapterId: i.toString(), paragraphs });
    }
  } else {
    await Epub.forEachXHtmlFile(file, (path, doc) => {
      const paragraphs = epubParserV1.extractText(doc);
      chapters.push({ chapterId: path, paragraphs });
    });
  }
  const tx = db.transaction('chapter', 'readwrite');
  chapters.forEach(({ chapterId, paragraphs }) => {
    tx.store.put({
      id: `${id}/${chapterId}`,
      volumeId: id,
      paragraphs,
    });
  });
  await tx.done;

  await db.put('metadata', {
    id,
    createAt: Date.now(),
    toc: chapters.map((it) => ({
      chapterId: it.chapterId,
    })),
    glossaryId: uuidv4(),
    glossary: {},
  });
  await db.put('file', { id, file });
});

const deleteVolume = withDb(async (db, id: string) => {
  const tx = db.transaction('chapter', 'readwrite');
  for await (const cursor of tx.store.index('byVolumeId').iterate(id)) {
    tx.store.delete(cursor.primaryKey);
  }
  await tx.done;

  await db.delete('metadata', id);
  await db.delete('file', id);
});

const updateGlossary = withDb(async (db, id: string, glossary: Glossary) => {
  const tx = db.transaction('metadata', 'readwrite');
  const metadata = await tx.store.get(id);
  if (metadata !== undefined) {
    metadata.glossary = glossary;
    metadata.glossaryId = uuidv4();
    await tx.store.put(metadata);
  }
  await tx.done;
});

const getTranslateTask = withDb(
  async (db, id: string, translatorId: TranslatorId) => {
    const tx = db.transaction('metadata', 'readwrite');
    const metadata = await tx.store.get(id);
    if (metadata === undefined) {
      throw new Error('小说不存在');
    }
    return {
      glossaryUuid: metadata.glossaryId,
      glossary: metadata.glossary,
      untranslatedChapters: metadata.toc
        .filter((it) => it[translatorId] === undefined)
        .map((it) => it.chapterId),
      expiredChapters: metadata.toc
        .filter(
          (it) =>
            it[translatorId] !== undefined &&
            it[translatorId] !== metadata.glossaryId
        )
        .map((it) => it.chapterId),
    };
  }
);

const getChapterToTranslate = withDb(
  async (db, id: string, chapterId: string) => {
    const chapter = await db.get('chapter', `${id}/${chapterId}`);
    if (chapter === undefined) {
      throw new Error('章节不存在');
    }
    return chapter?.paragraphs;
  }
);

const updateChapterTranslation = withDb(
  async (
    db,
    id: string,
    chapterId: string,
    translatorId: TranslatorId,
    translation: ChapterTranslation
  ) => {
    const metadata = await db.get('metadata', id);
    if (metadata === undefined) {
      throw new Error('章节不存在');
    }
    const chapter = await db.get('chapter', `${id}/${chapterId}`);
    if (chapter === undefined) {
      throw new Error('章节不存在');
    }
    metadata.toc
      .filter((it) => it.chapterId === chapterId)
      .forEach((it) => (it[translatorId] = translation.glossaryId));
    chapter[translatorId] = translation;
    await db.put('chapter', chapter);
    await db.put('metadata', metadata);

    return metadata.toc.filter((it) => it[translatorId] !== undefined).length;
  }
);

const makeTranslationVolumeFile = async ({
  volumeId,
  lang,
  translationsMode,
  translations,
}: {
  volumeId: string;
  lang: 'zh' | 'zh-jp' | 'jp-zh';
  translationsMode: 'parallel' | 'priority';
  translations: ('sakura' | 'baidu' | 'youdao' | 'gpt')[];
}) => {
  const filename = [
    lang,
    (translationsMode === 'parallel' ? 'B' : 'Y') +
      translations.map((it) => it[0]).join(''),
    volumeId,
  ].join('.');

  const db = await openVolumesDB();
  const metadata = await db.get('metadata', volumeId);
  if (metadata === undefined) throw Error('小说不存在');

  const getZhLinesList = async (chapterId: string) => {
    const chapter = await db.get('chapter', `${volumeId}/${chapterId}`);
    if (chapter === undefined) throw Error('章节不存在');

    const jpLines = chapter.paragraphs;
    const zhLinesList: Array<Array<string>> = [];

    for (const id of translations) {
      const zhLine = chapter[id]?.paragraphs;
      if (zhLine !== undefined) zhLinesList.push(zhLine);
    }

    if (translationsMode === 'priority' && zhLinesList.length > 1) {
      zhLinesList.length = 1;
    }

    return { jpLines, zhLinesList };
  };

  if (volumeId.endsWith('.txt')) {
    const buffer = [];
    for (const { chapterId } of metadata.toc) {
      const { jpLines, zhLinesList } = await getZhLinesList(chapterId);

      if (zhLinesList.length === 0) {
        buffer.push('// 该分段翻译缺失。');
      } else {
        const combinedLinesList = zhLinesList;
        if (lang === 'jp-zh') {
          combinedLinesList.unshift(jpLines);
        } else if (lang === 'zh-jp') {
          combinedLinesList.push(jpLines);
        }
        for (let i = 0; i < combinedLinesList[0].length; i++) {
          combinedLinesList.forEach((lines) => buffer.push(lines[i]));
        }
      }
    }
    return {
      filename,
      blob: new Blob([buffer.join('\n')], {
        type: 'text/plain',
      }),
    };
  } else {
    const file = await db.get('file', volumeId);
    if (file === undefined) throw Error('原始文件不存在');

    const parseXHtmlBlob = async (blob: Blob) => {
      const text = await blob.text();
      const parser = new DOMParser();
      const doc = parser.parseFromString(text, 'application/xhtml+xml');
      return doc;
    };
    const generated = await Epub.modify(file.file, async (path, blobIn) => {
      if (metadata.toc.some((it) => it.chapterId === path)) {
        const doc = await parseXHtmlBlob(blobIn);
        const { zhLinesList } = await getZhLinesList(path);
        if (zhLinesList.length === 0) return blobIn;
        await epubParserV1.injectTranslation(doc, lang, zhLinesList);
        return new Blob([doc.documentElement.outerHTML], {
          type: 'text/plain',
        });
      } else if (path.endsWith('opf')) {
        const doc = await parseXHtmlBlob(blobIn);
        // 防止部分阅读器使用竖排
        doc
          .getElementsByTagName('spine')
          .item(0)
          ?.removeAttribute('page-progression-direction');
        return new Blob([doc.documentElement.outerHTML], {
          type: 'text/plain',
        });
      } else if (path.endsWith('css')) {
        return new Blob([''], { type: 'text/plain' });
      } else {
        return blobIn;
      }
    });
    return { filename, blob: generated };
  }
};

interface EpubParser {
  extractText: (doc: Document) => string[];
  injectTranslation: (
    doc: Document,
    lang: 'zh' | 'jp-zh' | 'zh-jp',
    zhLinesList: string[][]
  ) => Document;
}

const epubParserV1: EpubParser = {
  extractText: (doc: Document) => {
    Array.from(doc.getElementsByTagName('rt')).forEach((node) =>
      node.parentNode!!.removeChild(node)
    );
    return Array.from(doc.body.getElementsByTagName('p'))
      .map((el) => el.innerText)
      .filter((it) => it.trim().length !== 0);
  },
  injectTranslation: (
    doc: Document,
    lang: 'zh' | 'jp-zh' | 'zh-jp',
    zhLinesList: string[][]
  ) => {
    Array.from(doc.body.getElementsByTagName('p'))
      .filter((el) => el.innerText.trim().length !== 0)
      .forEach((el, index) => {
        if (lang === 'zh') {
          zhLinesList.forEach((lines) => {
            const p = document.createElement('p');
            const t = document.createTextNode(lines[index]);
            p.appendChild(t);
            el.parentNode!!.insertBefore(p, el);
          });
          el.parentNode!!.removeChild(el);
        } else if (lang === 'jp-zh') {
          zhLinesList.forEach((lines) => {
            const p = document.createElement('p');
            const t = document.createTextNode(lines[index]);
            p.appendChild(t);
            el.parentNode!!.insertBefore(p, el.nextSibling);
          });
          el.setAttribute('style', 'opacity:0.4;');
        } else {
          zhLinesList.forEach((lines) => {
            const p = document.createElement('p');
            const t = document.createTextNode(lines[index]);
            p.appendChild(t);
            el.parentNode!!.insertBefore(p, el);
          });
          el.setAttribute('style', 'opacity:0.4;');
        }
      });

    return doc;
  },
};

export const PersonalVolumesManager = {
  listVolumes,
  saveVolume,
  deleteVolume,
  updateGlossary,
  getTranslateTask,
  getChapterToTranslate,
  updateChapterTranslation,
  deleteVolumesDb,
  //
  makeTranslationVolumeFile,
};
