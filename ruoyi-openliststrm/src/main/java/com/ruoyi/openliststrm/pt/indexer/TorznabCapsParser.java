package com.ruoyi.openliststrm.pt.indexer;

import com.ruoyi.common.utils.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * 解析 Torznab {@code t=caps} 响应，提取 movie-search/tv-search 是否支持 imdbid/tmdbid 参数。
 *
 * @author Jack
 */
public final class TorznabCapsParser {

    private TorznabCapsParser() {
    }

    /**
     * @param xml t=caps 的响应体，允许为 null/空/非法 XML
     * @return 解析出的能力；响应为空、解析失败、或没有 searching 节点时返回 {@link IndexerCapability#NONE}
     */
    public static IndexerCapability parse(String xml) {
        if (StringUtils.isBlank(xml)) {
            return IndexerCapability.NONE;
        }
        try {
            Document doc = SafeXmlDocuments.parse(xml);
            Element searching = firstChildElement(doc.getDocumentElement(), "searching");
            if (searching == null) {
                return IndexerCapability.NONE;
            }
            Element movieSearch = firstChildElement(searching, "movie-search");
            Element tvSearch = firstChildElement(searching, "tv-search");
            return new IndexerCapability(
                    supportsParam(movieSearch, "imdbid"),
                    supportsParam(movieSearch, "tmdbid"),
                    supportsParam(tvSearch, "imdbid"),
                    supportsParam(tvSearch, "tmdbid"));
        } catch (Exception e) {
            return IndexerCapability.NONE;
        }
    }

    private static boolean supportsParam(Element searchElement, String param) {
        if (searchElement == null) {
            return false;
        }
        if (!"yes".equalsIgnoreCase(searchElement.getAttribute("available"))) {
            return false;
        }
        String supportedParams = searchElement.getAttribute("supportedParams");
        if (StringUtils.isBlank(supportedParams)) {
            return false;
        }
        for (String token : supportedParams.split(",")) {
            if (param.equalsIgnoreCase(token.trim())) {
                return true;
            }
        }
        return false;
    }

    private static Element firstChildElement(Element parent, String tag) {
        if (parent == null) {
            return null;
        }
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && tag.equals(node.getNodeName())) {
                return (Element) node;
            }
        }
        return null;
    }
}
