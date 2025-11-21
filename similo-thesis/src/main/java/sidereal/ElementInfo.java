package sidereal;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

public class ElementInfo {
    public String tagName = "";
    public ArrayList<String> attributes = new ArrayList<>();
    public LinkedHashSet<Set<String>> setAttributes = new LinkedHashSet<>();
    public int position = -1;
    public int positionStar = -1;
    public String text = "";

    // Optional: for tracking which features were added during XPath generation
    public ArrayList<String> usedFeatures = new ArrayList<>();

    public ElementInfo(String tagName, List<String> attributes, String text, int position) {
        this.tagName = tagName;
        this.attributes = new ArrayList<>(attributes);
        this.text = text;
        this.position = position;
    }

    @Override
    public String toString() {
        return "ElementInfo{" +
                "tag='" + tagName + '\'' +
                ", position=" + position +
                ", attributes=" + attributes +
                ", text='" + text + '\'' +
                '}';
    }
}