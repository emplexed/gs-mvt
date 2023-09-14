package org.geoserver.wms.mvt;

import static org.geoserver.wms.mvt.MVTStreamingMapResponse.AVOID_EMPTY_PROTO;
import static org.geoserver.wms.mvt.MVTStreamingMapResponse.PARAM_SMALL_GEOM_THRESHOLD;

import java.io.InputStream;
import org.apache.commons.io.IOUtils;
import org.geoserver.AbstractMVTTest;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletResponse;

/** Test for creating a MVT PBF using an WMS request with FilterStyle. */
public class MVTTest extends AbstractMVTTest {

    @Test
    @Ignore
    // TODO: fix me, reference pbf has to be fixed?
    public void testBasicMvtGeneratorWithStyle() throws Exception {
        MockHttpServletResponse response =
                getAsServletResponse(
                        "wms?request=getmap&service=wms&version=1.1.1"
                                + "&format="
                                + MVT.MIME_TYPE
                                + "&layers="
                                + TEST_LINES.getPrefix()
                                + ":"
                                + TEST_LINES.getLocalPart()
                                + ","
                                + TEST_POINTS.getPrefix()
                                + ":"
                                + TEST_POINTS.getLocalPart()
                                + ","
                                + TEST_POLYGONS.getPrefix()
                                + ":"
                                + TEST_POLYGONS.getLocalPart()
                                + "&styles="
                                + STYLE_NAME
                                + ","
                                + STYLE_NAME
                                + ","
                                + STYLE_NAME
                                + "&height=256&width=256&bbox=1448023.063834379,6066042.5647115875,1457807.0034548815,6075826.50433209&srs=EPSG:3857&buffer=10");
        InputStream inputStream = this.getClass().getResourceAsStream("test_result.pbf");
        byte[] inputBytes = IOUtils.toByteArray(inputStream);
        byte[] content = response.getContentAsByteArray();
        Assert.assertEquals(inputBytes.length, content.length);
        IOUtils.closeQuietly(inputStream);
    }

    @Test
    public void testBasicMvtGeneratorWithCustomGeneralisation() throws Exception {
        String request =
                "wms?request=getmap&service=wms&version=1.1.1"
                        + "&format="
                        + MVT.MIME_TYPE
                        + "&layers="
                        + TEST_LINES.getPrefix()
                        + ":"
                        + TEST_LINES.getLocalPart()
                        + ","
                        + TEST_POINTS.getPrefix()
                        + ":"
                        + TEST_POINTS.getLocalPart()
                        + ","
                        + TEST_POLYGONS.getPrefix()
                        + ":"
                        + TEST_POLYGONS.getLocalPart()
                        + "&styles="
                        + STYLE_NAME
                        + ","
                        + STYLE_NAME
                        + ","
                        + STYLE_NAME
                        + "&height=256&width=256"
                        + "&bbox=1448023.063834379,6066042.5647115875,1457807.0034548815,6075826.50433209&srs=EPSG:3857&buffer=10";

        // map with gen_level (MID) to 0.4
        String defaultRequest = request + "&env=" + PARAM_SMALL_GEOM_THRESHOLD + ":-1";
        MockHttpServletResponse responseDefault = getAsServletResponse(defaultRequest);

        //        String request0005 = request + "&env=gen_factor:0.005";
        String request0005 =
                request + "&env=gen_factor:0.005;" + PARAM_SMALL_GEOM_THRESHOLD + ":-1";
        MockHttpServletResponse response0005 = getAsServletResponse(request0005);

        //        String request05 = request + "&env=gen_factor:0.5";
        String request05 = request + "&env=gen_factor:5000" + PARAM_SMALL_GEOM_THRESHOLD + ":-1";
        MockHttpServletResponse response05 = getAsServletResponse(request05);

        byte[] contentDefault = responseDefault.getContentAsByteArray();
        byte[] content0005 = response0005.getContentAsByteArray();
        byte[] content05 = response05.getContentAsByteArray();

        Assert.assertTrue(content0005.length >= contentDefault.length);
        Assert.assertTrue(content0005.length > content05.length);
    }

    /* in the current test dataset it dosn´t make any difference if 0.3 or 0.7 is applied as a generalization factor.
       * TODO: generate test data where we can test the difference between the different predefined sets.
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

       	String requestLowGen = request + "&env=gen_level:low";
       	MockHttpServletResponse responseLowGen = getAsServletResponse(requestLowGen);

       	String requestHigh = request + "&env=gen_level:high";
       	MockHttpServletResponse responseHigh = getAsServletResponse(requestHigh);

           byte[] contentLow = responseLowGen.getContentAsByteArray();
           byte[] contentHigh = responseHigh.getContentAsByteArray();

           // size of low generalisation is larger then with high generalisation
           Assert.assertTrue(contentLow.length > contentHigh.length);
       }*/

    @Test
    public void testBasicMvtGeneratorSkipSmallGeoms() throws Exception {
        String request =
                "wms?request=getmap&service=wms&version=1.1.1"
                        + "&format="
                        + MVT.MIME_TYPE
                        + "&layers="
                        + TEST_LINES.getPrefix()
                        + ":"
                        + TEST_LINES.getLocalPart()
                        + ","
                        + TEST_POINTS.getPrefix()
                        + ":"
                        + TEST_POINTS.getLocalPart()
                        + ","
                        + TEST_POLYGONS.getPrefix()
                        + ":"
                        + TEST_POLYGONS.getLocalPart()
                        + "&styles="
                        + STYLE_NAME
                        + ","
                        + STYLE_NAME
                        + ","
                        + STYLE_NAME
                        + "&height=256&width=256"
                        + "&bbox=1448023.063834379,6066042.5647115875,1457807.0034548815,6075826.50433209&srs=EPSG:3857&buffer=10";

        MockHttpServletResponse responseDefault = getAsServletResponse(request);

        String requestNoSkip = request + "&env=" + PARAM_SMALL_GEOM_THRESHOLD + ":-1";
        MockHttpServletResponse responseNoSkip = getAsServletResponse(requestNoSkip);

        String requestSkip10 = request + "&env=" + PARAM_SMALL_GEOM_THRESHOLD + ":0.01";
        MockHttpServletResponse responseSkip10 = getAsServletResponse(requestSkip10);

        byte[] contentDefault = responseDefault.getContentAsByteArray();
        byte[] contentNoSkip = responseNoSkip.getContentAsByteArray();
        byte[] contentSkip001 = responseSkip10.getContentAsByteArray();

        // size of default (0.05 Length/Area) is smaller then with no skipping
        Assert.assertTrue(contentDefault.length < contentNoSkip.length);
        // size of default (0.05 Length/Area) is smaller then with 0.01 (less skipped)
        Assert.assertTrue(contentDefault.length < contentSkip001.length);
    }

    @Test
    public void testEmptyResponseBehavior() throws Exception {
        String request =
                "wms?request=getmap&service=wms&version=1.1.1"
                        + "&format="
                        + MVT.MIME_TYPE
                        + "&layers="
                        + TEST_POLYGONS.getPrefix()
                        + ":"
                        + TEST_POLYGONS.getLocalPart()
                        + "&styles="
                        + STYLE_NAME
                        + "&height=256&width=256"
                        + "&bbox=1448023.063834379,6066042.5647115875,1457807.0034548815,6075826.50433209&srs=EPSG:3857&buffer=10";

        String requestEmpty = request + "&env=" + PARAM_SMALL_GEOM_THRESHOLD + ":1.000";
        MockHttpServletResponse responseEmpty = getAsServletResponse(requestEmpty);

        byte[] contentEmpty = responseEmpty.getContentAsByteArray();

        // every feature is filtered, returned content is a null / empty byte array
        Assert.assertEquals(0, contentEmpty.length);

        String requestNotEmpty =
                request
                        + "&env="
                        + PARAM_SMALL_GEOM_THRESHOLD
                        + ":1.000;"
                        + AVOID_EMPTY_PROTO
                        + ":true";
        MockHttpServletResponse responseNotEmpty = getAsServletResponse(requestNotEmpty);

        byte[] contentNotEmpty = responseNotEmpty.getContentAsByteArray();

        // every feature is filtered, returned content is a null / empty byte array
        Assert.assertTrue(contentNotEmpty.length > 0);
    }
}
