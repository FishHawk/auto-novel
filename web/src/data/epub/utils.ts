export function extract_katakana(text: string): Array<String> {
    // Ref: https://gist.github.com/terrancesnyder/1345094
    // full_with_katakana = "ァ-ン"
    // half_with_katakana = "ｧ-ﾝﾞﾟ";
    // hypen = ー

    const matches = text.matchAll(/([ァ-ンｧ-ﾝﾞﾟー]+)/gm);

    let ret = [];
    for (let match of matches) {
        ret.push(match[1]);
    }

    return ret;
}

export function is_text_page(filename: string): boolean {
    const support_exts = [".xhtml", ".html", ".htm"];

    for (let ext of support_exts) {
        if (filename.endsWith(ext))
            return true;
    }

    return false;
}

export function extract_text_from_xhtml(content: string, remove_ruby = true): Array<string> {
    let ret: Array<string> = [];

    let html = document.createElement('html');
    html.innerHTML = content;

    //FIXME(kuriko): find a better way to extract text from html
    //  Maybe use html-to-text package instead
    let els: Array<HTMLElement> = Array.from(html.querySelectorAll("h1,h2,h3,h4,h5,h6,p,title"));

    for (let el of els) {
        if (remove_ruby) {
            let html = el.innerHTML;
            html.replaceAll(/<rt>>/gm, "(")
            html.replaceAll(/<\/rt>>/gm, ")")
            el.innerHTML = html;
        }
        let text = el.innerText || el.textContent || null;

        if (text && text.length > 0)  {
            ret.push(text);
        } else if (text == null){
            console.debug("Invalid node: ", el);
        }
    }

    return ret;
}