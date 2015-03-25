package org.geoserver.wms.mvt;

import org.geoserver.platform.Operation;
import org.geoserver.platform.ServiceException;
import org.geoserver.wms.map.AbstractMapResponse;
import org.geoserver.wms.svg.StreamingSVGMap;

import java.io.IOException;
import java.io.OutputStream;

/**
 * The Streaming Map Response
 *
 * Created by shennebe on 18.03.2015.
 */
public class MVTStreamingMapResponse extends AbstractMapResponse {

    public MVTStreamingMapResponse() {
        super(StreamingMVTMap.class, MVT.OUTPUT_FORMATS);
    }

    @Override
    public void write(Object value, OutputStream output, Operation operation) throws IOException, ServiceException {
        StreamingMVTMap map = (StreamingMVTMap) value;
        try {
            map.encode(output);
        } finally {
            map.dispose();
        }
    }
}
