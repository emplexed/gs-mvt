package org.geoserver.wms.mvt;

import org.geoserver.AbstractMVTTest;
import org.geoserver.slippymap.SlippyMapTileCalculator;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletResponse;

/** Test for access to slippy map controller */
public class SlippyTilesControllerTest extends AbstractMVTTest {

    @Test
    public void testRequestParams() throws Exception {

        int x = 2196;
        int y = 1427;
        int z = 12;

        int buffer = 10;

        String requestSlippy =
                "slippymap/"
                        + TEST_LINES.getPrefix()
                        + ":"
                        + TEST_LINES.getLocalPart()
                        + "/"
                        + z
                        + "/"
                        + x
                        + "/"
                        + y
                        + ".pbf"
                        + "?buffer="
                        + buffer
                        + "&styles="
                        + STYLE_NAME
                        + "&tileSize=256&gen_level=low"
                        + "&bboxToBoundsViewparam=true&viewparams=test123:test123;";
        MockHttpServletResponse responseSlippy = getAsServletResponse(requestSlippy);
        Assert.assertEquals(200, responseSlippy.getStatus());
        String forwardedUrl = responseSlippy.getForwardedUrl();
        Assert.assertNotNull(forwardedUrl);

        MockHttpServletResponse responseForwardedUrl = getAsServletResponse(forwardedUrl);
        byte[] contentForwardedWms = responseForwardedUrl.getContentAsByteArray();

        ReferencedEnvelope bbox = SlippyMapTileCalculator.tile2boundingBox(x, y, z, 3857);
        String bboxSb =
                "&bbox="
                        + bbox.getMinX()
                        + ','
                        + bbox.getMinY()
                        + ','
                        + bbox.getMaxX()
                        + ','
                        + bbox.getMaxY();

        String requestWms =
                "wms?request=getmap&service=wms&version=1.1.1"
                        + "&format="
                        + MVT.MIME_TYPE
                        + "&layers="
                        + TEST_LINES.getPrefix()
                        + ":"
                        + TEST_LINES.getLocalPart()
                        + "&styles="
                        + STYLE_NAME
                        + "&height=256&width=256"
                        + bboxSb
                        + "&srs=EPSG:3857&buffer="
                        + buffer;
        MockHttpServletResponse responseWms = getAsServletResponse(requestWms);

        byte[] contentWms = responseWms.getContentAsByteArray();
        Assert.assertEquals(contentForwardedWms.length, contentWms.length);
        Assert.assertArrayEquals(contentForwardedWms, contentWms);
    }
}
