import * as utils from './utils';
import * as zip from "@zip.js/zip.js";

export class EpubChapter {
    constructor(
        // The origin file path inside epub
        public orig_file: string,

        // The origin html content
        public html: string,

        // The parsed paragraphs
        public lines: Array<string>,
    ) {}
}


export class Epub<Type> {
    readonly file: File;

    epub: zip.ZipReader<Type>
    entries_cache: zip.Entry[] | null = null;

    public zipReaderOptions: zip.ZipReaderOptions = {
        checkSignature: true,
    }

    public workerConfiguration: zip.WorkerConfiguration = {
        useWebWorkers: true,
        useCompressionStream: true,
    }

    public zipWriterConstructorOptions: zip.ZipWriterConstructorOptions = {
        // Compression level 0-9
        level: 5,
    }

    public other_files: Array<Blob> = [];
    public content: Array<EpubChapter> = []

    constructor(file: File) {
        this.file = file;
        this.epub = new zip.ZipReader(new zip.BlobReader(file));
    }

    async init() {
        if (this.entries_cache == null) {
            let entries = await this.epub.getEntries();

            this.entries_cache = Array.from(entries)
                .sort((a, b) => a.filename.localeCompare(b.filename));

            let options: zip.EntryGetDataOptions = {
                ...this.zipReaderOptions,
                ...this.workerConfiguration
            };

            // NOTE(kuriko): maybe rewrite this to Promise.all can speed up the processing?
            let results = await Promise.all(this.entries_cache.map(async (entry) => {
                // full path inside zip, E.g. item/xhtml/p-titlepage.xhtml
                const filename = entry.filename;
                if (!utils.is_text_page(filename)) {
                    //NOTE(kuriko): currently we don't need this class to build epub.
                    return null;
                } else {
                    let data = await entry.getData!(new zip.BlobWriter(), options)
                    let html = await data.text();
                    let txts = utils.extract_text_from_xhtml(html);

                    return new EpubChapter(filename, html, txts)
                }
            }));

            // ref: https://stackoverflow.com/questions/43118692/typescript-filter-out-nulls-from-an-array
            this.content = results.filter((e): e is EpubChapter => e !== null);
        }
    }

    async makeEpub(filename: string = this.file.name): Promise<File> {
        await this.init();

        try {
            let writer = new zip.ZipWriter(new zip.BlobWriter("application/epub+zip"))
            // Add all files to zip
            for (let entry of this.entries_cache!) {
                // TODO(kuriko): add things here
            }

            // Finish the zip
            const blob = await writer.close();

            // TODO(kuriko): modify the epub name?
            const file: File = new File([blob], filename);
            return file;
        } catch (error) {
            console.error("Error when making epub zip: ", error);
            throw error;
        }
    }

    async getFullContent(): Promise<string> {
        await this.init();

        // let ending = newline ? "\n" : "";
        let ending = "\n";

        let ret: Array<String> = [];
        for (let chapter of this.content) {
            ret.push(chapter.lines.join(ending));
        }

        return ret.join(ending);
    }

    async getKatakana(): Promise<Map<string, number>> {
        await this.init();

        let mm = new Map();

        await Promise.all(
            this.content.map(async (chapter) => {
                const filename = chapter.orig_file;
                console.debug(`Processing ${filename}`);

                let katakanas = utils.extract_katakana(chapter.html);

                for (let kata of katakanas) {
                    let val = mm.get(kata) || 0;
                    val += 1;
                    mm.set(kata, val);
                }
            })
        )

        let mmSorted = new Map(
            [...mm.entries()].sort((a, b) => {
                // ref: https://stackoverflow.com/questions/24080785/sorting-in-javascript-shouldnt-returning-a-boolean-be-enough-for-a-comparison
                return b[1] - a[1]
            })
        )

        console.log("Katakana List:", mmSorted);

        return mmSorted;
    }
}