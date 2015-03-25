package org.geoserver.wms.mvt;

import org.geoserver.wms.WMS;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by shennebe on 18.03.2015.
 */
class MVT {

    public static final String MIME_TYPE = "application/x-protobuf";

    public static final Set<String> OUTPUT_FORMATS = Collections
            .unmodifiableSet(new HashSet<String>(Arrays.asList(MIME_TYPE, "application/pbf",
                    "application/mvt")));

    private MVT() {
        //
    }

    public static boolean canHandle(WMS config, String myself) {
        //TODO fix use MVT Renderer
        String svgRendererTypeSetting = config.getSvgRenderer();
        if (null == svgRendererTypeSetting) {
            svgRendererTypeSetting = WMS.SVG_SIMPLE;
        }

        return svgRendererTypeSetting.equals(myself);
    }

}
