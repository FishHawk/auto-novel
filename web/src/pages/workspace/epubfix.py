import zipfile
import re,sys
from os import path,mkdir
from urllib.parse import unquote
from xml.etree import ElementTree

class EpubTool:
    def __init__(self,epub_src):
        self.epub = zipfile.ZipFile(epub_src)
        self.epub_src = epub_src
        self.epub_name = path.basename(epub_src)
        self.ebook_root = path.dirname(epub_src)
        self.epub_type = ''
        self.temp_dir = ""
        self._init_namelist()
        self._init_mime_map()
        self._init_opf()
        self.id_to_h_m_p = {}
        self.manifest_list = []
        self.id_to_href = {}
        self.href_to_id = {} 
        self.text_list = []     
        self.css_list = []      
        self.image_list =[]     
        self.font_list = []     
        self.audio_list = []    
        self.video_list = []   
        self.spine_list = []    
        self.other_list = []
        self.errorOPF_log = []        
        self._parse_opf()
    def _init_namelist(self):
        self.namelist = self.epub.namelist()
    
    def _init_mime_map(self):
        self.mime_map = {
            '.bm'    : 'image/bmp',
            '.bmp'   : 'image/bmp',
            '.css'   : 'text/css',
            '.epub'  : 'application/epub+zip',
            '.gif'   : 'image/gif',
            '.htm'   : 'application/xhtml+xml',
            '.html'  : 'application/xhtml+xml',
            '.jpeg'  : 'image/jpeg',
            '.jpg'   : 'image/jpeg',
            '.js'    : 'application/javascript',
            '.m4a'   : 'audio/mp4',
            '.m4v'   : 'video/mp4',
            '.mp3'   : 'audio/mpeg',
            '.mp4'   : 'video/mp4',
            '.ncx'   : 'application/x-dtbncx+xml',
            '.oga'   : 'audio/ogg',
            '.ogg'   : 'audio/ogg',
            '.ogv'   : 'video/ogg',
            '.opf'   : 'application/oebps-package+xml',
            '.otf'   : 'font/otf',
            '.pls'   : 'application/pls+xml',
            '.png'   : 'image/png',
            '.smil'  : 'application/smil+xml',
            '.svg'   : 'image/svg+xml',
            '.tif'   : 'image/tiff',
            '.tiff'  : 'image/tiff',
            '.ttc'   : 'font/collection',
            '.ttf'   : 'font/ttf',
            '.ttml'  : 'application/ttml+xml',
            '.txt'   : 'text/plain',
            '.vtt'   : 'text/vtt',
            '.webm'  : 'video/webm',
            '.webp'  : 'image/webp',
            '.woff'  : 'font/woff',
            '.woff2' : 'font/woff2',
            '.xhtml' : 'application/xhtml+xml',
            '.xml'   : 'application/oebps-page-map+xml',
            '.xpgt'  : 'application/vnd.adobe-page-template+xml',
        }

    def _init_opf(self):
        container_xml = self.epub.read('META-INF/container.xml').decode('utf-8')
        rf = re.match(r'<rootfile[^>]*full-path="(?i:(.*?\.opf))"',container_xml)
        if rf is not None:
            self.opfpath = rf.group(1)
            self.opf = self.epub.read(self.opfpath).decode('utf-8')
            return
        for bkpath in self.namelist:
            if bkpath.lower().endswith('.opf'):
                self.opfpath = bkpath
                self.opf = self.epub.read(self.opfpath).decode('utf-8')
                return
        raise ValueError('无法发现opf文件')

    def _parse_opf(self):
        self.etree_opf = { 'package': ElementTree.fromstring(self.opf) }

        for child in self.etree_opf['package']:
            tag = re.sub(r'\{.*?\}',r'',child.tag)
            self.etree_opf[tag] = child

        self._parse_metadata()
        self._parse_manifest()
        self._parse_spine()
        self._clear_duplicate_id_href()
        self._check_opf_href_avaliable_and_case()
        self._add_files_not_in_opf()

        self.manifest_list = []
        for id in self.id_to_h_m_p:
            href,mime,properties = self.id_to_h_m_p[id]
            self.manifest_list.append((id,href,mime,properties))

        epub_type = self.etree_opf['package'].get('version')

        if epub_type is not None and epub_type in ['2.0','3.0']:
            self.epub_type = epub_type
        else:
            raise ValueError('此脚本不支持该EPUB类型')

        self.tocpath = ''
        self.tocid = ''
        tocid = self.etree_opf['spine'].get('toc')
        self.tocid = tocid if tocid is not None else ''
        
        opf_dir = path.dirname(self.opfpath)
        for id,href,mime,properties in self.manifest_list:

            if mime == 'application/xhtml+xml':
                self.text_list.append((id,href,properties))
            elif mime == 'text/css':
                self.css_list.append((id,href,properties))
            elif 'image/' in mime:
                self.image_list.append((id,href,properties))
            elif 'font/' in mime or href.lower().endswith(('.ttf','.otf','.woff')):
                self.font_list.append((id,href,properties))
            elif 'audio/' in mime:
                self.audio_list.append((id,href,properties))
            elif 'video/' in mime:
                self.video_list.append((id,href,properties))
            elif self.tocid != "" and id == self.tocid:
                opf_dir = path.dirname(self.opfpath)
                self.tocpath = opf_dir + '/' + href if opf_dir else href
            else:
                self.other_list.append((id,href,mime,properties))

        self._check_manifest_and_spine()

    def _parse_metadata(self):
        self.metadata = {}
        for key in ['title','creator','language','subject','source','identifier','cover']:
            self.metadata[key] = ''
        for meta in self.etree_opf['metadata']:
            tag = re.sub(r'\{.*?\}',r'',meta.tag)
            if tag in ['title','creator','language','subject','source','identifier']:
                self.metadata[tag] = meta.text
            elif tag == 'meta':
                if meta.get('name') and meta.get('content'):
                    self.metadata['cover'] = meta.get('content')
        
    def _parse_manifest(self):
        self.id_to_h_m_p = {}
        self.id_to_href = {}
        self.href_to_id = {}

        for item in self.etree_opf['manifest']:
            id = item.get('id')
            href = unquote(item.get('href'))
            mime = item.get('media-type')
            properties = item.get('properties') if item.get('properties') else ''

            self.id_to_h_m_p[id] = (href,mime,properties)
            self.id_to_href[id] = href.lower()
            self.href_to_id[href.lower()] = id
            
    def _parse_spine(self):
        self.spine_list = []
        for itemref in self.etree_opf['spine']:
            sid = itemref.get('idref')
            linear = itemref.get('linear') if itemref.get('linear') else ''
            properties = itemref.get('properties') if itemref.get('properties') else ''
            self.spine_list.append((sid,linear,properties))

    def _clear_duplicate_id_href(self):
        id_used = [ x[0] for x in self.spine_list ]
        if self.metadata['cover']:
            id_used.append(self.metadata['cover'])

        del_id = []
        for id,href in self.id_to_href.items():
            if self.href_to_id[href] != id:
                if id in id_used and self.href_to_id[href] not in id_used:
                    if id not in del_id:
                        del_id.append(self.href_to_id[href])
                    self.href_to_id[href] = id
                elif id in id_used and self.href_to_id[href] in id_used:
                    continue
                else:
                    if id not in del_id:
                        del_id.append(id)

        for id in del_id:
            self.errorOPF_log.append(("duplicate_id",id))
            del self.id_to_href[id]
            del self.id_to_h_m_p[id]

    def _check_manifest_and_spine(self):
        spine_idrefs = [i for i,j,k in self.spine_list]

        for idref in spine_idrefs:
            if not self.id_to_h_m_p.get(idref):
                self.errorOPF_log.append(("invalid_idref",idref))

        for mid,opf_href,mime,properties in self.manifest_list:
            if mime == "application/xhtml+xml":
                if mid not in spine_idrefs:
                    self.errorOPF_log.append(("xhtml_not_in_spine",mid))
    
    def _check_opf_href_avaliable_and_case(self):
                
        del_id = []             
        del_href = []           
        corrected_id_hrefs = []
        error_log = []
        lowerPath_to_archivePath = {x.lower():x for x in self.epub.namelist()}
        
        for id in self.id_to_h_m_p:
            href, mime, prop = self.id_to_h_m_p[id]
            bookPath = get_bookpath(href, self.opfpath)
            archivePath = lowerPath_to_archivePath.get(bookPath.lower(),None)
            if archivePath is None:
                del_id.append(id)
                del_href.append(href.lower())
                error_log.append((href, None))
            elif bookPath != archivePath:
                corrected_href = get_relpath(self.opfpath, archivePath)
                corrected_id_hrefs.append((id, corrected_href))
                error_log.append((href, corrected_href))
                
        for id, corrected_href in corrected_id_hrefs:
            href, mime, prop = self.id_to_h_m_p[id]
            self.id_to_h_m_p[id] = (corrected_href, mime, prop)
        
        for id in del_id:
            del self.id_to_href[id]
            del self.id_to_h_m_p[id]
        
        for href_l in del_href:
            del self.href_to_id[href_l]
        
        if error_log != []:
            self.errorLink_log[self.opfpath] = error_log

    def _add_files_not_in_opf(self):

        hrefs_not_in_opf = []
        for archive_path in self.namelist:
            if archive_path.lower().endswith(('.html','.xhtml','.css','.jpg','.jpeg','.bmp','.gif',
                                        '.png','.webp','.svg','.ttf','.otf','.js','.mp3','.mp4','.smil')):
                opf_href = get_relpath(self.opfpath, archive_path)
                if opf_href.lower() not in self.href_to_id.keys():
                    hrefs_not_in_opf.append(opf_href)

        def allocate_id(href):
            basename = path.basename(href)
            if 'A' <= basename[0] <= 'Z' or 'a' <= basename[0] <= 'z':
                new_id = basename
            else:
                new_id = 'x' + basename
            pre,suf = path.splitext(new_id)
            pre_ = pre
            i = 0
            while pre_ + suf in self.id_to_href.keys():
                i += 1
                pre_ = pre + '_' + str(i)
            new_id = pre_ + suf
            return new_id

        for href in hrefs_not_in_opf:
            new_id = allocate_id(href)
            self.id_to_href[new_id] = href.lower()
            self.href_to_id[href.lower()] = new_id
            ext = path.splitext(href)[1]
            ext = ext.lower()
            try:
                mime = self.mime_map[ext]
            except KeyError:
                mime = 'text/plain'
            self.id_to_h_m_p[new_id] = (href,mime,'')

    def create_tgt_epub(self):
        if not path.exists("重构EPUB"):
            mkdir("重构EPUB")
        return zipfile.ZipFile(path.join('重构EPUB',self.epub_name), 'w', zipfile.ZIP_STORED)

    def restructure(self):
        self.tgt_epub = self.create_tgt_epub()
        mimetype = self.epub.read('mimetype')
        self.tgt_epub.writestr('mimetype', mimetype, zipfile.ZIP_DEFLATED)
        metainf_data = self.epub.read('META-INF/container.xml').decode('utf-8')
        metainf_data = re.sub(r'<rootfile[^>]*media-type="application/oebps-[^>]*/>',
                              r'<rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>',metainf_data)
        self.tgt_epub.writestr('META-INF/container.xml', bytes(metainf_data,encoding='utf-8'), zipfile.ZIP_DEFLATED)
        re_path_map = {'text':{},'css':{},'image':{},'font':{},'audio':{},'video':{},'other':{}}
        basename_log = {'text':[],'css':[],'image':[],'font':[],'audio':[],'video':[],'other':[]}
        lowerPath_to_originPath = {}

        def auto_rename(id,href,ftype):
            filename,ext = path.splitext(path.basename(href))
            filename_ = filename
            num = 0
            while filename_ + ext in basename_log[ftype]:
                num += 1
                filename_ = filename + '_' + str(num)
            basename = filename_ + ext
            basename_log[ftype].append(basename)
            return basename

        def check_link(filename, bkpath, href, self, target_id = ""):
            if href == "" or href.startswith(("http://","https://","res:/","file:/","data:")):
                return None
            if bkpath.lower() in lowerPath_to_originPath.keys():
                if bkpath != lowerPath_to_originPath[bkpath.lower()]:
                    correct_path = lowerPath_to_originPath[bkpath.lower()]
                    self.errorLink_log.setdefault(filename,[])
                    self.errorLink_log[filename].append((href + target_id,correct_path))
                    bkpath = correct_path
            else:
                self.errorLink_log.setdefault(filename,[])
                self.errorLink_log[filename].append((href + target_id,None))
                return None
            return bkpath

        for id,href,properties in self.text_list:
            bkpath = get_bookpath(href,self.opfpath)
            basename = auto_rename(id,href,'text')
            re_path_map['text'][bkpath] = basename
            lowerPath_to_originPath[bkpath.lower()] = bkpath

        for id,href,properties in self.css_list:
            bkpath = get_bookpath(href,self.opfpath)
            basename = auto_rename(id,href,'css')
            re_path_map['css'][bkpath] = basename
            lowerPath_to_originPath[bkpath.lower()] = bkpath

        for id,href,properties in self.image_list:
            bkpath = get_bookpath(href,self.opfpath)
            basename = auto_rename(id,href,'image')
            re_path_map['image'][bkpath] = basename
            lowerPath_to_originPath[bkpath.lower()] = bkpath

        for id,href,properties in self.font_list:
            bkpath = get_bookpath(href,self.opfpath)
            basename = auto_rename(id,href,'font')
            re_path_map['font'][bkpath] = basename
            lowerPath_to_originPath[bkpath.lower()] = bkpath

        for id,href,properties in self.audio_list:
            bkpath = get_bookpath(href,self.opfpath)
            basename = auto_rename(id,href,'audio')
            re_path_map['audio'][bkpath] = basename
            lowerPath_to_originPath[bkpath.lower()] = bkpath

        for id,href,properties in self.video_list:
            bkpath = get_bookpath(href,self.opfpath)
            basename = auto_rename(id,href,'video')
            re_path_map['video'][bkpath] = basename
            lowerPath_to_originPath[bkpath.lower()] = bkpath

        for id,href,mime,properties in self.other_list:
            bkpath = get_bookpath(href,self.opfpath)
            basename = auto_rename(id,href,'other')
            re_path_map['other'][bkpath] = basename
            lowerPath_to_originPath[bkpath.lower()] = bkpath

        if self.tocpath:
            toc = self.epub.read(self.tocpath).decode('utf-8')
            toc_dir = path.dirname(self.tocpath)
            def re_toc_href(match):
                href = match.group(2)
                href = unquote(href).strip()
                if "#" in href:
                    href,target_id = href.split('#')
                    target_id = '#' + target_id
                else:
                    target_id = ''
                bkpath = get_bookpath(href, self.tocpath)
                bkpath = check_link(self.tocpath, bkpath, href, self, target_id)
                if not bkpath:
                    return match.group()
                filename = path.basename(bkpath)
                return 'src="Text/' + filename + '"' + target_id
            toc = re.sub(r'src=([\'\"])(.*?)\1',re_toc_href,toc)
            self.tgt_epub.writestr('OEBPS/toc.ncx', bytes(toc,encoding='utf-8'), zipfile.ZIP_DEFLATED)

        for xhtml_bkpath,new_name in re_path_map['text'].items():
            text = self.epub.read(xhtml_bkpath).decode('utf-8')
            if not text.startswith('<?xml'):
                text = '<?xml version="1.0" encoding="utf-8"?>\n'+ text
            if not re.match(r'(?s).*<!DOCTYPE html',text):
                text = re.sub(r'(<\?xml.*?>)\n*',r'\1\n<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN"\n  "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd">\n',text,1)
             #修改a[href]

            def re_href(match):
                href = match.group(3)
                href = unquote(href).strip()
                if "#" in href:
                    href,target_id = href.split('#')
                    target_id = '#' + target_id
                else:
                    target_id = ''
                
                bkpath = get_bookpath(href, xhtml_bkpath)
                bkpath = check_link(xhtml_bkpath, bkpath, href, self, target_id)
                if not bkpath:
                    return match.group()

                if href.lower().endswith(('.jpg','.jpeg','.png','.bmp','.gif','.webp')):
                    filename = re_path_map['image'][bkpath]
                    return match.group(1) + '../Images/' + filename + match.group(4)
                elif href.lower().endswith('.css'):
                    filename = re_path_map['css'][bkpath]
                    return '<link href="../Styles/' + filename +'" type="text/css" rel="stylesheet"/>'
                elif href.lower().endswith(('.xhtml','.html')):
                    filename = re_path_map['text'][bkpath]
                    return match.group(1) + filename + target_id + match.group(4)
                else:
                    return match.group()
                
            text = re.sub(r'(<[^>]*href=([\'\"]))(.*?)(\2[^>]*>)',re_href,text)

            #修改src
            def re_src(match):
                href = match.group(3)
                href = unquote(href).strip()
                bkpath = get_bookpath(href, xhtml_bkpath)
                bkpath = check_link(xhtml_bkpath, bkpath, href, self)
                if not bkpath:
                    return match.group()

                if href.lower().endswith(('.jpg','.jpeg','.png','.bmp','.gif','.webp','.svg')):
                    filename = re_path_map['image'][bkpath]
                    return match.group(1) + '../Images/' + filename + match.group(4)
                elif href.lower().endswith('.mp3'):
                    filename = re_path_map['audio'][bkpath]
                    return match.group(1) + '../Audio/' + filename + match.group(4)
                elif href.lower().endswith('.mp4'):
                    filename = re_path_map['video'][bkpath]
                    return match.group(1) + '../Video/' + filename + match.group(4)
                elif href.lower().endswith('.js'):
                    filename = re_path_map['other'][bkpath]
                    return match.group(1) + '../Misc/' + filename + match.group(4)
                else:
                    return match.group()
            text = re.sub(r'(<[^>]* src=([\'\"]))(.*?)(\2[^>]*>)',re_src,text)
            
            #修改 url
            def re_url(match):
                url = match.group(2)
                url = unquote(url).strip()
                bkpath = get_bookpath(url, xhtml_bkpath)
                bkpath = check_link(xhtml_bkpath, bkpath, url, self)
                if not bkpath:
                    return match.group()

                if url.lower().endswith(('.ttf','.otf')):
                    filename = re_path_map['font'][bkpath]
                    return match.group(1) + '../Fonts/' + filename + match.group(3)
                elif url.lower().endswith(('.jpg','.jpeg','.png','.bmp','.gif','.webp','.svg')):
                    filename = re_path_map['image'][bkpath]
                    return match.group(1) + '../Images/' + filename + match.group(3)
                else:
                    return match.group()
            text = re.sub(r'(url\([\'\"]?)(.*?)([\'\"]?\))',re_url,text)
            self.tgt_epub.writestr('OEBPS/Text/'+new_name, bytes(text,encoding='utf-8'), zipfile.ZIP_DEFLATED)
        #css文件
        for css_bkpath,new_name in re_path_map['css'].items():
            try:
                css = self.epub.read(css_bkpath).decode('utf-8')
            except:
                continue
            #修改 @import 
            def re_import(match):
                if match.group(2):
                    href = match.group(2)
                else:
                    href = match.group(3)
                href = unquote(href).strip()
                if not href.lower().endswith('.css'):
                    return match.group()
                filename = path.basename(href)
                return '@import "' + filename + '"'
            css = re.sub(r'@import ([\'\"])(.*?)\1|@import url\([\'\"]?(.*?)[\'\"]?\)',re_import,css)
            #修改 css的url
            def re_css_url(match):
                url = match.group(2)
                url = unquote(url).strip()
                bkpath = get_bookpath(url, css_bkpath)
                bkpath = check_link(css_bkpath, bkpath, url, self)
                if not bkpath:
                    return match.group()
                if url.lower().endswith(('.ttf','.otf')):
                    filename = re_path_map['font'][bkpath]
                    return match.group(1) + '../Fonts/' + filename + match.group(3)
                elif url.lower().endswith(('.jpg','.jpeg','.png','.bmp','.gif','.webp','.svg')):
                    filename = re_path_map['image'][bkpath]
                    return match.group(1) + '../Images/' + filename + match.group(3)
                else:
                    return match.group()
            css = re.sub(r'(url\([\'\"]?)(.*?)([\'\"]?\))',re_css_url,css)
            self.tgt_epub.writestr('OEBPS/Styles/'+new_name, bytes(css,encoding='utf-8'), zipfile.ZIP_DEFLATED)
        #图片
        for img_bkpath,new_name in re_path_map['image'].items():
            try:
                img = self.epub.read(img_bkpath)
            except:
                continue
            self.tgt_epub.writestr('OEBPS/Images/'+new_name, img, zipfile.ZIP_DEFLATED)
            
        for font_bkpath,new_name in re_path_map['font'].items():
            try:
                font = self.epub.read(font_bkpath)
            except:
                continue
            self.tgt_epub.writestr('OEBPS/Fonts/'+new_name, font, zipfile.ZIP_DEFLATED)

        for audio_bkpath,new_name in re_path_map['audio'].items():
            try:
                audio = self.epub.read(audio_bkpath)
            except:
                continue
            self.tgt_epub.writestr('OEBPS/Audio/'+new_name, audio, zipfile.ZIP_DEFLATED)

        for video_bkpath,new_name in re_path_map['video'].items():
            try:
                video = self.epub.read(video_bkpath)
            except:
                continue
            self.tgt_epub.writestr('OEBPS/Video/'+new_name, video, zipfile.ZIP_DEFLATED)

        for font_bkpath,new_name in re_path_map['other'].items():
            try:
                other = self.epub.read(font_bkpath)
            except:
                continue
            self.tgt_epub.writestr('OEBPS/Misc/'+new_name, other, zipfile.ZIP_DEFLATED)
        #OPF
        manifest_text = '<manifest>'
        
        for id,href,mime,prop in self.manifest_list:
            bkpath = get_bookpath(href, self.opfpath)
            prop_ = ' properties="' + prop +'"' if prop else ''
            if mime == 'application/xhtml+xml':
                filename = re_path_map['text'][bkpath]
                manifest_text += '    <item id="{id}" href="{href}" media-type="{mime}"{prop}/>'.format(id=id,href='Text/'+filename,mime=mime,prop=prop_)
            elif mime == 'text/css':
                filename = re_path_map['css'][bkpath]
                manifest_text += '    <item id="{id}" href="{href}" media-type="{mime}"{prop}/>'.format(id=id,href='Styles/'+filename,mime=mime,prop=prop_)
            elif 'image/' in mime:
                filename = re_path_map['image'][bkpath]
                manifest_text += '    <item id="{id}" href="{href}" media-type="{mime}"{prop}/>'.format(id=id,href='Images/'+filename,mime=mime,prop=prop_)
            elif 'font/' in mime or href.lower().endswith(('.ttf','.otf','.woff')):
                filename = re_path_map['font'][bkpath]
                manifest_text += '    <item id="{id}" href="{href}" media-type="{mime}"{prop}/>'.format(id=id,href='Fonts/'+filename,mime=mime,prop=prop_)
            elif 'audio/' in mime:
                filename = re_path_map['audio'][bkpath]
                manifest_text += '    <item id="{id}" href="{href}" media-type="{mime}"{prop}/>'.format(id=id,href='Audio/'+filename,mime=mime,prop=prop_)
            elif 'video/' in mime:
                filename = re_path_map['video'][bkpath]
                manifest_text += '    <item id="{id}" href="{href}" media-type="{mime}"{prop}/>'.format(id=id,href='Video/'+filename,mime=mime,prop=prop_)
            elif id == self.tocid:
                manifest_text += '    <item id="{id}" href="toc.ncx" media-type="application/x-dtbncx+xml"/>'.format(id=id)
            else:
                filename = re_path_map['other'][bkpath]
                manifest_text += '    <item id="{id}" href="{href}" media-type="{mime}"{prop}/>'.format(id=id,href='Misc/'+filename,mime=mime,prop=prop_)

        manifest_text += '  </manifest>'
        opf = re.sub(r'(?s)<manifest.*?>.*?</manifest>',manifest_text,self.opf,1)
        
        def re_refer(match):
            href = match.group(3)
            href = unquote(href).strip()
            basename = path.basename(href)
            filename = unquote(basename)
            if not basename.endswith('.ncx'):
                return match.group(1) + '../Text/' + filename + match.group(4)
            else:
                return match.group()
        opf = re.sub(r'(<reference[^>]*href=([\'\"]))(.*?)(\2[^>]*/>)',re_refer,opf)
        self.tgt_epub.writestr('OEBPS/content.opf', bytes(opf,encoding='utf-8'), zipfile.ZIP_DEFLATED)
        self.tgt_epub.close()
        self.epub.close()


#相对路径计算函数
def get_relpath(from_path,to_path):
    #from_path 和 to_path 都需要是绝对路径
    from_path = re.split(r'[\\/]',from_path)
    to_path = re.split(r'[\\/]',to_path)
    while from_path[0] == to_path[0]:
        from_path.pop(0),to_path.pop(0)
    to_path = '../' * (len(from_path) - 1) + '/'.join(to_path)
    return to_path            

#计算bookpath
def get_bookpath(relative_path,refer_bkpath):
    # relative_path 相对路径，一般是href
    # refer_bkpath 参考的绝对路径

    relative_ = re.split(r'[\\/]',relative_path)
    refer_ = re.split(r'[\\/]',refer_bkpath)
    
    back_step = 0
    while relative_[0] == '..':
        back_step += 1
        relative_.pop(0) 

    if len(refer_) <= 1:
        return '/'.join(relative_)
    else:
        refer_.pop(-1)

    if back_step < 1:
        return '/'.join(refer_+relative_)
    elif back_step > len(refer_):
        return '/'.join(relative_)

    #len(refer_) > 1 and back_setp <= len(refer_):
    while back_step > 0 and len(refer_) > 0:
        refer_.pop(-1)
        back_step -= 1
    
    return '/'.join(refer_ + relative_)


def epub_sources():
    if len(sys.argv) <= 1:
        return sys.argv
    epub_srcs = []
    exe_path = path.dirname(sys.argv[0])
    epub_srcs.append(exe_path)
    for epub_src in sys.argv[1:None]:
        filename = path.basename(epub_src)
        basename,ext = path.splitext(filename)
        if ext.lower() == '.epub':
            if path.exists(epub_src):
                epub_srcs.append(epub_src)
    return epub_srcs

def run(epub_src):
    print('%s 正在尝试重构EPUB'%epub_src)
    epub = EpubTool(epub_src)
    epub.restructure() #重构
    el = epub.errorLink_log.copy()
    del_keys = []
    for file_path,log in  epub.errorLink_log.items():
        # 忽略 CSS 文件里未找到对应文件的链接（CSS里这类链接较多且影响不大，可以忽略）
        if file_path.lower().endswith(".css"):
            el[file_path] = list(filter(lambda x:x[1] is not None, log))
            if el[file_path] == []:
                del_keys.append(file_path)
    for key in del_keys:
        del el[key]
    
    if epub.errorOPF_log:
        print("\n-------在 OPF文件 发现问题------:")
        for error_type,error_value in epub.errorOPF_log:
            if error_type == "duplicate_id":
                print("\n问题：发现manifest节点内部存在重复ID %s !!!"%error_value)
                print("措施：已自动清除重复ID对应的manifest项。")
            elif error_type == "invalid_idref":
                print("\n问题：发现spine节点内部存在无效引用ID %s !!!"%error_value)
                print("措施：请自行检查spine内的itemref节点并手动修改，确保引用的ID存在于manifest的item项。\n"+
                      "      （大小写不一致也会导致引用无效。）\n")
            elif error_type == "xhtml_not_in_spine":
                print("\n问题：发现ID为 %s 的文件manifest中登记为application/xhtml+xml类型，但不被spine节点的项所引用" %error_value)
                print("措施：自行检查该文件是否需要被spine引用。部分阅读器中，如果存在xhtml文件不被spine引用，可能导致epub无法打开。\n")
            
    if el:
        for file_path,log in el.items():
            basename = path.basename(file_path)
            print("\n-----在 %s 发现问题链接-----:\n"%basename)
            for href,correct_path in log:
                if correct_path is not None:
                    print("链接：%s\n问题：与实际文件名大小写不一致！\n措施：程序已自动纠正链接。\n"%href)
                else:
                    print("链接：%s\n问题：未能找到对应文件！！！\n"%href)
    print('%s 重构EPUB成功'%epub_src)

def main():
    # windows 下双击启动
    if len(sys.argv) == 1:
        while True:
            epub_src = input("\n【使用说明】请把EPUB文件拖曳到本窗口上：")
            epub_src = epub_src.strip("\'").strip('\"').strip()
            run(epub_src)
            input("\n请按回车键继续")
    
    # 命令行启动：python <脚本路径> <文件路径>
    elif len(sys.argv) == 2:
        epub_src = sys.argv[1]
        epub_src = epub_src.strip("\'").strip('\"').strip()
        run(epub_src)

if __name__ == "__main__":
    print('【EPUB重构工具 v%s】\n'%__version__ +
            '1、 将epub目录结构规范化至sigil规范格式。\n' +
            '2、 将没有列入manifest项的epub有效文件自动列入manifest项。\n' +
            '3、 自动清除manifest中携带重复ID或多余ID的无效项。\n'+
            '    脚本将优先保留spine或metadata中关联的ID。\n'+
            '4、 自动检查并提醒spine节点中引用无效ID的itemref项。\n'+
            '5、 自动检查并提醒manifest节点中xhtml类型文件不被spine节点引用的情况。\n'+
            '6、 自动检测并纠正实际文件名与对应的引用链接大小写不一致的问题。\n'+
            '7、 自动检测并提醒找不到对应文件的链接。')

    #test_main()
    main()

