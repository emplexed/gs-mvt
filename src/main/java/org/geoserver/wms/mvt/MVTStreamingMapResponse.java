package org.geoserver.wms.mvt;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.logging.Logger;
import org.apache.commons.lang.math.NumberUtils;
import org.geoserver.platform.Operation;
import org.geoserver.platform.ServiceException;
import org.geoserver.wms.GeneralisationLevel;
import org.geoserver.wms.GetMapRequest;
import org.geoserver.wms.map.AbstractMapResponse;
import org.geotools.util.logging.Logging;

/**
 * The Streaming Map Response using the StreamingMVTMap for retrieving and encoding the features.
 */
public class MVTStreamingMapResponse extends AbstractMapResponse {

    private static final Logger LOGGER = Logging.getLogger(MVTStreamingMapResponse.class);

    public static final double DEFAULT_GENERALISATION_FACTOR = 0.1;
    public static final double DEFAULT_SMALL_GEOMETRY_THRESHOLD = 0.05;
    public static final String PARAM_GENERALISATION_FACTOR = "gen_factor";
    public static final String PARAM_GENERALISATION_LEVEL = "gen_level";
    public static final String PARAM_SMALL_GEOM_THRESHOLD = "small_geom_threshold";

    private GeneralisationLevel defaultGenLevel;
    private Map<GeneralisationLevel, Map<Integer, Double>> generalisationTables;

    public MVTStreamingMapResponse() {
        super(StreamingMVTMap.class, MVT.OUTPUT_FORMATS);
    }

    @Override
    public void write(Object value, OutputStream output, Operation operation)
            throws IOException, ServiceException {
        StreamingMVTMap map = (StreamingMVTMap) value;
        // if no generalisation factor / level requested from outside => use default config (factor
        // for level mid)
        // use as fallback
        // double genFactor = getGenFactorForGenLevel(defaultGenLevel);
        Double genFactor = null;
        Double smallGeometryThreshold = DEFAULT_SMALL_GEOMETRY_THRESHOLD;
        Map<Integer, Double> genFactorTable = getGenFactorForGenLevel(defaultGenLevel);
        if (operation.getParameters()[0] instanceof GetMapRequest) {
            // check configuration based on parameters
            GetMapRequest request = (GetMapRequest) operation.getParameters()[0];
            // if a generalisation factor is given we use it
            Object reqGenFactor = request.getEnv().get(PARAM_GENERALISATION_FACTOR);
            Object reqGenLevel = request.getEnv().get(PARAM_GENERALISATION_LEVEL);

            if (reqGenFactor != null && NumberUtils.isNumber((String) reqGenFactor)) {
                genFactor =
                        NumberUtils.toDouble((String) reqGenFactor, DEFAULT_GENERALISATION_FACTOR);
            }
            // if no generalisation factor is given but a generalisation level is requested
            // we have to look up the currently suiting generalisation
            else if (reqGenLevel != null) {
                genFactorTable = getGenFactorForRequestedLevel(reqGenLevel);
            }
            Object reqSkipSmallGeoms = request.getEnv().get(PARAM_SMALL_GEOM_THRESHOLD);
            if (reqSkipSmallGeoms != null) {
                smallGeometryThreshold =
                        NumberUtils.toDouble(
                                (String) reqSkipSmallGeoms, DEFAULT_SMALL_GEOMETRY_THRESHOLD);
            }
        }
        try {
            // passed in generlalisation factor is overriding default configuration (table for
            // zooms)
            if (genFactor != null) {
                map.encode(output, smallGeometryThreshold, genFactor);
            } else {
                map.encode(
                        output,
                        smallGeometryThreshold,
                        genFactorTable,
                        DEFAULT_GENERALISATION_FACTOR);
            }
        } finally {
            map.dispose();
        }
    }

    private Map<Integer, Double> getGenFactorForGenLevel(GeneralisationLevel genLevel) {
        return generalisationTables.get(genLevel);
    }

    private Map<Integer, Double> getGenFactorForRequestedLevel(Object reqGenLevel) {
        GeneralisationLevel genLevel =
                GeneralisationLevel.valueOf(reqGenLevel.toString().toUpperCase());
        if (genLevel == null) {
            LOGGER.warning(
                    "requested generalisation level "
                            + reqGenLevel
                            + " is not a valid value (use \"low\", \"mid\", \"high\". "
                            + "Default generalisation level (mid) will be used");
            genLevel = defaultGenLevel;
        }
        return getGenFactorForGenLevel(genLevel);
    }

    public GeneralisationLevel getDefaultGenLevel() {
        return defaultGenLevel;
    }

    public void setDefaultGenLevel(GeneralisationLevel defaultGenLevel) {
        this.defaultGenLevel = defaultGenLevel;
    }

    public Map<GeneralisationLevel, Map<Integer, Double>> getGeneralisationTables() {
        return generalisationTables;
    }

    public void setGeneralisationTables(
            Map<GeneralisationLevel, Map<Integer, Double>> generalisationTables) {
        this.generalisationTables = generalisationTables;
    }
}
