package org.geoserver;

import java.util.Collections;

import javax.xml.namespace.QName;

import org.geoserver.data.test.MockData;
import org.geoserver.data.test.SystemTestData;
import org.geoserver.wms.WMSTestSupport;
import org.geoserver.wms.mvt.MVTTest;

public class AbstractMVTTest extends WMSTestSupport {

	public static QName TEST_LINES = new QName(MockData.CITE_URI, "test_lines",
			MockData.CITE_PREFIX);
	public static QName TEST_POINTS = new QName(MockData.CITE_URI,
			"test_points", MockData.CITE_PREFIX);
	public static QName TEST_POLYGONS = new QName(MockData.CITE_URI,
			"test_polygons", MockData.CITE_PREFIX);
	public static String STYLE_NAME = "test_pbf_filter";

	@Override
	protected void onSetUp(SystemTestData testData) throws Exception {
		super.onSetUp(testData);
		testData.addStyle(STYLE_NAME, "./test_pbf_filter.sld", getClass(),
				getCatalog());
		testData.addVectorLayer(TEST_LINES, Collections.EMPTY_MAP,
				"test_lines.properties", MVTTest.class, getCatalog());
		testData.addVectorLayer(TEST_POINTS, Collections.EMPTY_MAP,
				"test_points.properties", MVTTest.class, getCatalog());
		testData.addVectorLayer(TEST_POLYGONS, Collections.EMPTY_MAP,
				"test_polygons.properties", MVTTest.class, getCatalog());
	}
}
