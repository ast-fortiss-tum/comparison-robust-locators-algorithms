package sidereal;

public class XPathInfo implements Comparable<XPathInfo>
{
    private String xpath;
    private double precision;
    private double fragility; // SIDEREAL addition

    public XPathInfo(String xpath, double precision) {
        this.xpath = xpath;
        this.precision = precision;
        this.fragility = 1.0; // default worst-case FC
    }

    public XPathInfo(String xpath, double precision, double fragility) {
        this.xpath = xpath;
        this.precision = precision;
        this.fragility = fragility;
    }

    public String getXpath() {
        return xpath;
    }

    public void setXpath(String xpath) {
        this.xpath = xpath;
    }

    public double getPrecision() {
        return precision;
    }

    public void setPrecision(double precision) {
        this.precision = precision;
    }

    public double getFragility() {
        return fragility;
    }

    public void setFragility(double fragility) {
        this.fragility = fragility;
    }

    @Override
    public int compareTo(XPathInfo other) {
        return Double.compare(this.fragility, other.fragility); // Lower FC is better
    }

    @Override
    public String toString() {
        return "XPathInfo{" +
                "xpath='" + xpath + '\'' +
                ", precision=" + precision +
                ", fragility=" + fragility +
                '}';
    }
}
