package util

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

object Signature {
    fun text(): String {
        return "本文档内容为机器翻译生成"
    }

    private var TEMPLATE_NAV = """
        <div style="background-color: #fff8c5; border: 1px solid #ffd33d; border-radius: 6px; padding: 16px; margin: 16px 0; display: flex; align-items: flex-start; gap: 12px;">
          <p style="">ℹ️ <strong>注意:</strong></p>
          <p style="color: #3d2a00;">
            ${text()}
          </p>
        </div>
    """

    val epubNav: Element by lazy {
        Jsoup.parseBodyFragment(TEMPLATE_NAV.trimIndent())
            .body()
            .child(0)
    }


    fun epubNcx(content: String? = null): Element {
        val template_ncx = """
            <navPoint>
                <navLabel>
                    <text> ${text()} </text>
                </navLabel>
                ${ 
                    if (content != null)  
                        "<content src=\"$content\"/>" 
                    else  
                        "" 
                }
                
            </navPoint>
        """
        return Jsoup.parseBodyFragment(template_ncx.trimIndent())
            .body()
            .child(0)
    }
}
