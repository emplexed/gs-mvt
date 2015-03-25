package org.geoserver.wfs.response;

import net.opengis.wfs.GetFeatureType;
import net.opengis.wfs.QueryType;
import org.geoserver.config.GeoServer;
import org.geoserver.platform.Operation;
import org.geoserver.platform.ServiceException;
import org.geoserver.wfs.WFSGetFeatureOutputFormat;
import org.geoserver.wfs.request.FeatureCollectionResponse;
import org.geoserver.wms.mvt.MVTWriter;
import org.geotools.feature.FeatureCollection;
import org.geotools.util.logging.Logging;
import org.opengis.filter.Filter;
import org.opengis.filter.spatial.BBOX;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.TransformException;

import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class provides the possibility to get MVT PBF features. It is
 *
 *
 */
@Deprecated
public class MVTOutputFormat extends WFSGetFeatureOutputFormat {

    private static final Logger LOGGER = Logging.getLogger(MVTOutputFormat.class);

    public static final String format = "MVT-PBF";

    private static final String extension = ".pbf";

    public MVTOutputFormat(GeoServer gs) {
        super(gs, format);
    }

    /**
     * Mime type: application/x-protobuf
     */
    @Override
    public String getMimeType(Object value, Operation operation) throws ServiceException {
        return "application/x-protobuf";
    }

    @Override
    public String getPreferredDisposition(Object value, Operation operation) {
        return DISPOSITION_ATTACH;
    }

    @Override
    public String getAttachmentFileName(Object value, Operation operation) {
        String filename = "error";
        if (value instanceof FeatureCollectionResponse
                && !((FeatureCollectionResponse) value).getFeatures().isEmpty()) {
            filename = ((FeatureCollectionResponse) value).getFeatures().get(0).getSchema().getName().getNamespaceURI();
            filename += "-";
            filename += ((FeatureCollectionResponse) value).getFeatures().get(0).getSchema().getName().getLocalPart();
        }
        return filename + extension;
    }

    @Override
    protected void write(FeatureCollectionResponse featureCollection, OutputStream output, Operation getFeature)
            throws IOException, ServiceException {
        BBOX bbox = extractBBOXFromQuery(getFeature);
        CoordinateReferenceSystem sourceCRS = extractSourceCRS(featureCollection);
        MVTWriter mvtWriter = null;
        try {
            mvtWriter = MVTWriter.getInstance(bbox, sourceCRS);
            output.write(mvtWriter.adaptFeatures(featureCollection.getFeatures()));
        } catch (TransformException | FactoryException e) {
            LOGGER.log(Level.WARNING,"Coordinate transformation failed");
        }
    }

    private BBOX extractBBOXFromQuery(Operation getFeature) {
        BBOX bbox = null;
        for (Object parameter : getFeature.getParameters()) {
            if (bbox != null) {
                break;
            }
            if (parameter instanceof GetFeatureType) {
                for (Object query : ((GetFeatureType) parameter).getQuery()) {
                    if (query instanceof QueryType) {
                        Filter filter = ((QueryType) query).getFilter();
                        if (filter instanceof BBOX) {
                            bbox = (BBOX) filter;
                            break;
                        }
                    }
                }
            }
        }
        return bbox;
    }

    private CoordinateReferenceSystem extractSourceCRS(FeatureCollectionResponse featureCollectionResponse) {
        //TODO maybe there are multiple layers with multiple CRS Systems
        CoordinateReferenceSystem coordinateReferenceSystem = null;
        for (FeatureCollection featureCollection : featureCollectionResponse.getFeatures()) {
            coordinateReferenceSystem = featureCollection.getSchema().getCoordinateReferenceSystem();
            break;
        }
        return coordinateReferenceSystem;
    }
}
