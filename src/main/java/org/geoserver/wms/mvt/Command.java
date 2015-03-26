package org.geoserver.wms.mvt;

/**
 * The Command enumaration needed by the VectorTile encoder
 */
enum Command {

    MOVETO(1),
    LINETO(2),
    CLOSEPATH(7);

    private int value;

    Command(int value) {
        this.value = value;
    }

    public int getValue() {
        return this.value;
    }
}
