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
        this(extent,createTileEnvelope(polygonClipBuffer),simplificationFactor, smallGeometryThreshold);
    }

    /**
     * Buffers the target envelope for clipping
     * @param buffer the buffer parameter
     * @return buffered result
     */
    private static Envelope createTileEnvelope(int buffer) {
        Coordinate[] coords = new Coordinate[5];
        coords[0] = new Coordinate(0 - buffer, 256 + buffer);
        coords[1] = new Coordinate(256 + buffer, 256 + buffer);
        coords[2] = new Coordinate(256 + buffer, 0);
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

        // clip geometry. polygons or linestrings right outside. other geometries at tile
        // border.
        // clip geometry. polygons right outside. other geometries at tile
        // border.
        try {
            if (geometry instanceof Polygon) {
                Geometry original = geometry;
                geometry = clipGeometry.intersection(original);

                // some times a intersection is returned as an empty geometry.
                // going via wkt fixes the problem.
                if (geometry.isEmpty() && original.intersects(clipGeometry)) {
                    Geometry originalViaWkt = new WKTReader().read(original.toText());
                    geometry = clipGeometry.intersection(originalViaWkt);
                }

            } else if (geometry instanceof LineString) {
                geometry = clipGeometry.intersection(geometry);
            }
        } catch (TopologyException | ParseException e) {
            // could not intersect. original geometry will be used instead.
        }

        // if clipping result in MultiPolygon, then split once more
        if (geometry instanceof MultiPolygon) {
            splitAndAddFeatures(layerName, attributes, (GeometryCollection) geometry);
            return;
        }

        // no need to add empty geometry
        if (geometry.isEmpty() || geometry.getCoordinates() == null || geometry.getCoordinates().length == 0) {
            return;
        }

        //generalize geometry (less memory) use TopolyPreservingSimplifier to prevent null geometries
        if(this.simplificationFactor > 0) {
        	geometry = TopologyPreservingSimplifier.simplify(geometry, this.simplificationFactor);
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

    private void splitAndAddFeatures(String layerName, Map<String, ?> attributes, GeometryCollection geometry) {
        for (int i = 0; i < geometry.getNumGeometries(); i++) {
            Geometry subGeometry = geometry.getGeometryN(i);
            addFeature(layerName, attributes, subGeometry);
        }
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
            layerBuilder.setVersion(1);
            layerBuilder.setName(layerName);

            layerBuilder.addAllKeys(layer.keys());

            for (Object value : layer.values()) {
                VectorTile.Tile.Value.Builder valueBuilder = VectorTile.Tile.Value.newBuilder();
                if (value instanceof String) {
                    valueBuilder.setStringValue((String) value);
                } else if (value instanceof Integer) {
                    valueBuilder.setSintValue((Integer) value);
                } else if (value instanceof Long) {
                    valueBuilder.setSintValue((Long) value);
                } else if (value instanceof Float) {
                    valueBuilder.setFloatValue((Float) value);
                } else if (value instanceof Double) {
                    valueBuilder.setDoubleValue((Double) value);
                } else {
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

    List<Integer> commands(Geometry geometry) {

        Coordinate relativeCoordinate = new Coordinate();

        if (geometry instanceof Polygon) {
            Polygon polygon = (Polygon) geometry;
            if (polygon.getNumInteriorRing() > 0) {
                List<Integer> commands = new ArrayList<>();
                commands.addAll(commands(polygon.getExteriorRing().getCoordinates(),relativeCoordinate,true));
                for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
                    commands.addAll(commands(polygon.getInteriorRingN(i).getCoordinates(),relativeCoordinate,true));
                }
                return commands;
            }
        }

        if (geometry instanceof MultiLineString) {
            List<Integer> commands = new ArrayList<>();
            GeometryCollection gc = (GeometryCollection) geometry;
            for (int i = 0; i < gc.getNumGeometries(); i++) {
                commands.addAll(commands(gc.getGeometryN(i).getCoordinates(),relativeCoordinate,false));
            }
            return commands;
        }

        return commands(geometry.getCoordinates(),relativeCoordinate,shouldClosePath(geometry), geometry instanceof MultiPoint);
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
     * @param cs an array of coordinates
     * @return list of integer commands
     */
    List<Integer> commands(Coordinate[] cs, Coordinate relativeCoordinate, boolean closePathAtEnd) {
        return commands(cs, relativeCoordinate, closePathAtEnd, false);
    }

    List<Integer> commands(Coordinate[] cs, Coordinate relativeCoordinate, boolean closePathAtEnd, boolean multiPoint) {

        if (cs.length == 0) {
            throw new IllegalArgumentException("empty geometry");
        }

        List<Integer> r = new ArrayList<>();

        int lineToIndex = 0;
        int lineToLength = 0;

        double scale = extent / 256.0;

        for (int i = 0; i < cs.length; i++) {
            Coordinate c = cs[i];

            if (i == 0) {
                r.add(commandAndLength(Command.MOVETO.getValue(), multiPoint ? cs.length : 1));
            }

            int _x = (int) Math.round(c.x * scale);
            int _y = (int) Math.round(c.y * scale);

            // prevent point equal to the previous
            if (i > 0 && _x == relativeCoordinate.x && _y == relativeCoordinate.y) {
                lineToLength--;
                continue;
            }

            // prevent double closing
            if (closePathAtEnd && cs.length > 1 && i == (cs.length - 1) && cs[0].equals(c)) {
                lineToLength--;
                continue;
            }

            // delta, then zigzag
            r.add(zigZagEncode(_x - (int) relativeCoordinate.x));
            r.add(zigZagEncode(_y - (int) relativeCoordinate.y));

            relativeCoordinate.x = _x;
            relativeCoordinate.y = _y;

            if (i == 0 && cs.length > 1 && !multiPoint) {
                // can length be too long?
                lineToIndex = r.size();
                lineToLength = cs.length - 1;
                r.add(commandAndLength(Command.LINETO.getValue(), lineToLength));
            }

        }

        // update LineTo length
        if (lineToIndex > 0) {
            if (lineToLength == 0) {
                //remove empty LineTo
                r.remove(lineToIndex);
            } else {
                //update LineTo with new length
                r.set(lineToIndex, commandAndLength(Command.LINETO.getValue(), lineToLength));
            }
        }

        if (closePathAtEnd) {
            r.add(commandAndLength(Command.CLOSEPATH.getValue(), 1));
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
            return Collections.unmodifiableList(new ArrayList<>(values.keySet()));
        }
    }

    private static final class Feature {

        Geometry geometry;
        final List<Integer> tags = new ArrayList<>();

    }
}
