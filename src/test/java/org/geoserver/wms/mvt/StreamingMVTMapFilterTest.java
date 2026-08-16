package org.geoserver.wms.mvt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.geotools.api.filter.Filter;
import org.geotools.api.filter.FilterFactory;
import org.geotools.api.filter.Or;
import org.geotools.api.style.Style;
import org.geotools.factory.CommonFactoryFinder;
import org.junit.Test;

/**
 * Tests the filter that is derived from the layer style and pushed down into the datastore query.
 */
public class StreamingMVTMapFilterTest {

    private static final FilterFactory FF = CommonFactoryFinder.getFilterFactory();

    private final StreamingMVTMap map = new StreamingMVTMap(null);

    private final Filter detailFilter = FF.equals(FF.property("frc"), FF.literal(3));
    private final Filter overviewFilter = FF.equals(FF.property("frc"), FF.literal(0));

    @Test
    public void testOnlyInScaleRulesContributeToTheQuery() {
        Style style =
                MVTStyleTestUtils.style(
                        MVTStyleTestUtils.rule(0, 20000, detailFilter),
                        MVTStyleTestUtils.rule(20000, 10000000, overviewFilter));
        // at a detail scale the overview filter must not be OR-ed into the query, that is what the
        // former "or" style scale test did
        assertEquals(detailFilter, map.getFeatureFilterFromStyle(style, FF, 5000));
        assertEquals(overviewFilter, map.getFeatureFilterFromStyle(style, FF, 500000));
    }

    @Test
    public void testSeveralInScaleRulesAreCombinedWithOr() {
        Style style =
                MVTStyleTestUtils.style(
                        MVTStyleTestUtils.rule(0, 20000, detailFilter),
                        MVTStyleTestUtils.rule(0, 20000, overviewFilter));
        Filter filter = map.getFeatureFilterFromStyle(style, FF, 5000);
        assertTrue(filter instanceof Or);
        assertEquals(2, ((Or) filter).getChildren().size());
    }

    @Test
    public void testInScaleRuleWithoutFilterMatchesEverything() {
        Style style =
                MVTStyleTestUtils.style(
                        MVTStyleTestUtils.rule(0, 20000, detailFilter),
                        MVTStyleTestUtils.rule(0, 20000, null));
        assertNull(map.getFeatureFilterFromStyle(style, FF, 5000));
    }

    @Test
    public void testOutOfScaleRuleWithoutFilterDoesNotMatchEverything() {
        // the rule without filter is out of scale, only the detail filter may be applied
        Style style =
                MVTStyleTestUtils.style(
                        MVTStyleTestUtils.rule(0, 20000, detailFilter),
                        MVTStyleTestUtils.rule(1000000, Double.POSITIVE_INFINITY, null));
        assertEquals(detailFilter, map.getFeatureFilterFromStyle(style, FF, 5000));
    }

    @Test
    public void testNoRuleInScaleExcludesEverything() {
        Style style =
                MVTStyleTestUtils.style(
                        MVTStyleTestUtils.rule(0, 20000, detailFilter),
                        MVTStyleTestUtils.rule(1000000, 10000000, overviewFilter));
        assertEquals(Filter.EXCLUDE, map.getFeatureFilterFromStyle(style, FF, 500000));
    }

    @Test
    public void testStyleWithoutRulesDoesNotRestrict() {
        assertNull(map.getFeatureFilterFromStyle(MVTStyleTestUtils.style(), FF, 5000));
    }
}
