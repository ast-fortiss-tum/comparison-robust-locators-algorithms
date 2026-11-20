package robulanew;

public class XPathInfo implements Comparable<XPathInfo>
{

    private String xpath;
    private double precision;

    public XPathInfo(String xpath, Double precision) {
        this.xpath = xpath;
        this.precision = precision;
    }

    public String getXpath()
    {
        return xpath;
    }

    public void setXpath(String xpath)
    {
        this.xpath = xpath;
    }

    public Double getPrecision()
    {
        return precision;
    }

    public void setPrecision(Double precision)
    {
        this.precision = precision;
    }

    public int compareTo(XPathInfo o)
    {
        if (this.getPrecision() >= o.getPrecision())
            return 0;
        return 1;
    }
}