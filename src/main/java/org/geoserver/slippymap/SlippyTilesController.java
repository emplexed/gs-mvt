package org.geoserver.slippymap;

import org.geoserver.wms.GeneralisationLevel;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.opengis.referencing.ReferenceIdentifier;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

import static org.geoserver.wms.mvt.MVTStreamingMapResponse.*;

/**
 * Slippy Map Tiles controller that converts the requests into WMS requests. Answers with an redirect to the WMS service
 * so that the security rules of the WMS service are used.
 *
 */
@Controller
@RequestMapping("/slippymap")
public class SlippyTilesController {

    private int defaultBuffer = 10;
    private String defaultFormat = "application/x-protobuf";
    private String defaultStyles = "";

    private Map<String,String> supportedOutputFormats;
    private Map<String,String> defaultTileSize;

    
    @InitBinder
	public void initBinder(WebDataBinder dataBinder) {
    	dataBinder.registerCustomEditor(GeneralisationLevel.class, new GeneralisationLevelEnumConverter());
	}
    
    @RequestMapping(value="/{layers}/{z}/{x}/{y}.{format}", method = RequestMethod.GET)
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
            @RequestParam(value = PARAM_GENERALISATION_FACTOR, required = false) Double gen_factor,
            @RequestParam(value = PARAM_GENERALISATION_LEVEL, required = false) GeneralisationLevel gen_level,
            @RequestParam(value = PARAM_SMALL_GEOM_THRESHOLD, required = false) Double small_geom_threshold,
            @RequestParam(value = "cql_filter", required = false) String cql_filter,
            final HttpServletRequest request,
            final HttpServletResponse response) throws IOException, ServletException {

        //Build relative WMS redirect URL from Path Variables and optional request params
        ReferencedEnvelope bbox = SlippyMapTileCalculator.tile2boundingBox(x, y, z,3857);
        StringBuilder sb = new StringBuilder("/wms?");
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
        sb.append("&WIDTH=").append(tileSize != null ? tileSize : defaultTileSize.get(format));
        sb.append("&HEIGHT=").append(tileSize != null ? tileSize : defaultTileSize.get(format));
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
        if (cql_filter != null) {
            sb.append("&CQL_FILTER=").append(cql_filter);
        }
        boolean envAppended = false;
        if(gen_factor != null) {
        	sb.append("&ENV=").append(PARAM_GENERALISATION_FACTOR).append(":").append(gen_factor);
        	envAppended = true;
        }
        if(gen_level != null) {
        	if(!envAppended) {
        		sb.append("&ENV=");
        		envAppended = true;
        	}
        	else {
        		sb.append(";");
        	}
        	sb.append(PARAM_GENERALISATION_LEVEL).append(":").append(gen_level.getValue());        	
        }
        if(small_geom_threshold != null) {
        	if(!envAppended) {
        		sb.append("&ENV=");
        	}
        	else {
        		sb.append(";");
        	}
        	sb.append(PARAM_SMALL_GEOM_THRESHOLD).append(":").append(small_geom_threshold);        	
        }
        
        String url = sb.toString();
        RequestDispatcher dispatcher = request.getRequestDispatcher(response.encodeRedirectURL(url));
        dispatcher.forward(request,response);
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

    /**
     * Mapping of file endings to the default tile size. While png slippy maps are requested by default in 256x256
     * mapbox requests the vector tiles in 512x512. The tileSize is important to calculate the scale denominators
     * and pixel offsets
     *
     * @param defaultTileSize a map of the default tile sizes
     */
    public void setDefaultTileSize(Map<String,String> defaultTileSize) {
        this.defaultTileSize = defaultTileSize;
    }
}
