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
import java.util.List;

/**
 * Adapted WebMap implementation. Gets the style and retrieves filter rules. These rules are considered when loading
 * features from the datastore.
 *
 * Created by Stefan Henneberger on 18.03.2015.
 */
public class StreamingMVTMap extends WebMap {

    /**
     * @param context the map context, can be {@code null} is there's _really_ no context around
     */
    public StreamingMVTMap(WMSMapContent context) {
        super(context);
    }

    public void encode(final OutputStream out) throws IOException {
        ReferencedEnvelope renderingArea = this.mapContent.getRenderingArea();
        try {
            MVTWriter mvtWriter =
                    MVTWriter.getInstance(renderingArea, this.mapContent.getCoordinateReferenceSystem(),
                            this.mapContent.getMapWidth(), this.mapContent.getMapHeight(), this.mapContent.getBuffer());
            List<FeatureCollection> featureCollectionList = new ArrayList<>();
            FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
            for (Layer layer : this.mapContent.layers()) {
                SimpleFeatureSource featureSource = (SimpleFeatureSource) layer.getFeatureSource();
                SimpleFeatureType schema = featureSource.getSchema();
                String defaultGeometry = schema.getGeometryDescriptor().getName().getLocalPart();
                renderingArea = mvtWriter.getSourceBBOXWithBuffer() != null ? mvtWriter.getSourceBBOXWithBuffer() : renderingArea;
                BBOX bboxFilter = ff.bbox(ff.property(defaultGeometry), renderingArea);
                Query bboxQuery = new Query(schema.getTypeName(), bboxFilter);
                Query definitionQuery = layer.getQuery();
                Query finalQuery = new Query(DataUtilities.mixQueries(definitionQuery, bboxQuery,
                        "mvtEncoder"));
                if (layer.getStyle() != null) {
                    Filter styleFilter = getFeatureFilterFromStyle(layer.getStyle(),ff,this.mapContent.getScaleDenominator());
                    if (styleFilter != null) {
                        Query filterQuery = new Query(schema.getTypeName(),styleFilter);
                        finalQuery = new Query(DataUtilities.mixQueries(finalQuery,filterQuery,"mvtEncoder"));
                    }
                }
                finalQuery.setHints(definitionQuery.getHints());
                finalQuery.setSortBy(definitionQuery.getSortBy());
                finalQuery.setStartIndex(definitionQuery.getStartIndex());
                featureCollectionList.add(featureSource.getFeatures(finalQuery));
            }
            mvtWriter.writeFeatures(featureCollectionList,out);
        } catch (TransformException | FactoryException e) {
            e.printStackTrace();
        }
    }

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
