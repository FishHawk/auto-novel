import io
import posixpath as zip_path
from PIL import Image
from lxml import etree
from urllib.parse import unquote
import zipfile


def convert_webp(bytes_input: bytes, q: int = 90):
    imgMemInput = io.BytesIO(bytes_input)
    img = Image.open(imgMemInput)
    imgMemOutput = io.BytesIO()
    img.save(imgMemOutput, format="webp", quality=q)
    return imgMemOutput.getvalue()


NAMESPACES = {
    "XML": "http://www.w3.org/XML/1998/namespace",
    "EPUB": "http://www.idpf.org/2007/ops",
    "DAISY": "http://www.daisy.org/z3986/2005/ncx/",
    "OPF": "http://www.idpf.org/2007/opf",
    "CONTAINERNS": "urn:oasis:names:tc:opendocument:xmlns:container",
    "DC": "http://purl.org/dc/elements/1.1/",
    "XHTML": "http://www.w3.org/1999/xhtml",
}

IMAGE_MEDIA_TYPES = ["image/jpeg", "image/jpg", "image/png"]

CONTAINER_PATH = "META-INF/container.xml"


def parse_string(s):
    parser = etree.XMLParser(recover=True, resolve_entities=False)
    try:
        tree = etree.parse(io.BytesIO(s.encode("utf-8")), parser=parser)
    except:
        tree = etree.parse(io.BytesIO(s), parser=parser)
    return tree


def read_file(zf: zipfile.ZipFile, name: str):
    name = zip_path.normpath(name)
    return zf.read(name)


class Epub:
    items = {}

    def __init__(self, filename: str) -> None:
        with zipfile.ZipFile(filename, "r") as zf:
            self._load_container(zf)
            self._load_manifest(zf)

    def _load_container(self, zf: zipfile.ZipFile):
        self.container = read_file(zf, "META-INF/container.xml")
        tree = parse_string(self.container)

        for root_file in tree.findall(
            "//xmlns:rootfile[@media-type]",
            namespaces={"xmlns": NAMESPACES["CONTAINERNS"]},
        ):
            if root_file.get("media-type") == "application/oebps-package+xml":
                self.opf_file = root_file.get("full-path")
                self.opf_dir = zip_path.dirname(self.opf_file)

    def _load_manifest(self, zf: zipfile.ZipFile):
        opf = read_file(zf, self.opf_file)
        tree = parse_string(opf)

        r: etree._Element
        for r in tree.find("{%s}manifest" % (NAMESPACES["OPF"])):
            if r is not None and r.tag != "{%s}item" % NAMESPACES["OPF"]:
                continue
            href = unquote(r.get("href"))
            media_type = r.get("media-type")
            content = read_file(zf, zip_path.join(self.opf_dir, href))
            if media_type in IMAGE_MEDIA_TYPES:
                r.set("media-type", "image/webp")
                content = convert_webp(content)
            self.items[href] = content

        self.opf = etree.tostring(
            tree, pretty_print=True, encoding="utf-8", xml_declaration=True
        )

    def write(self, filename):
        with zipfile.ZipFile(filename, "w", zipfile.ZIP_DEFLATED) as zf:
            zf.writestr(
                "mimetype", "application/epub+zip", compress_type=zipfile.ZIP_STORED
            )
            zf.writestr(CONTAINER_PATH, self.container)
            zf.writestr(self.opf_file, self.opf)
            for item_filename, content in self.items.items():
                zf.writestr(f"{self.opf_dir}/{item_filename}", content)


Epub("test.epub").write("test1.epub")
Epub("2.epub").write("22.epub")
