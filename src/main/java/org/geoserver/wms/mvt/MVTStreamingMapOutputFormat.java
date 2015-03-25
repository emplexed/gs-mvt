package org.geoserver.wms.mvt;

import org.geoserver.platform.ServiceException;
import org.geoserver.wms.GetMapOutputFormat;
import org.geoserver.wms.MapProducerCapabilities;
import org.geoserver.wms.WMSMapContent;

import java.io.IOException;
import java.util.Set;

/**
 * Created by shennebe on 18.03.2015.
 */
public class MVTStreamingMapOutputFormat implements GetMapOutputFormat {

    private static MapProducerCapabilities CAPABILITIES= new MapProducerCapabilities(false, false, false, false, null);

    public MVTStreamingMapOutputFormat() {
        //
    }

    /**
     * @return {@code ["image/svg+xml", "image/svg xml", "image/svg"]}
     * @see org.geoserver.wms.GetMapOutputFormat#getOutputFormatNames()
     */
    public Set<String> getOutputFormatNames() {
        return MVT.OUTPUT_FORMATS;
    }

    /**
     * @return {@code "image/svg+xml"}
     * @see org.geoserver.wms.GetMapOutputFormat#getMimeType()
     */
    public String getMimeType() {
        return MVT.MIME_TYPE;
    }

    /**
     *
     * @see org.geoserver.wms.GetMapOutputFormat#produceMap(org.geoserver.wms.WMSMapContent)
     */
    public StreamingMVTMap produceMap(WMSMapContent mapContent) throws ServiceException,
            IOException {
        StreamingMVTMap mvt = new StreamingMVTMap(mapContent);
        mvt.setMimeType(getMimeType());
        return mvt;
    }

    public MapProducerCapabilities getCapabilities(String format) {
        return CAPABILITIES;
    }
}
