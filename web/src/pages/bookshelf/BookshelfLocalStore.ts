import { Locator } from '@/data';
import { LocalVolumeMetadata } from '@/model/LocalVolume';
import { TranslateTaskDescriptor, TranslatorId } from '@/model/Translator';
import { downloadFile } from '@/util';

type BookshelfLocalStore = {
  volumes: LocalVolumeMetadata[];
};

export const useBookshelfLocalStore = defineStore('BookshelfLocal', {
  state: () =>
    <BookshelfLocalStore>{
      volumes: [],
    },
  actions: {
    async loadVolumes() {
      const repo = await Locator.localVolumeRepository();
      this.volumes = await repo.listVolume();
      return this.volumes;
    },
    async deleteVolume(id: string) {
      const repo = await Locator.localVolumeRepository();
      await repo.deleteVolume(id);
      this.volumes = this.volumes.filter((it) => it.id !== id);
    },
    async addVolume(file: File) {
      const repo = await Locator.localVolumeRepository();
      await repo.createVolume(file);
      await this.loadVolumes();
    },
    async deleteVolumes(ids: string[]) {
      const repo = await Locator.localVolumeRepository();

      let failed = 0;
      await Promise.all(
        ids.map(async (id: string) => {
          try {
            await repo.deleteVolume(id);
            this.volumes = this.volumes.filter((it) => it.id !== id);
          } catch (error) {
            failed += 1;
            console.error(`删除错误：${error}\n标题:${id}`);
          }
        }),
      );
      return { success: ids.length - failed, failed };
    },
    async downloadVolumes(ids: string[]) {
      const { setting } = Locator.settingRepository();
      const { mode, translationsMode, translations } =
        setting.value.downloadFormat;

      const repo = await Locator.localVolumeRepository();

      const { BlobReader, BlobWriter, ZipWriter } = await import(
        '@zip.js/zip.js'
      );
      const zipBlobWriter = new BlobWriter();
      const writer = new ZipWriter(zipBlobWriter);

      let failed = 0;
      await Promise.all(
        ids.map(async (id: string) => {
          try {
            const { filename, blob } = await repo.getTranslationFile({
              id,
              mode,
              translationsMode,
              translations,
            });
            await writer.add(filename, new BlobReader(blob));
          } catch (error) {
            failed += 1;
            console.error(`生成文件错误：${error}\n标题:${id}`);
          }
        }),
      );

      await writer.close();
      const zipBlob = await zipBlobWriter.getData();
      downloadFile(`批量下载[${ids.length}].zip`, zipBlob);

      return { success: ids.length - failed, failed };
    },
    queueJobsToWorkspace(
      ids: string[],
      {
        level,
        type,
        shouldTop,
      }: {
        level: 'expire' | 'all';
        type: 'gpt' | 'sakura';
        shouldTop: boolean;
      },
    ) {
      const workspace =
        type === 'gpt'
          ? Locator.gptWorkspaceRepository()
          : Locator.sakuraWorkspaceRepository();

      let failed = 0;
      ids.forEach((id) => {
        const task = TranslateTaskDescriptor.local(id, {
          level,
          sync: false,
          forceMetadata: false,
          startIndex: 0,
          endIndex: 65535,
        });
        const job = {
          task,
          description: id,
          createAt: Date.now(),
        };
        const success = workspace.addJob(job);
        if (success && shouldTop) {
          workspace.topJob(job);
        }
        if (!success) {
          failed += 1;
        }
      });

      return { success: ids.length - failed, failed };
    },
    async deleteAllVolumes() {
      const repo = await Locator.localVolumeRepository();
      await repo.deleteVolumesDb();
      await this.loadVolumes();
    },
  },
});

export namespace BookshelfLocalUtil {
  export const filterAndSortVolumes = (
    volumes: LocalVolumeMetadata[],
    {
      query,
      enableRegexMode,
      order,
    }: {
      query: string;
      enableRegexMode: boolean;
      order: {
        value: 'byCreateAt' | 'byReadAt' | 'byId';
        desc: boolean;
      };
    },
  ) => {
    if (query) {
      const buildSearchFilter = () => {
        const parts = query
          .trim()
          .split(' ')
          .filter((v) => v.length > 0);
        if (enableRegexMode) {
          const regs = parts.map((it) => new RegExp(it, 'i'));
          return (s: string) => !regs.some((r) => !r.test(s));
        } else {
          return (s: string) => !parts.some((r) => !s.includes(r));
        }
      };
      const filter = buildSearchFilter();
      volumes = volumes.filter((volume) => filter(volume.id));
    }

    return volumes.sort((a, b) => {
      let delta = 0;
      switch (order.value) {
        case 'byId':
          delta = b.id.localeCompare(a.id);
          break;
        case 'byCreateAt': {
          delta = a.createAt - b.createAt;
          break;
        }
        case 'byReadAt': {
          delta = (a.readAt ?? 0) - (b.readAt ?? 0);
          break;
        }
        default:
          console.error(`未支持${order.value}排序`);
          break;
      }
      return order.desc ? -delta : delta;
    });
  };

  export const downloadVolumes = async (
    volumes: LocalVolumeMetadata[],
    {
      mode,
      translationsMode,
      translations,
      onError,
    }: {
      mode: 'zh' | 'zh-jp' | 'jp-zh';
      translationsMode: 'parallel' | 'priority';
      translations: TranslatorId[];
      onError: (id: string, error: unknown) => void;
    },
  ) => {
    const { BlobReader, BlobWriter, ZipWriter } = await import(
      '@zip.js/zip.js'
    );

    const repo = await Locator.localVolumeRepository();

    const zipBlobWriter = new BlobWriter();
    const writer = new ZipWriter(zipBlobWriter);

    await Promise.all(
      volumes.map(async (volume: LocalVolumeMetadata) => {
        try {
          const { filename, blob } = await repo.getTranslationFile({
            id: volume.id,
            mode,
            translationsMode,
            translations,
          });
          await writer.add(filename, new BlobReader(blob));
        } catch (error) {
          onError(volume.id, error);
          // message.error(`${volume.id} 文件生成错误：${error}`);
        }
      }),
    );

    await writer.close();
    const zipBlob = await zipBlobWriter.getData();
    downloadFile(`批量下载[${volumes.length}].zip`, zipBlob);
  };
}
