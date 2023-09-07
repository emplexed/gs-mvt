package org.geoserver.wms.mvt;

import java.io.IOException;
import java.util.Set;
import org.geoserver.platform.ServiceException;
import org.geoserver.wms.GetMapOutputFormat;
import org.geoserver.wms.MapProducerCapabilities;
import org.geoserver.wms.WMSMapContent;

/** The Streaming map outputFormat class for WMS PBF files */
public class MVTStreamingMapOutputFormat implements GetMapOutputFormat {

    private static MapProducerCapabilities CAPABILITIES =
            // new MapProducerCapabilities(false, false, false, false, null);
            new MapProducerCapabilities(false, false, false);

    /**
     * @return {@code ["application/x-protobuf", "application/pbf", "application/mvt"]}
     * @see org.geoserver.wms.GetMapOutputFormat#getOutputFormatNames()
     */
    public Set<String> getOutputFormatNames() {
        return MVT.OUTPUT_FORMATS;
    }

    /**
     * @return {@code "application/x-protobuf"}
     * @see org.geoserver.wms.GetMapOutputFormat#getMimeType()
     */
    public String getMimeType() {
        return MVT.MIME_TYPE;
    }

    /** @see org.geoserver.wms.GetMapOutputFormat#produceMap(org.geoserver.wms.WMSMapContent) */
    public StreamingMVTMap produceMap(WMSMapContent mapContent)
            throws ServiceException, IOException {
        StreamingMVTMap mvt = new StreamingMVTMap(mapContent);
        mvt.setMimeType(getMimeType());
        mvt.setContentDispositionHeader(mapContent, ".pbf", false);
        return mvt;
    }

    public MapProducerCapabilities getCapabilities(String format) {
        return CAPABILITIES;
    }
}
