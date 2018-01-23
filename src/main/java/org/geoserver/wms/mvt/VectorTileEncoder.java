package org.geoserver.wms.mvt;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.vividsolutions.jts.algorithm.CGAlgorithms;
import org.geotools.geometry.jts.GeometryClipper;
import org.geotools.geometry.jts.JTS;
import org.geotools.util.logging.Logging;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryCollection;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.MultiPoint;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.geom.TopologyException;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKTReader;
import com.vividsolutions.jts.simplify.TopologyPreservingSimplifier;

import static org.geoserver.wms.mvt.MVTStreamingMapResponse.DEFAULT_SMALL_GEOMETRY_THRESHOLD;

/**
 * VectorTileEncoder adapted from https://github.com/ElectronicChartCentre/java-vector-tile/blob/master/src/main/java/no/ecc/vectortile/VectorTileEncoder.java
 *
 */
public class VectorTileEncoder {

    private final Map<String, Layer> layers = new LinkedHashMap<>();

    private final int extent;

    private final Geometry clipGeometry;

    /**
     * autoscale limits the coordinatesystem of the tiles to 256
     */
    private final boolean autoScale;

    private final double simplificationFactor;
    private final double smallGeometryThreshold;
    
    private static final Logger LOGGER = Logging.getLogger(VectorTileEncoder.class);

    /**
     * Creates the VectorTileEncoder for the defined target Bbox. It uses default values for the extent (4096) and
     * simplification factor (0.1d)
     *
     * @param targetBBox the target bbox
     */
    public VectorTileEncoder(Envelope targetBBox) {
        this(4096,targetBBox,0.1d, DEFAULT_SMALL_GEOMETRY_THRESHOLD);
    }

    /**
     * Create a {@link VectorTileEncoder} with the given extent value.
     * <p>
     * The extent value control how detailed the coordinates are encoded in the
     * vector tile. 4096 is a good default, 256 can be used to reduce density.
     * <p>
     * The polygon clip buffer value control how large the clipping area is
     * outside of the tile for polygons. 0 means that the clipping is done at
     * the tile border. 8 is a good default.
     *
     * @param extent  a int with extent value. 4096 is a good value.
     * @param targetBbox the bbox defined for the target tile
     * @param simplificationFactor the factor for simplification
     * @param smallGeometryThreshold defines the threshold in length / area when geometries should be skipped in output. 0 or negative means all geoms are included
     */
    public VectorTileEncoder(int extent, Envelope targetBbox, double simplificationFactor, double smallGeometryThreshold) {
        this.autoScale = true;
        this.extent = extent;
        this.clipGeometry = JTS.toGeometry(targetBbox);
        this.simplificationFactor = simplificationFactor;
        this.smallGeometryThreshold = smallGeometryThreshold;
    }

    /**
     * Create a {@link VectorTileEncoder} with the given extent value.
     * <p>
     * The extent value control how detailed the coordinates are encoded in the
     * vector tile. 4096 is a good default, 256 can be used to reduce density.
     * <p>
     * The polygon clip buffer value control how large the clipping area is
     * outside of the tile for polygons. 0 means that the clipping is done at
     * the tile border. 8 is a good default.
     *
     * @param extent
     *            a int with extent value. 4096 is a good value.
     * @param polygonClipBuffer
     *            a int with clip buffer size for polygons and line strings. 8 is a good value.
     * @param smallGeometryThreshold defines the threshold in length / area when geometries should be skipped in output. 0 or negative means all geoms are included
     */
    public VectorTileEncoder(int extent, int polygonClipBuffer, double simplificationFactor, double smallGeometryThreshold) {
        this(extent,createTileEnvelope(polygonClipBuffer,256),simplificationFactor, smallGeometryThreshold);
    }

    /**
     * Buffers the target envelope for clipping
     * @param buffer the buffer parameter
     * @return buffered result
     */
    private static Envelope createTileEnvelope(int buffer, int size) {
        Coordinate[] coords = new Coordinate[5];
        coords[0] = new Coordinate(0 - buffer, size + buffer);
        coords[1] = new Coordinate(size + buffer, size + buffer);
        coords[2] = new Coordinate(size + buffer, 0 - buffer);
        coords[3] = new Coordinate(0 - buffer, 0 - buffer);
        coords[4] = coords[0];
        return new GeometryFactory().createPolygon(coords).getEnvelopeInternal();
    }

    /**
     * Add a feature with layer name (typically feature type name), some
     * attributes and a Geometry. The Geometry must be in "pixel" space 0,0
     * lower left and 256,256 upper right.
     * <p>
     * For optimization, geometries will be clipped, geometries will simplified
     * and features with geometries outside of the tile will be skipped.
     *
     * @param layerName name of the layer to be added
     * @param attributes all attributes of the feature
     * @param geometry the target geometry
     */
    public void addFeature(String layerName, Map<String, ?> attributes, Geometry geometry) {

        // split up MultiPolygon and GeometryCollection (without subclasses)
        if (geometry instanceof MultiPolygon || geometry.getClass().equals(GeometryCollection.class)) {
            splitAndAddFeatures(layerName, attributes, (GeometryCollection) geometry);
            return;
        }

        // skip small Polygon/LineString.
        if(this.smallGeometryThreshold > 0) {
        	if (geometry instanceof Polygon && geometry.getArea() < this.smallGeometryThreshold) {        	
        		return;
        	}
        	if (geometry instanceof LineString && geometry.getLength() < this.smallGeometryThreshold) {
        		return;
        	}
        }

        // clip geometry
        if (geometry instanceof Point) {
            if (!clipCovers(geometry)) {
                return;
            }
        } else {
            geometry = clipGeometry(geometry);
            if (geometry == null) {
                return;
            }
        }

        // if clipping result in MultiPolygon, then split once more
        if (geometry instanceof MultiPolygon || geometry.getClass().equals(GeometryCollection.class)) {
            splitAndAddFeatures(layerName, attributes, (GeometryCollection) geometry);
            return;
        }

        // no need to add empty geometry
        if (geometry.isEmpty() || geometry.getCoordinates() == null || geometry.getCoordinates().length == 0) {
            return;
        }

        //generalize geometry (less memory) use TopolyPreservingSimplifier to prevent null geometries
        if(this.simplificationFactor > 0) {
            try {
                geometry = TopologyPreservingSimplifier.simplify(geometry, this.simplificationFactor);
            } catch (Exception e) {
                LOGGER.warning("Geometry cannot be simplified!! " + geometry.toString());
                if (geometry instanceof LineString) {
                    List<Coordinate> coordinates = new ArrayList<>();
                    for (Coordinate coordinate : geometry.getCoordinates()) {
                        if (coordinate.x > 0 && coordinate.y > 0) {
                            coordinates.add(coordinate);
                        }
                    }
                    GeometryFactory gm = new GeometryFactory();
                    geometry = gm.createLineString(coordinates.toArray(new Coordinate[coordinates.size()]));
                }
            }
            if (geometry instanceof Polygon) {
                // Known bug in TopologyPreservingSimplifier
                // (see https://locationtech.github.io/jts/javadoc/org/locationtech/jts/simplify/TopologyPreservingSimplifier.html)
                if (!geometry.isValid()) {
                    geometry = checkPolygonRingsIntersecting((Polygon) geometry);
                }
            }
        }

        for (Coordinate coordinate : geometry.getCoordinates()) {
            if (coordinate.x == 0 && coordinate.y == 0) {
                LOGGER.warning("0 Value detected in Geometry " + geometry.toString());
            }
        }

        Layer layer = layers.get(layerName);
        if (layer == null) {
            layer = new Layer();
            layers.put(layerName, layer);
        }

        Feature feature = new Feature();
        feature.geometry = geometry;

        for (Map.Entry<String, ?> e : attributes.entrySet()) {
            // skip attribute without value
            if (e.getValue() == null) {
                continue;
            }
            feature.tags.add(layer.key(e.getKey()));
            feature.tags.add(layer.value(e.getValue()));
        }

        layer.features.add(feature);
    }

    /**
     * A short circuit clip to the tile extent (tile boundary + buffer) for
     * points to improve performance. This method can be overridden to change
     * clipping behavior. See also {@link #clipGeometry(Geometry)}.
     *
     * {@see https://github.com/ElectronicChartCentre/java-vector-tile/issues/13}
     */
    protected boolean clipCovers(Geometry geom) {
        if (geom instanceof Point) {
            Point p = (Point) geom;
            return clipGeometry.getEnvelopeInternal().covers(p.getCoordinate());
        }
        return clipGeometry.covers(geom);
    }

    /**
     * Clip geometry according to buffer given at construct time. This method
     * can be overridden to change clipping behavior. See also
     * {@link #clipCovers(Geometry)}.
     *
     * @param geometry the geometry to be clipped
     * @return clipped geometry
     */
    protected Geometry clipGeometry(Geometry geometry) {
        final GeometryClipper clipper = new GeometryClipper(clipGeometry.getEnvelopeInternal());
        geometry = clipper.clipSafe(geometry, false, 0);
        return geometry;
    }

    private void splitAndAddFeatures(String layerName, Map<String, ?> attributes, GeometryCollection geometry) {
        for (int i = 0; i < geometry.getNumGeometries(); i++) {
            Geometry subGeometry = geometry.getGeometryN(i);
            addFeature(layerName, attributes, subGeometry);
        }
    }

    /**
     * Method that checks if the exterior and interior rings are intersecting. In this case the intersecting interior
     * rings are skipped to retrieve a valid geometry again.
     *
     * @param polygon the invalid polygon to be handeld
     * @return the geometry with intersecting rings removed
     */
    private Geometry checkPolygonRingsIntersecting(Polygon polygon) {
        //Check if there are intersecting rings in this case skip the interior rings which are intersecting
        final LineString exteriorRing = polygon.getExteriorRing();
        final List<LineString> interiorRings = new ArrayList<>();
        final List<LineString> intersectingRings = new ArrayList<>();
        for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
            LineString interiorRing = polygon.getInteriorRingN(i);
            if (!exteriorRing.intersects(interiorRing)) {
                interiorRings.add(interiorRing);
            } else {
                LOGGER.info("Polygon intersecting interior ring with exterior ring after simplifying");
            }
        }
        for (int i = 0; i < interiorRings.size(); i++) {
            LineString interiorRing = interiorRings.get(i);
            for (int j = i + 1; j < interiorRings.size() - 1; j++) {
                LineString toCompare = interiorRings.get(j);
                if (toCompare.intersects(interiorRing)) {
                    LOGGER.info("Polygon intersecting interior ring with interior ring after simplifying");
                    intersectingRings.add(interiorRing);
                    break;
                }
            }
        }
        GeometryFactory gm = new GeometryFactory();
        LinearRing[] interiorRingArray = new LinearRing[interiorRings.size() - intersectingRings.size()];
        int i = 0;
        for (LineString lineString : interiorRings) {
            if (!intersectingRings.contains(lineString)) {
                interiorRingArray[i++] = gm.createLinearRing(lineString.getCoordinateSequence());
            }
        }
        return gm.createPolygon(gm.createLinearRing(exteriorRing.getCoordinateSequence()),interiorRingArray);
    }

    /**
     * @return a byte array with the vector tile
     */
    public byte[] encode() {
        byte[] result = new byte[]{};
        try {
            result = this.retrieveVectorTile().toByteArray();
        } catch (NoClassDefFoundError cex) {
            LOGGER.log(Level.SEVERE,"Google Protocol Buffers Library not found in Classpath");
        }
        return result;
    }

    /**
     * Writes the result to the {@link OutputStream} given as argument
     *
     * @param outputStream the stream to write the result to
     * @throws IOException
     */
    public void encode(OutputStream outputStream) throws IOException {
        try {
            this.retrieveVectorTile().writeTo(outputStream);
        } catch (NoClassDefFoundError cex) {
            LOGGER.log(Level.SEVERE,"Google Protocol Buffers Library not found in Classpath");
        }
    }

    private VectorTile.Tile retrieveVectorTile() {
        VectorTile.Tile.Builder tileBuilder = VectorTile.Tile.newBuilder();

        for (Map.Entry<String, Layer> e : layers.entrySet()) {
            String layerName = e.getKey();
            Layer layer = e.getValue();

            VectorTile.Tile.Layer.Builder layerBuilder = VectorTile.Tile.Layer.newBuilder();
            layerBuilder.setVersion(2);
            layerBuilder.setName(layerName);

            layerBuilder.addAllKeys(layer.keys());

            for (Object value : layer.values()) {
                VectorTile.Tile.Value.Builder valueBuilder = VectorTile.Tile.Value.newBuilder();
                if (value instanceof String) {
                    valueBuilder.setStringValue((String) value);
                } else if (value instanceof Integer) {
                    valueBuilder.setIntValue((Integer) value);
                } else if (value instanceof Long) {
                    valueBuilder.setSintValue((Long) value);
                } else if (value instanceof Float) {
                    valueBuilder.setFloatValue((Float) value);
                } else if (value instanceof Double) {
                    valueBuilder.setDoubleValue((Double) value);
                } else if (value instanceof Boolean) {
                    valueBuilder.setBoolValue((Boolean)value);
                }
                else {
                    valueBuilder.setStringValue(value.toString());
                }
                layerBuilder.addValues(valueBuilder.build());
            }

            layerBuilder.setExtent(extent);

            for (Feature feature : layer.features) {

                Geometry geometry = feature.geometry;

                VectorTile.Tile.Feature.Builder featureBuilder = VectorTile.Tile.Feature.newBuilder();

                featureBuilder.addAllTags(feature.tags);
                featureBuilder.setType(toGeomType(geometry));
                featureBuilder.addAllGeometry(commands(geometry));

                layerBuilder.addFeatures(featureBuilder.build());
            }

            tileBuilder.addLayers(layerBuilder.build());

        }
        return tileBuilder.build();
    }

    static VectorTile.Tile.GeomType toGeomType(Geometry geometry) {
        if (geometry instanceof Point) {
            return VectorTile.Tile.GeomType.POINT;
        }
        if (geometry instanceof MultiPoint) {
            return VectorTile.Tile.GeomType.POINT;
        }
        if (geometry instanceof LineString) {
            return VectorTile.Tile.GeomType.LINESTRING;
        }
        if (geometry instanceof MultiLineString) {
            return VectorTile.Tile.GeomType.LINESTRING;
        }
        if (geometry instanceof Polygon) {
            return VectorTile.Tile.GeomType.POLYGON;
        }
        return VectorTile.Tile.GeomType.UNKNOWN;
    }

    static boolean shouldClosePath(Geometry geometry) {
        return (geometry instanceof Polygon) || (geometry instanceof LinearRing);
    }

    private int x = 0;
    private int y = 0;

    List<Integer> commands(Geometry geometry) {

        x = 0;
        y = 0;

        if (geometry instanceof Polygon) {
            Polygon polygon = (Polygon) geometry;
            List<Integer> commands = new ArrayList<Integer>();

            // According to the vector tile specification, the exterior ring of a polygon
            // must be in clockwise order, while the interior ring in counter-clockwise order.
            // In the tile coordinate system, Y axis is positive down.
            //
            // However, in geographic coordinate system, Y axis is positive up.
            // Therefore, we must reverse the coordinates.
            // So, the code below will make sure that exterior ring is in counter-clockwise order
            // and interior ring in clockwise order.
            LineString exteriorRing = polygon.getExteriorRing();
            if (!CGAlgorithms.isCCW(exteriorRing.getCoordinates())) {
                exteriorRing = (LineString) exteriorRing.reverse();
            }
            commands.addAll(commands(exteriorRing.getCoordinates(), true));

            for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
                LineString interiorRing = polygon.getInteriorRingN(i);
                if (CGAlgorithms.isCCW(interiorRing.getCoordinates())) {
                    interiorRing = (LineString) interiorRing.reverse();
                }
                commands.addAll(commands(interiorRing.getCoordinates(), true));
            }
            return commands;
        }

        if (geometry instanceof MultiLineString) {
            List<Integer> commands = new ArrayList<>();
            GeometryCollection gc = (GeometryCollection) geometry;
            for (int i = 0; i < gc.getNumGeometries(); i++) {
                commands.addAll(commands(gc.getGeometryN(i).getCoordinates(), false));
            }
            return commands;
        }

        return commands(geometry.getCoordinates(), shouldClosePath(geometry), geometry instanceof MultiPoint);
    }

    /**
     * // // // Ex.: MoveTo(3, 6), LineTo(8, 12), LineTo(20, 34), ClosePath //
     * Encoded as: [ 9 3 6 18 5 6 12 22 15 ] // == command type 7 (ClosePath),
     * length 1 // ===== relative LineTo(+12, +22) == LineTo(20, 34) // ===
     * relative LineTo(+5, +6) == LineTo(8, 12) // == [00010 010] = command type
     * 2 (LineTo), length 2 // === relative MoveTo(+3, +6) // == [00001 001] =
     * command type 1 (MoveTo), length 1 // Commands are encoded as uint32
     * varints, vertex parameters are // encoded as sint32 varints (zigzag).
     * Vertex parameters are // also encoded as deltas to the previous position.
     * The original // position is (0,0)
     *
     * @param cs
     * @return
     */
    List<Integer> commands(Coordinate[] cs, boolean closePathAtEnd) {
        return commands(cs, closePathAtEnd, false);
    }

    List<Integer> commands(Coordinate[] cs, boolean closePathAtEnd, boolean multiPoint) {

        if (cs.length == 0) {
            throw new IllegalArgumentException("empty geometry");
        }

        List<Integer> r = new ArrayList<Integer>();

        int lineToIndex = 0;
        int lineToLength = 0;

        double scale = autoScale ? (extent / 256.0) : 1.0;

        for (int i = 0; i < cs.length; i++) {
            Coordinate c = cs[i];

            if (i == 0) {
                r.add(commandAndLength(Command.MoveTo, multiPoint ? cs.length : 1));
            }

            int _x = (int) Math.round(c.x * scale);
            int _y = (int) Math.round(c.y * scale);

            // prevent point equal to the previous
            if (i > 0 && _x == x && _y == y) {
                lineToLength--;
                continue;
            }

            // prevent double closing
            if (closePathAtEnd && cs.length > 1 && i == (cs.length - 1) && cs[0].equals(c)) {
                lineToLength--;
                continue;
            }

            // delta, then zigzag
            r.add(zigZagEncode(_x - x));
            r.add(zigZagEncode(_y - y));

            x = _x;
            y = _y;

            if (i == 0 && cs.length > 1 && !multiPoint) {
                // can length be too long?
                lineToIndex = r.size();
                lineToLength = cs.length - 1;
                r.add(commandAndLength(Command.LineTo, lineToLength));
            }

        }

        // update LineTo length
        if (lineToIndex > 0) {
            if (lineToLength == 0) {
                // remove empty LineTo
                r.remove(lineToIndex);
            } else {
                // update LineTo with new length
                r.set(lineToIndex, commandAndLength(Command.LineTo, lineToLength));
            }
        }

        if (closePathAtEnd) {
            r.add(commandAndLength(Command.ClosePath, 1));
        }

        return r;
    }

    static int commandAndLength(int command, int repeat) {
        return repeat << 3 | command;
    }

    static int zigZagEncode(int n) {
        // https://developers.google.com/protocol-buffers/docs/encoding#types
        return (n << 1) ^ (n >> 31);
    }

    private static final class Layer {

        final List<Feature> features = new ArrayList<>();

        private final Map<String, Integer> keys = new LinkedHashMap<>();
        private final Map<Object, Integer> values = new LinkedHashMap<>();

        public Integer key(String key) {
            Integer i = keys.get(key);
            if (i == null) {
                i = keys.size();
                keys.put(key, i);
            }
            return i;
        }

        public List<String> keys() {
            return Collections.unmodifiableList(new ArrayList<>(keys.keySet()));
        }

        public Integer value(Object value) {
            Integer i = values.get(value);
            if (i == null) {
                i = values.size();
                values.put(value, i);
            }
            return i;
        }

        public List<Object> values() {
            return Collections.unmodifiableList(new ArrayList<Object>(values.keySet()));
        }
    }

    private static final class Feature {

        Geometry geometry;
        final List<Integer> tags = new ArrayList<>();

    }
}
