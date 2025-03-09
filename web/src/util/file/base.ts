export class BaseFile {
  name: string;
  protected rawFile?: File;

  protected constructor(name: string, rawFile?: File) {
    this.name = name;
    if (rawFile) this.rawFile = rawFile;
  }
}
