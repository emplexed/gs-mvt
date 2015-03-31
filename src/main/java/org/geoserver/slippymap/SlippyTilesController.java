package org.geoserver.slippymap;

import org.geotools.geometry.jts.ReferencedEnvelope;
import org.opengis.referencing.ReferenceIdentifier;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/**
 * Slippy Map Tiles controller that converts the requests into WMS requests. Answers with an redirect to the WMS service
 * so that the security rules of the WMS service are used.
 *
 */
@Controller
public class SlippyTilesController {

    private int defaultBuffer = 10;
    private int defaultTileSize = 256;
    private String defaultFormat = "application/x-protobuf";
    private String defaultStyles = "";

    private Map<String,String> supportedOutputFormats;

    @RequestMapping(value="{layers}/{z}/{x}/{y}.{format}", method = RequestMethod.GET)
    public void doGetSlippyWmsMap(
            @PathVariable String layers,
            @PathVariable int z,
            @PathVariable int x,
            @PathVariable int y,
            @PathVariable String format,
            @RequestParam(value = "buffer", required = false) Integer buffer,
            @RequestParam(value = "tileSize", required = false) Integer tileSize,
            @RequestParam(value = "styles", required = false) String styles,
            @RequestParam(value = "time",required = false) String time,
            @RequestParam(value = "sld", required = false) String sld,
            @RequestParam(value = "sld_body", required = false) String sld_body,
            final HttpServletResponse response) throws IOException {

        //Build relative WMS redirect URL from Path Variables and optional request params
        ReferencedEnvelope bbox = SlippyMapTileCalculator.tile2boundingBox(x, y, z,3857);
        StringBuilder sb = new StringBuilder("/geoserver/service/wms?");
        sb.append("STYLES=").append(styles != null ? styles : defaultStyles);
        sb.append("&LAYERS=").append(layers);
        sb.append("&FORMAT=").append(format != null && supportedOutputFormats != null
                && !supportedOutputFormats.isEmpty() ? supportedOutputFormats.get(format) : defaultFormat);
        sb.append("&SERVICE=").append("WMS");
        sb.append("&VERSION=").append("1.1.1");
        sb.append("&REQUEST=").append("GetMap");
        sb.append("&SRS=").append(getCRSIdentifier(bbox.getCoordinateReferenceSystem()));
        sb.append("&BBOX=").append(bbox.getMinX()).append(',').append(bbox.getMinY())
                .append(',').append(bbox.getMaxX()).append(',').append(bbox.getMaxY());
        sb.append("&WIDTH=").append(tileSize != null ? tileSize : defaultTileSize);
        sb.append("&HEIGHT=").append(tileSize != null ? tileSize : defaultTileSize);
        sb.append("&BUFFER=").append(buffer != null ? buffer : defaultBuffer);
        if (time != null) {
            sb.append("&TIME=").append(time);
        }
        if (sld != null) {
            sb.append("&SLD=").append(sld);
        }
        if (sld_body != null) {
            sb.append("&SLD_BODY=").append(sld_body);
        }
        String url = sb.toString();
        response.sendRedirect(response.encodeRedirectURL(url));
    }

    private String getCRSIdentifier(CoordinateReferenceSystem coordinateReferenceSystem) {
        String name = "";
        if (coordinateReferenceSystem.getIdentifiers() == null) {
            name = coordinateReferenceSystem.getName().toString();
        } else {
            for (ReferenceIdentifier identifier : coordinateReferenceSystem.getIdentifiers()) {
                name = identifier.toString();
                break;
            }
        }
        return name;
    }

    public void setDefaultBuffer(int defaultBuffer) {
        this.defaultBuffer = defaultBuffer;
    }

    public void setDefaultTileSize(int defaultTileSize) {
        this.defaultTileSize = defaultTileSize;
    }

    public void setDefaultFormat(String defaultFormat) {
        this.defaultFormat = defaultFormat;
    }

    public void setDefaultStyles(String defaultStyles) {
        this.defaultStyles = defaultStyles;
    }

    public Map<String, String> getSupportedOutputFormats() {
        return supportedOutputFormats;
    }

    /**
     * Mapping of file endings to mime types / output formats know by the geoserver. Unfortunately file endings are not
     * recored by the {@link org.geoserver.wms.GetMapOutputFormat} interface so this has to be entered here manually.
     *
     * @param supportedOutputFormats the outputFormat to file ending mapping
     */
    public void setSupportedOutputFormats(Map<String, String> supportedOutputFormats) {
        this.supportedOutputFormats = supportedOutputFormats;
    }
}
