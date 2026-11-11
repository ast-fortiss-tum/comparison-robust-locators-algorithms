package robulanew;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

public class ElementInfo
{
    String		     tagName       = new String();
    ArrayList<String>	  attributes    = new ArrayList<String>();
    LinkedHashSet<Set<String>> setAttributes = new LinkedHashSet<Set<String>>();
    int			position      = -1;
    int			positionStar  = -1;
    String		     text	  = new String();
}
