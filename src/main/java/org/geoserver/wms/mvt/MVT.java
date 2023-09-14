package org.geoserver.wms.mvt;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** MVT constants (mimetype and output formats) */
interface MVT {

    //#String MIME_TYPE = "application/x-protobuf";
    String MIME_TYPE = "application/vnd.mapbox-vector-tile";

    Set<String> OUTPUT_FORMATS =
            Collections.unmodifiableSet(
                    new HashSet<>(
                            Arrays.asList(
                                    MIME_TYPE,
                                    "application/pbf",
                                    "application/mvt",
                                    "application/x-protobuf;type=mapbox-vector",
                                    "application/vnd.mapbox-vector-tile")));
}
