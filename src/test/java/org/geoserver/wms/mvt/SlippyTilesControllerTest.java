package org.geoserver.wms.mvt;

import org.geoserver.AbstractMVTTest;
import org.junit.Assert;
import org.junit.Test;

import com.mockrunner.mock.web.MockHttpServletResponse;

/**
 *
 * Test for access to slippy map controller
 *
 */
public class SlippyTilesControllerTest extends AbstractMVTTest {

    @Test
    public void testRequestParams() throws Exception {
    	
    	String requestSlippy = "slippymap/" + TEST_LINES.getPrefix() + ":" + TEST_LINES.getLocalPart() + "/12/2196/1427.pbf" +
    			"?buffer=10&styles=" + STYLE_NAME + "&tileSize=256&gen_level=low";    	
        MockHttpServletResponse responseSlippy = getAsServletResponse(requestSlippy);
        Assert.assertEquals(200, responseSlippy.getStatusCode());
        
/*      test if content is the same when requested via slippy map or wms (tile is same then bbox). 
 * 		Unfortunately it seems that the forward in slippymap Controller is not executed correctly in Moc environment.
 * 		Response is ok (200) but content is empty, debugging shows that no breakpoint in MVT Plugin is hit in case of slippy map request 
 * 
   		String requestWms = "wms?request=getmap&service=wms&version=1.1.1" +
                "&format=" + MVT.MIME_TYPE +
                "&layers=" + TEST_LINES.getPrefix() + ":" + TEST_LINES.getLocalPart() +
                "&styles=" + STYLE_NAME + 
                "&height=256&width=256&bbox=1448023.063834379,6066042.5647115875,1457807.0034548815,6075826.50433209&srs=EPSG:3857&buffer=10";
        MockHttpServletResponse responseWms = getAsServletResponse(requestWms);

        byte[] contentSlippy = responseSlippy.getOutputStreamContent().getBytes();
        byte[] contentWms = responseWms.getOutputStreamContent().getBytes();
        Assert.assertEquals(contentSlippy.length, contentWms.length);*/
    }
    
}
