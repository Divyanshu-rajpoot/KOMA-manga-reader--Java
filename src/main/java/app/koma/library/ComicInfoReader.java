package app.koma.library;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;

/** Reads the fields KOMA uses from ComicInfo.xml (Series, Number, Manga, LocalizedSeries, Format). */
public final class ComicInfoReader {
    private ComicInfoReader() {}

    public record Info(String series, Double number, String localizedSeries, boolean rightToLeft, boolean webtoon) {}

    public static Info parse(byte[] xml) {
        if (xml == null || xml.length == 0) return null;
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setExpandEntityReferences(false);
            f.setXIncludeAware(false);
            Document d = f.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
            Element root = d.getDocumentElement();
            String series = text(root, "Series");
            String number = text(root, "Number");
            if (number == null) number = text(root, "Volume");
            Double n = null;
            if (number != null) {
                try {
                    n = Double.parseDouble(number.trim());
                } catch (NumberFormatException ignored) {
                    // non-numeric numbers ("1a") fall back to the file name
                }
            }
            String manga = text(root, "Manga");
            String format = text(root, "Format");
            boolean rtl = manga != null && manga.trim().equalsIgnoreCase("YesAndRightToLeft");
            boolean webtoon = format != null && format.toLowerCase().contains("webtoon");
            return new Info(blankToNull(series), n, blankToNull(text(root, "LocalizedSeries")), rtl, webtoon);
        } catch (Exception e) {
            return null;
        }
    }

    private static String text(Element root, String tag) {
        NodeList nl = root.getElementsByTagName(tag);
        return nl.getLength() == 0 ? null : nl.item(0).getTextContent();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
