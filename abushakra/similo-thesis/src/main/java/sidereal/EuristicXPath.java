package sidereal;

public class EuristicXPath {
    public XPathInfo XPath;
    public int length;
    public int positional;
    public int attributeKind;

    public EuristicXPath() {}

    public EuristicXPath(XPathInfo xpathInfo) {
        this.XPath = xpathInfo;
    }

    @Override
    public String toString() {
        return "EuristicXPath{" +
                "XPath=" + XPath +
                ", length=" + length +
                ", positional=" + positional +
                ", attributeKind=" + attributeKind +
                '}';
    }
}