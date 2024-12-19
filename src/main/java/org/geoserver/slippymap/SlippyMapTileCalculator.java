package org.geoserver.slippymap;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.geotools.referencing.datum.DefaultEllipsoid;
import org.geotools.util.logging.Logging;

/**
 * Tile calculator that converts between slippy map tiles and coordiante bounds in EPSG 4326 format.
 */
public class SlippyMapTileCalculator {

    private static CoordinateReferenceSystem CRS_3857;

    private static CoordinateReferenceSystem CRS_4326;

    private static final Logger LOGGER = Logging.getLogger(SlippyMapTileCalculator.class);

    static {
        try {
            CRS_3857 = CRS.decode("EPSG:3857");
            CRS_4326 = CRS.decode("EPSG:4326");
        } catch (FactoryException e) {
            LOGGER.log(Level.WARNING, "CRS could not be created", e);
        }
    }

    /**
     * Return the boundingbox in degree (EPSG:4326) for the requested Slippy Map Tile in the zoom
     * level specified by the argument.
     *
     * @param x the Slippy Map xTile
     * @param y the Slippy Map yTile
     * @param zoom the Slippy Map zoom Level
     * @param srid the reference system of the resulting bbox. Either 4326 (expensive), 900913 or
     *     3857 (less expensive)
     * @return a boundingbox containing the min and max lat / lon values in EPSG:4326
     */
    public static ReferencedEnvelope tile2boundingBox(
            final int x, final int y, final int zoom, int srid) {
        ReferencedEnvelope referencedEnvelope;
        switch (srid) {
            case 4326:
                referencedEnvelope =
                        new ReferencedEnvelope(
                                tile2lon(x, zoom),
                                tile2lon(x + 1, zoom),
                                tile2lat(y + 1, zoom),
                                tile2lat(y, zoom),
                                CRS_4326);
                break;
            case 3857:
            case 900913:
                referencedEnvelope =
                        new ReferencedEnvelope(
                                tile2xMercator(x, zoom),
                                tile2xMercator(x + 1, zoom),
                                tile2yMercator(y + 1, zoom),
                                tile2yMercator(y, zoom),
                                CRS_3857);
                break;
            default:
                throw new IllegalArgumentException(
                        srid + " is not allowed please use 4326, 900913 or 3857");
        }
        return referencedEnvelope;
    }

    /**
     * Returns the longitude for an x tile
     *
     * @param x the Slippy Map xTile
     * @param z the Slippy Map zoom Level
     * @return a longitude in decimal degree
     */
    public static double tile2lon(int x, int z) {
        return x / Math.pow(2.0, z) * 360.0 - 180;
    }

    /**
     * Returns the x value for the google mercator projection (EPSG:3857)
     *
     * @param x the Slippy Map xTile
     * @param z the Slippy Map zoom Level
     * @return a x coordinate in google mercator
     */
    public static double tile2xMercator(int x, int z) {
        double n = Math.pow(2.0, z);
        return DefaultEllipsoid.WGS84.getSemiMajorAxis() * Math.PI * (2 * x / n - 1);
    }

    /**
     * Returns the longitude for an y tile
     *
     * @param y the Slippy Map yTile
     * @param z the Slippy Map zoom Level
     * @return a latitude in decimal degree
     */
    public static double tile2lat(int y, int z) {
        double n = Math.PI - (2.0 * Math.PI * y) / Math.pow(2.0, z);
        return Math.toDegrees(Math.atan(Math.sinh(n)));
    }

    /**
     * Returns the y value in mercator projection (EPSG:3857)
     *
     * @param y the Slippy Map y tile
     * @param z the Slippy Map zzom Level
     * @return y coordinate in google mercator
     */
    public static double tile2yMercator(int y, int z) {
        double n = Math.pow(2.0, z);
        return DefaultEllipsoid.WGS84.getSemiMajorAxis() * Math.PI * (1 - (2 * y / n));
    }

    /**
     * Returns the Slippy Map xTile from a position given in lon / lat (EPSG:4326)
     *
     * @param lat the latitude
     * @param lon the longitude
     * @param zoom the zoomlevel which should be considered
     * @return the xTile value
     */
    public static int getXTile(final double lat, final double lon, final int zoom) {
        return (int) Math.floor((lon + 180) / 360 * (1 << zoom));
    }

    /**
     * Returns the Slippy Map yTile from a position given in lon / lat (EPSG:4326)
     *
     * @param lat the latitude
     * @param lon the longitude
     * @param zoom the zoomlevel which should be considered
     * @return the yTile value
     */
    public static int getYTile(final double lat, final double lon, final int zoom) {
        return (int)
                Math.floor(
                        (1
                                        - Math.log(
                                                        Math.tan(Math.toRadians(lat))
                                                                + 1 / Math.cos(Math.toRadians(lat)))
                                                / Math.PI)
                                / 2
                                * (1 << zoom));
    }
}
