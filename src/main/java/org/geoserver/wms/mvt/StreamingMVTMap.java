package org.geoserver.wms.mvt;

import org.geoserver.wms.WMSMapContent;
import org.geoserver.wms.WebMap;
import org.geotools.data.DataUtilities;
import org.geotools.data.Query;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.FeatureCollection;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.map.Layer;
import org.geotools.styling.FeatureTypeStyle;
import org.geotools.styling.Rule;
import org.geotools.styling.Style;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.filter.spatial.BBOX;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapted WebMap implementation. Gets the style and retrieves filter rules. These rules are considered when loading
 * features from the datastore.
 *
 */
public class StreamingMVTMap extends WebMap {

    /**
     * The Mapbox client requests 512x512 tiles but the target tile has 256 units in each direction.
     */
    private static int targetBinaryCRSTileSize = 256;

    /**
     * @param context the map context, can be {@code null} is there's _really_ no context around
     */
    public StreamingMVTMap(WMSMapContent context) {
        super(context);
    }

    /**
     * Retrieves the feature from the underlying datasource and encodes them the MVT PBF format.
     *
     * @param out the outputstream to write to
     * @throws IOException
     */
    public void encode(final OutputStream out) throws IOException {
        ReferencedEnvelope renderingArea = this.mapContent.getRenderingArea();
        try {
            MVTWriter mvtWriter =
                    MVTWriter.getInstance(renderingArea, this.mapContent.getCoordinateReferenceSystem(),
                            targetBinaryCRSTileSize, targetBinaryCRSTileSize, this.mapContent.getBuffer());
            Map<FeatureCollection,Style> featureCollectionStyleMap = new HashMap<>();
            FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
            //Iterate through all layers. Layers can be requested through WMS with comma separation
            for (Layer layer : this.mapContent.layers()) {
                SimpleFeatureSource featureSource = (SimpleFeatureSource) layer.getFeatureSource();
                SimpleFeatureType schema = featureSource.getSchema();
                String defaultGeometry = schema.getGeometryDescriptor().getName().getLocalPart();
                //Retrieve rendering area. In case of a buffer the extent is the buffered extent and not the requested
                //extent.
                renderingArea = mvtWriter.getSourceBBOXWithBuffer() != null ? mvtWriter.getSourceBBOXWithBuffer() : renderingArea;
                BBOX bboxFilter = ff.bbox(ff.property(defaultGeometry), renderingArea);
                Query bboxQuery = new Query(schema.getTypeName(), bboxFilter);
                Query definitionQuery = layer.getQuery();
                Query finalQuery = new Query(DataUtilities.mixQueries(definitionQuery, bboxQuery,
                        "mvtEncoder"));
                if (layer.getStyle() != null) {
                    //Add Style Filters to the request
                    Filter styleFilter = getFeatureFilterFromStyle(layer.getStyle(),ff,this.mapContent.getScaleDenominator());
                    if (styleFilter != null) {
                        Query filterQuery = new Query(schema.getTypeName(),styleFilter);
                        finalQuery = new Query(DataUtilities.mixQueries(finalQuery,filterQuery,"mvtEncoder"));
                    }
                }
                finalQuery.setCoordinateSystemReproject(MVTWriter.TARGET_CRS);
                finalQuery.setHints(definitionQuery.getHints());
                finalQuery.setSortBy(definitionQuery.getSortBy());
                finalQuery.setStartIndex(definitionQuery.getStartIndex());
                //Retrieve feature collection from the layer
                featureCollectionStyleMap.put(featureSource.getFeatures(finalQuery), layer.getStyle());
            }
            //Write all features to the output stream
            mvtWriter.writeFeatures(featureCollectionStyleMap, this.mapContent.getScaleDenominator(), out);
        } catch (TransformException | FactoryException e) {
            e.printStackTrace();
        }
    }

    /**
     * Retrieve Filter information from the Layer Style.
     * TODO maybe there is a better method to do that e.g. using a {@link org.geotools.styling.StyleVisitor}
     *
     * @param style the style of the layer
     * @param ff the filter factory to create (concat) filters
     * @param currentScaleDenominator the current scale denominator of the reuquested tiles
     *
     * @return The filter containing all relevant filters for the current solutions or null if no filter is difined.
     */
    private Filter getFeatureFilterFromStyle(Style style, FilterFactory2 ff, double currentScaleDenominator) {
        List<Filter> filter = new ArrayList<>();
        for (FeatureTypeStyle featureTypeStyle : style.featureTypeStyles()) {
            for (Rule rule : featureTypeStyle.rules()) {
                if ((rule.getMaxScaleDenominator() < Double.POSITIVE_INFINITY && currentScaleDenominator < rule.getMaxScaleDenominator())
                        || (rule.getMinScaleDenominator() > 0 && currentScaleDenominator > rule.getMinScaleDenominator())) {
                    filter.add(rule.getFilter());
                } else if (rule.getMinScaleDenominator() == 0 && rule.getMaxScaleDenominator() == Double.POSITIVE_INFINITY) {
                    //No Scale denominator defined so render all
                    if (rule.getFilter() == null) {
                        return null;
                    } else {
                        filter.add(rule.getFilter());
                    }
                }
            }
        }
        return ff.or(filter);
    }
}
