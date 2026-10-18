export const readDroppedEntry = async (
  entry: FileSystemEntry,
): Promise<File[]> => {
  if (entry.isFile) {
    return [
      await new Promise<File>((resolve, reject) => {
        (entry as FileSystemFileEntry).file(resolve, reject);
      }),
    ];
  }

  const reader = (entry as FileSystemDirectoryEntry).createReader();
  const files: File[] = [];
  // Chromium 每次可能只返回一批目录项，必须读取到空批次为止。
  while (true) {
    const entries = await new Promise<FileSystemEntry[]>((resolve, reject) => {
      reader.readEntries(resolve, reject);
    });
    if (entries.length === 0) return files;
    files.push(...(await Promise.all(entries.map(readDroppedEntry))).flat());
  }
};
