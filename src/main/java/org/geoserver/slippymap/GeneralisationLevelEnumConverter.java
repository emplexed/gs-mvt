package org.geoserver.slippymap;

import java.beans.PropertyEditorSupport;
import org.geoserver.wms.GeneralisationLevel;

public class GeneralisationLevelEnumConverter extends PropertyEditorSupport {

    @Override
    public void setAsText(String text) throws IllegalArgumentException {
        String capitalized = text.toUpperCase();
        GeneralisationLevel generalisationLevel = GeneralisationLevel.valueOf(capitalized);
        setValue(generalisationLevel);
    }
}
