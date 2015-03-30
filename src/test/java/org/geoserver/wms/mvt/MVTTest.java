package org.geoserver.wms.mvt;

import com.mockrunner.mock.web.MockHttpServletResponse;
import org.apache.commons.io.IOUtils;
import org.geoserver.data.test.MockData;
import org.geoserver.data.test.SystemTestData;
import org.geoserver.wms.WMSTestSupport;
import org.junit.Assert;
import org.junit.Test;


import javax.xml.namespace.QName;
import java.io.*;
import java.util.Collections;

/**
 *
 * Test for creating a MVT PBF using an WMS request with FilterStyle.
 *
 */
public class MVTTest extends WMSTestSupport {

    public static QName TEST_LINES = new QName(MockData.CITE_URI, "test_lines", MockData.CITE_PREFIX);
    public static QName TEST_POINTS = new QName(MockData.CITE_URI, "test_points", MockData.CITE_PREFIX);
    public static QName TEST_POLYGONS = new QName(MockData.CITE_URI, "test_polygons", MockData.CITE_PREFIX);
    public static String STYLE_NAME = "test_pbf_filter";

    @Override
    protected void onSetUp(SystemTestData testData) throws Exception {
        super.onSetUp(testData);
        testData.addStyle(STYLE_NAME, "./test_pbf_filter.sld", getClass(), getCatalog());
        testData.addVectorLayer(TEST_LINES, Collections.EMPTY_MAP, "test_lines.properties", MVTTest.class, getCatalog());
        testData.addVectorLayer(TEST_POINTS, Collections.EMPTY_MAP, "test_points.properties",MVTTest.class, getCatalog());
        testData.addVectorLayer(TEST_POLYGONS, Collections.EMPTY_MAP, "test_polygons.properties",MVTTest.class, getCatalog());
    }

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
}
