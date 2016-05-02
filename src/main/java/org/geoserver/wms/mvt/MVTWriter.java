package org.geoserver.wms.mvt;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.geotools.styling.AbstractSymbolizer;
import org.geotools.styling.Style;
import org.geotools.util.logging.Logging;
import org.opengis.feature.Feature;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.filter.Filter;
import org.opengis.filter.expression.Expression;
import org.opengis.filter.spatial.BBOX;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;
import org.opengis.style.FeatureTypeStyle;
import org.opengis.style.Rule;
import org.opengis.style.Symbolizer;

import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Class is not stateless. Writer transforms the geometries into the local tile coordinate System. For each tile
 * an own MVTWriter instance is needed.
 */
public class MVTWriter {

    static CoordinateReferenceSystem TARGET_CRS;

    private static final Logger LOGGER = Logging.getLogger(MVTWriter.class);

    static {
        try {
            TARGET_CRS = CRS.decode("EPSG:3857");
        } catch (FactoryException e) {
            LOGGER.log(Level.WARNING,"CRS could not be created",e);
        }
    }

    /**
     * VectorTileEncoder for encoding {@link FeatureCollection} to MVT PBF
     */
    private VectorTileEncoder vectorTileEncoder;

    /**
     * The requested bounding box
     */
    private final Envelope sourceBBOX;

    /**
     * The bounding box of the target (tile local) system
     */
    private final Envelope targetBBOX;

    /**
     * scale in x direction
     */
    private final double xScale;

    /**
     * scale in y direction
     */
    private final double yScale;

    /**
     * Retrieves an instance of the MVTWriter.
     *
     * @param sourceBBOX the bbox envelope requested in World coordinates
     * @param sourceCRS the rereference system of the source bbox
     * @param tileSizeX the tile size of the resulting vector tile in x direction (without buffer)
     * @param tileSizeY the tile size of the resulting vector tile in y direction (without buffer)
     * @param buffer the buffer of the vector tiles
     * @param genFactor generalisation factor, value used as parameter in simplification algorithm
     * @param skipSmallGeoms defines if small geometries (short linestrings or polys with small area) should be skipped in output
     * @return an MVTWriter instance for further processing
     *
     * @throws FactoryException in case of the SRS is unknown
     * @throws TransformException in case of the transformation failed
     */
    public static MVTWriter getInstance(Envelope sourceBBOX, CoordinateReferenceSystem sourceCRS, 
    		int tileSizeX, int tileSizeY, int buffer, double genFactor, boolean skipSmallGeoms) throws FactoryException, TransformException {
        Envelope targetBBOX = new ReferencedEnvelope(0,tileSizeX,0,tileSizeY,TARGET_CRS);
        return new MVTWriter(sourceBBOX,targetBBOX,sourceCRS,buffer,genFactor, skipSmallGeoms);
    }
    
    /**
     * Retrieves an instance of the MVTWriter.
     *
     * @param sourceBBOX the bbox envelope requested in World coordinates
     * @param sourceCRS the rereference system of the source bbox
     * @param tileSizeX the tile size of the resulting vector tile in x direction (without buffer)
     * @param tileSizeY the tile size of the resulting vector tile in y direction (without buffer)
     * @param buffer the buffer of the vector tiles
     * @return an MVTWriter instance for further processing
     *
     * @throws FactoryException in case of the SRS is unknown
     * @throws TransformException in case of the transformation failed
     */
    public static MVTWriter getInstance(Envelope sourceBBOX, CoordinateReferenceSystem sourceCRS, int tileSizeX, int tileSizeY, int buffer) throws FactoryException, TransformException {
        Envelope targetBBOX = new ReferencedEnvelope(0,tileSizeX,0,tileSizeY,TARGET_CRS);
        return new MVTWriter(sourceBBOX,targetBBOX,sourceCRS,buffer);
    }

    /**
     * Retrieves an instance of the MVTWriter. The tileSize is assumes as identical in x and y. A buffer of 0 is requested.
     *
     * @param sourceBBOX the bbox envelope requested in World coordinates
     * @param sourceCRS the rereference system of the source bbox
     * @param tileSize the tile size of the resulting vector tile in x and y direction
     * @return an MVTWriter instance for further processing
     *
     * @throws FactoryException in case of the SRS is unknown
     * @throws TransformException in case of the transformation failed
     */
    public static MVTWriter getInstance(Envelope sourceBBOX, CoordinateReferenceSystem sourceCRS, int tileSize) throws FactoryException, TransformException {
        return MVTWriter.getInstance(sourceBBOX, sourceCRS, tileSize, tileSize, 0);
    }

    /**
     * Retrieves an instance of the MVTWriter. The tileSize is assumed as 256 units in x and y direction. A buffer of 0 is requested.
     *
     * @param sourceBBOX the bbox envelope requested in World coordinates
     * @param sourceCRS the rereference system of the source bbox
     * @return an MVTWriter instance for further processing
     *
     * @throws FactoryException
     * @throws TransformException
     */
    public static MVTWriter getInstance(Envelope sourceBBOX, CoordinateReferenceSystem sourceCRS) throws FactoryException, TransformException {
        return MVTWriter.getInstance(sourceBBOX, sourceCRS, 256);
    }

    /**
     * Retrieves an instance of the MVTWriter. The tileSize is assumed as 256 units in x and y direction. A buffer of 0 is requested.
     *
     * @param bbox the bbox envelope requested in World coordinates
     * @param sourceCRS the rereference system of the source bbox
     * @return an MVTWriter instance for further processing
     *
     * @throws FactoryException
     * @throws TransformException
     */
    public static MVTWriter getInstance(BBOX bbox, CoordinateReferenceSystem sourceCRS)
            throws TransformException, FactoryException {
        Envelope sourceBBOX = JTS.toGeometry(bbox.getBounds()).getEnvelopeInternal();
        return MVTWriter.getInstance(sourceBBOX, sourceCRS, 256);
    }

    /**
     * Returns the buffered bounding box. This is needed for the request to the datasource to get all features
     * that are within the buffered bounds.
     * @return a referenced envelope containig the bounds with buffer (if existent)
     *
     * @throws TransformException
     * @throws FactoryException
     */
    public ReferencedEnvelope getSourceBBOXWithBuffer() throws TransformException, FactoryException {
        return new ReferencedEnvelope(this.sourceBBOX,TARGET_CRS);
    }

    private MVTWriter(Envelope sourceBBOX,
            Envelope targetBBOX,
            CoordinateReferenceSystem sourceCRS,
            int bufferSize, double genFactor, boolean skipSmallGeoms) throws TransformException, FactoryException {
		this(sourceBBOX, targetBBOX, sourceCRS, bufferSize);
		this.vectorTileEncoder = new VectorTileEncoder(4096, targetBBOX, genFactor, skipSmallGeoms);
	}

    
    private MVTWriter(Envelope sourceBBOX,
                      Envelope targetBBOX,
                      CoordinateReferenceSystem sourceCRS,
                      int bufferSize) throws TransformException, FactoryException {
        MathTransform transform = CRS.findMathTransform(sourceCRS, TARGET_CRS);
        sourceBBOX = JTS.transform(sourceBBOX, transform);
        if (bufferSize > 0) {
            sourceBBOX = this.getBufferedSourceBBOX(bufferSize,sourceBBOX,targetBBOX, TARGET_CRS);
            targetBBOX = new ReferencedEnvelope(targetBBOX.getMinX() - bufferSize, targetBBOX.getMaxX() + bufferSize,
                    targetBBOX.getMinY() - bufferSize, targetBBOX.getMaxY() + bufferSize,TARGET_CRS);
        }
        this.sourceBBOX = sourceBBOX;
        this.targetBBOX = targetBBOX;
        this.xScale = this.calculateXFactor();
        this.yScale = this.calculateYFactor();
        this.vectorTileEncoder = new VectorTileEncoder(targetBBOX);
    }

    /**
     * Buffers the sourceBBOX in the same relation like the buffer has to the targetBBOX.
     *
     * @param bufferSize the size of the buffer in target units
     * @param sourceBBOX the sourceBBOX to be buffered
     * @param targetBBOX the unbuffered targetBBOX
     * @param targetCRS the CRS of the targetBBOX (should be a equidistant reference system)
     *
     * @return the buffered sourceBBOX
     * @throws TransformException
     */
    private ReferencedEnvelope getBufferedSourceBBOX(int bufferSize,Envelope sourceBBOX, Envelope targetBBOX, CoordinateReferenceSystem targetCRS) throws TransformException {
        if (bufferSize > 0) {
            double xBuffer = sourceBBOX.getWidth() * bufferSize / targetBBOX.getWidth();
            double yBuffer = sourceBBOX.getHeight() * bufferSize / targetBBOX.getHeight();
            return new ReferencedEnvelope(sourceBBOX.getMinX()-xBuffer,sourceBBOX.getMaxX()+xBuffer,sourceBBOX.getMinY()-yBuffer,sourceBBOX.getMaxY()+yBuffer,targetCRS);
        }
        return null;
    }

    /**
     * Calculates the scale in X direction
     *
     * @return the x scale
     */
    private double calculateXFactor() {
        double deltaSource = sourceBBOX.getMaxX() - sourceBBOX.getMinX();
        double deltaTarget = targetBBOX.getMaxX() - targetBBOX.getMinX();
        return deltaTarget / deltaSource;
    }

    /**
     * Calculates the scale in Y direction
     *
     * @return the y scale
     */
    private double calculateYFactor() {
        double deltaSource = sourceBBOX.getMaxY() - sourceBBOX.getMinY();
        double deltaTarget = targetBBOX.getMaxY() - targetBBOX.getMinY();
        return deltaTarget / deltaSource;
    }

    /**
     * Returns all features of the featurecollections in the MVT PBF format as
     * byte array.
     *
     * @param featureCollectionStyleMap a map of all features and its refering style
     *
     * @return byte[] containing the information in the MVT format
     */
    public byte[] adaptFeatures(Map<FeatureCollection,Style> featureCollectionStyleMap, double scaleDenominator) {
        this.addFeaturesToEncoder(featureCollectionStyleMap, scaleDenominator);
        return this.vectorTileEncoder.encode();
    }

    /**
     * Writes the PBF result directly to the output stream which is given as argument.
     *
     * @param featureCollectionStyleMap the feature collection map to encode and its refering style
     * @param outputStream the PBF output stream to write to
     * @throws IOException
     */
    public void writeFeatures(Map<FeatureCollection,Style> featureCollectionStyleMap,double scaleDenominator, OutputStream outputStream) throws IOException {
        this.addFeaturesToEncoder(featureCollectionStyleMap, scaleDenominator);
        this.vectorTileEncoder.encode(outputStream);
    }

    /**
     * Adds all features to the encoder and prepares it before. So geometries are removed from the attribute map and
     * the geometry is transformed to the target tile local system
     *
     * @param featureCollectionStyleMap the feature collection map to be encoded and its refering style
     */
    private void addFeaturesToEncoder(Map<FeatureCollection,Style> featureCollectionStyleMap, double scaleDenominator) {
        for (FeatureCollection featureCollection : featureCollectionStyleMap.keySet()) {
            String layerName = featureCollection.getSchema().getName().getLocalPart();
            Style featureStyle = featureCollectionStyleMap.get(featureCollection);
            try (FeatureIterator<SimpleFeature> it = featureCollection.features()) {
                while (it.hasNext()) {
                    SimpleFeature feature = it.next();
                    Collection<Property> propertiesList = feature.getProperties();
                    Map<String, Object> attributeMap = new HashMap<>();
                    for (Property property : propertiesList) {
                        if (!(property.getValue() instanceof Geometry)) {
                            attributeMap.put(property.getName().toString(), property.getValue());
                        }
                    }
                    //Process GeometryTransformations in Symbolizers. It is possible to render the same geometry
                    //with more than one symbolizer. Therefore a list is returned.
                    List<Geometry> geometryList = processSymbolizers(featureStyle,feature,scaleDenominator);
                    for (Geometry geometry : geometryList) {
                        geometry = transFormGeometry(geometry);
                        this.vectorTileEncoder.addFeature(layerName, attributeMap, geometry);
                    }
                }
            }
        }
    }

    /**
     * All geometries that are generated by this style with the symbolizers are returned by this method. If no
     * symbolizer has been defined the original geometry is returned.
     *
     * @param style the style for the feature containing all rules
     * @param feature the feature that should be rendered
     * @return all geometries that can be generated by the symbolizers or the original geometry if no symbolizers are defined
     */
    private List<Geometry> processSymbolizers(Style style, SimpleFeature feature, double currentScaleDenominator) {
        List<Geometry> geometryList = new ArrayList<>();
        if (style != null && feature != null) {
            for (FeatureTypeStyle featureTypeStyle : style.featureTypeStyles()) {
                for (Rule rule : featureTypeStyle.rules()) {
                    if (ruleInScale(rule, currentScaleDenominator)) {
                        Filter filter = rule.getFilter();
                        //If no filter is present or the feature evaluates positive against the filter
                        if (filter == null || filter.evaluate(feature)) {
                            Geometry geometry = null;
                            //If no symbolizers are present, get the default geometry
                            if (rule.symbolizers() == null || rule.symbolizers().isEmpty()) {
                                geometry = (Geometry) feature.getDefaultGeometry();
                                if (geometry != null) {
                                    geometryList.add(geometry);
                                }
                                //If symbolizers are present get the geometry from the symbolizers (especially
                                //if geometric expressions have to be executed
                            } else {
                                for (Symbolizer symbolizer : rule.symbolizers()) {
                                    if (symbolizer instanceof AbstractSymbolizer) {
                                        boolean duplicateGeometry = false;
                                        geometry = findGeometry(feature, (AbstractSymbolizer) symbolizer);
                                        //Check for duplicate geometries, e.g. if you use a ordinary wms style with
                                        //foreground and background rendering then the geometries could be duplicate.
                                        for (Geometry geometryInList : geometryList) {
                                            if (geometry.equals(geometryInList)) {
                                                duplicateGeometry = true;
                                            }
                                        }
                                        if (!duplicateGeometry && geometry != null) {
                                            geometryList.add(geometry);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return geometryList;
    }

    /**
     * This method evaluates the rule against the current scale denominator.
     *
     * @param rule the current rule to be checked
     * @param currentScaleDenominator the current scale denominator
     * @return true if the current rule matches to the current scale denominator, otherwise false
     */
    private boolean ruleInScale(Rule rule, double currentScaleDenominator) {
        //true if within range or no cale denominator set
        boolean inScale = true;
        if (rule.getMinScaleDenominator() > 0 && currentScaleDenominator < rule.getMinScaleDenominator()) {
            inScale = false;
        }
        if (rule.getMaxScaleDenominator() < Double.POSITIVE_INFINITY && currentScaleDenominator > rule.getMaxScaleDenominator()) {
            inScale = false;
        }
        return inScale;
    }

    /**
     * Transforms the geometry to the tile local CRS. In a second step some affine transformations
     * are performed to match the coordinates to the target system.
     *
     * @param geometry the geometry to be transformed
     * @return the transformed geometry
     */
    private Geometry transFormGeometry(Geometry geometry) {
        geometry = doManualTransformation((Geometry)geometry.clone());
        return geometry;
    }

    /**
     * Transforms the geometries in several steps. First a translation is done. The coordinates are reduced to the tile
     * edges. In a second step the coordinates are scaled to the target system. In a third the coordinates are mirrored
     * horizontally since the origin of the target coordinate system is top left and not bottom left. In a last step the
     * coordinates are clipped to the start coordinate. In case of a buffer the targetBBOX coordinates are negative. Without
     * buffer the last step is not necessary because the min values of the targetBBOX are always 0.
     *
     * @param geometry the geometry to be converted
     * @return the converted geometry
     */
    private Geometry doManualTransformation(Geometry geometry) {
        // TODO maybe also {@link RendererUtilities#worldToScreenTransform(Envelope, Rectangle)} can be used for this operation
        for (Coordinate coordinate : geometry.getCoordinates()) {
            coordinate.x = targetBBOX.getMinX() + ((coordinate.x - sourceBBOX.getMinX()) * xScale);
            coordinate.y = targetBBOX.getMinY() + (targetBBOX.getHeight() - ((coordinate.y - sourceBBOX.getMinY()) * yScale ));
        }
        return geometry;
    }

    /** Finds the geometric attribute requested by the symbolizer.
     * Method copied from {@link org.geotools.renderer.lite.StreamingRenderer#findGeometry(Object, org.geotools.styling.Symbolizer)}
     *
     * @param drawMe the feature
     * @param s the symbolizer
     *
     * @return The geometry requested in the symbolizer, or the default geometry
     *         if none is specified
     */
    private Geometry findGeometry(Object drawMe, AbstractSymbolizer s) {
        Expression geomExpr = s.getGeometry();

        // get the geometry
        Geometry geom;
        if(geomExpr == null) {
            if(drawMe instanceof SimpleFeature) {
                geom = (Geometry) ((SimpleFeature) drawMe).getDefaultGeometry();
            } else if (drawMe instanceof Feature) {
                geom = (Geometry) ((Feature) drawMe).getDefaultGeometryProperty().getValue();
            } else {
                geom = CommonFactoryFinder.getFilterFactory2().property("").evaluate(drawMe, Geometry.class);
            }
        } else {
            geom = geomExpr.evaluate(drawMe, Geometry.class);
        }

        return geom;
    }
}
