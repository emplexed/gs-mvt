package org.geoserver.wms.mvt;

/**
 * The Command enumaration needed by the VectorTile encoder
 *
 * Created by shennebe on 13.03.2015.
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
