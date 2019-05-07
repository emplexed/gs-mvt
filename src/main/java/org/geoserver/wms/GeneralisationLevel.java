package org.geoserver.wms;

public enum GeneralisationLevel {
    LOW("low"),
    MID("mid"),
    HIGH("high");

    private String value;

    GeneralisationLevel(String value) {
        this.value = value;
    }

    public String getValue() {
        return this.value;
    }
}
