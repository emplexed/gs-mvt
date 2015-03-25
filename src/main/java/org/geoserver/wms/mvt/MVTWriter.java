package org.geoserver.wms.mvt;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.geotools.util.logging.Logging;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.filter.spatial.BBOX;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Class is not stateless. Writer transforms the geometries into the local tile coordinate System. For each tile
 * an own writer is needed.
 *
 * Created by shennebe on 16.03.2015.
 */
public class MVTWriter {

    private MathTransform transform;
    private VectorTileEncoder vectorTileEncoder;
    private final Envelope sourceBBOX;
    private final Envelope targetBBOX;
    private final double xFactor;
    private final double yFactor;

    private static CoordinateReferenceSystem targetCRS;
    private static final Logger LOGGER = Logging.getLogger(MVTWriter.class);

    static {
        try {
            targetCRS = CRS.decode("EPSG:3857");
        } catch (FactoryException e) {
            e.printStackTrace();
        }
    }

    public static MVTWriter getInstance(Envelope sourceBBOX, CoordinateReferenceSystem sourceCRS, int tileSizeX, int tileSizeY, int buffer) throws FactoryException, TransformException {
        Envelope targetBBOX = new ReferencedEnvelope(0 - buffer,tileSizeX + buffer,0 - buffer,tileSizeY + buffer,targetCRS);
        return new MVTWriter(sourceBBOX,targetBBOX,sourceCRS,targetCRS,buffer);
    }

    public static MVTWriter getInstance(Envelope sourceBBOX, CoordinateReferenceSystem sourceCRS, int tileSize) throws FactoryException, TransformException {
        return MVTWriter.getInstance(sourceBBOX, sourceCRS, tileSize, tileSize, 0);
    }

    public static MVTWriter getInstance(Envelope sourceBBOX, CoordinateReferenceSystem sourceCRS) throws FactoryException, TransformException {
        return MVTWriter.getInstance(sourceBBOX, sourceCRS, 256);
    }

    public static MVTWriter getInstance(BBOX bbox, CoordinateReferenceSystem sourceCRS)
            throws TransformException, FactoryException {
        Envelope sourceBBOX = JTS.toGeometry(bbox.getBounds()).getEnvelopeInternal();
        return MVTWriter.getInstance(sourceBBOX, sourceCRS, 256);
    }

    public ReferencedEnvelope getSourceBBOXWithBuffer() throws TransformException, FactoryException {
        return new ReferencedEnvelope(this.sourceBBOX,targetCRS);
    }

    private MVTWriter(Envelope sourceBBOX,
                      Envelope targetBBOX,
                      CoordinateReferenceSystem sourceCRS,
                      CoordinateReferenceSystem targetCRS,
                      int bufferSize) throws TransformException, FactoryException {
        this.transform = CRS.findMathTransform(sourceCRS, targetCRS);
        sourceBBOX = JTS.transform(sourceBBOX, transform);
        if (bufferSize > 0) {
            sourceBBOX = this.getBufferedSourceBBOX(bufferSize,sourceBBOX,targetBBOX, targetCRS);
        }
        this.sourceBBOX = sourceBBOX;
        this.targetBBOX = targetBBOX;
        this.xFactor = this.calculateXFactor();
        this.yFactor = this.calculateYFactor();
        this.vectorTileEncoder = new VectorTileEncoder(4096, 8, 0.1d);
    }

    private ReferencedEnvelope getBufferedSourceBBOX(int bufferSize,Envelope sourceBBOX, Envelope targetBBOX, CoordinateReferenceSystem targetCRS) throws TransformException {
        if (bufferSize > 0) {
            double xBuffer = sourceBBOX.getWidth() * bufferSize / targetBBOX.getWidth();
            double yBuffer = sourceBBOX.getHeight() * bufferSize / targetBBOX.getHeight();
            return new ReferencedEnvelope(sourceBBOX.getMinX()-xBuffer,sourceBBOX.getMaxX()+xBuffer,sourceBBOX.getMinY()+yBuffer,sourceBBOX.getMaxY()+yBuffer,targetCRS);
        }
        return null;
    }

    private double calculateXFactor() {
        double deltaSource = sourceBBOX.getMaxX() - sourceBBOX.getMinX();
        double deltaTarget = targetBBOX.getMaxX() - targetBBOX.getMinX();
        return deltaTarget / deltaSource;
    }

    private double calculateYFactor() {
        double deltaSource = sourceBBOX.getMaxY() - sourceBBOX.getMinY();
        double deltaTarget = targetBBOX.getMaxY() - targetBBOX.getMinY();
        return deltaTarget / deltaSource;
    }

    public byte[] adaptFeatures(List<FeatureCollection> featureCollectionList) {
        this.addFeaturesToEncoder(featureCollectionList);
        return this.vectorTileEncoder.encode();
    }

    public void writeFeatures(List<FeatureCollection> featureCollectionList, OutputStream outputStream) throws IOException {
        this.addFeaturesToEncoder(featureCollectionList);
        this.vectorTileEncoder.encode(outputStream);
    }

    private void addFeaturesToEncoder(List<FeatureCollection> featureCollectionList) {
        for (FeatureCollection featureCollection : featureCollectionList) {
            String layerName = featureCollection.getSchema().getName().getLocalPart();
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
                    Geometry geometry = (Geometry) feature.getDefaultGeometry();
                    geometry = transFormGeometry(geometry);
                    this.vectorTileEncoder.addFeature(layerName, attributeMap, geometry);
                }
            }
        }
    }

    private Geometry transFormGeometry(Geometry geometry) {
        try {
            if (geometry.getSRID() != 900913 && geometry.getSRID() != 3857) {
                geometry = JTS.transform(geometry, transform);
            }
            geometry = doManualTransformation(geometry);
        } catch (TransformException e) {
            LOGGER.log(Level.WARNING,"Exception while transforming Geometry",e);
        }
        return geometry;
    }

    private Geometry doManualTransformation(Geometry geometry) {
        for (Coordinate coordinate : geometry.getCoordinates()) {
            //First do Translation
            coordinate.x -= sourceBBOX.getMinX();
            coordinate.y -= sourceBBOX.getMinY();
            //Scale down to target bbox
            coordinate.x *= xFactor;
            coordinate.y *= yFactor;
            //Mirror the Y Axis since the target 0,0 is Top Left and not Bottom Left (source)
            coordinate.y = targetBBOX.getHeight() - coordinate.y;
            //Clip to start coordinate
            coordinate.x = targetBBOX.getMinX() + coordinate.x;
            coordinate.y = targetBBOX.getMinY() + coordinate.y;
        }
        return geometry;
    }
}
