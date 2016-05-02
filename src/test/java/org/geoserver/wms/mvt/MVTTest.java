package org.geoserver.wms.mvt;

import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.geoserver.AbstractMVTTest;
import org.junit.Assert;
import org.junit.Test;

import com.mockrunner.mock.web.MockHttpServletResponse;

/**
 *
 * Test for creating a MVT PBF using an WMS request with FilterStyle.
 *
 */
public class MVTTest extends AbstractMVTTest {

    @Test
    public void testBasicMvtGeneratorWithStyle() throws Exception {
        MockHttpServletResponse response = getAsServletResponse("wms?request=getmap&service=wms&version=1.1.1" +
                "&format=" + MVT.MIME_TYPE +
                "&layers=" + TEST_LINES.getPrefix() + ":" + TEST_LINES.getLocalPart() + ","
                + TEST_POINTS.getPrefix() + ":" + TEST_POINTS.getLocalPart() + ","
                + TEST_POLYGONS.getPrefix() + ":" + TEST_POLYGONS.getLocalPart() +
                "&styles=" + STYLE_NAME + "," + STYLE_NAME + "," + STYLE_NAME +
                "&height=256&width=256&bbox=1448023.063834379,6066042.5647115875,1457807.0034548815,6075826.50433209&srs=EPSG:3857&buffer=10");
        InputStream inputStream = this.getClass().getResourceAsStream("test_result.pbf");
        byte[] inputBytes = IOUtils.toByteArray(inputStream);
        byte[] content = response.getOutputStreamContent().getBytes();
        Assert.assertEquals(inputBytes.length, content.length);
        Assert.assertArrayEquals(inputBytes,content);
        IOUtils.closeQuietly(inputStream);
    }
    
    @Test
    public void testBasicMvtGeneratorWithCustomGeneralisation() throws Exception {
    	String request = "wms?request=getmap&service=wms&version=1.1.1" +
                "&format=" + MVT.MIME_TYPE +
                "&layers=" + TEST_LINES.getPrefix() + ":" + TEST_LINES.getLocalPart() + ","
                + TEST_POINTS.getPrefix() + ":" + TEST_POINTS.getLocalPart() + ","
                + TEST_POLYGONS.getPrefix() + ":" + TEST_POLYGONS.getLocalPart() +
                "&styles=" + STYLE_NAME + "," + STYLE_NAME + "," + STYLE_NAME +
                "&height=256&width=256" +
                "&bbox=1448023.063834379,6066042.5647115875,1457807.0034548815,6075826.50433209&srs=EPSG:3857&buffer=10";

        MockHttpServletResponse responseDefault = getAsServletResponse(request);
        
    	String request05 = request + "&env=gen_factor:0.5";
    	MockHttpServletResponse response05 = getAsServletResponse(request05);
    
    	String request20 = request + "&env=gen_factor:2.0";
    	MockHttpServletResponse response20 = getAsServletResponse(request20);
    	
        byte[] contentDefault = responseDefault.getOutputStreamContent().getBytes();
        byte[] content05 = response05.getOutputStreamContent().getBytes();
        byte[] content20 = response20.getOutputStreamContent().getBytes();
        
        
        Assert.assertTrue(contentDefault.length > content05.length);
        Assert.assertTrue(content05.length > content20.length);
    }
    
    
    @Test
    public void testBasicMvtGeneratorWithGeneralisationLevels() throws Exception {
    	String request = "wms?request=getmap&service=wms&version=1.1.1" +
                "&format=" + MVT.MIME_TYPE +
                "&layers=" + TEST_LINES.getPrefix() + ":" + TEST_LINES.getLocalPart() + ","
                + TEST_POINTS.getPrefix() + ":" + TEST_POINTS.getLocalPart() + ","
                + TEST_POLYGONS.getPrefix() + ":" + TEST_POLYGONS.getLocalPart() +
                "&styles=" + STYLE_NAME + "," + STYLE_NAME + "," + STYLE_NAME +
                "&height=256&width=256" +
                "&bbox=1448023.063834379,6066042.5647115875,1457807.0034548815,6075826.50433209&srs=EPSG:3857&buffer=10";

        MockHttpServletResponse responseDefault = getAsServletResponse(request);
        
    	String requestLowGen = request + "&env=gen_level:low";
    	MockHttpServletResponse responseLowGen = getAsServletResponse(requestLowGen);
    
    	String requestHigh = request + "&env=gen_level:high";
    	MockHttpServletResponse responseHigh = getAsServletResponse(requestHigh);
    	
        byte[] contentDefault = responseDefault.getOutputStreamContent().getBytes();
        byte[] contentLow = responseLowGen.getOutputStreamContent().getBytes();
        byte[] contentHigh = responseHigh.getOutputStreamContent().getBytes();
        
        // size of default (mid) generalisation is larger then with high generalisation
        Assert.assertTrue(contentDefault.length > contentHigh.length);
        // size of log  generalisation is larger then with high generalisation
        Assert.assertTrue(contentLow.length > contentHigh.length);
    }
    
}
