package epub

fun EpubReader.copyTo(writer: EpubWriter, path: String) =
    writer.writeBinaryFile(path, readFileAsBinary(path))