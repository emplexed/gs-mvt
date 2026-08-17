package org.geoserver.wms.mvt;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.geotools.api.style.FeatureTypeStyle;
import org.geotools.api.style.Rule;
import org.geotools.api.style.Style;
import org.junit.Test;

/** Tests the scale denominator evaluation shared by the query and the encoder side. */
public class MVTStyleScaleSupportTest {

    @Test
    public void testRuleWithoutScaleDenominatorsAlwaysApplies() {
        Rule rule = MVTStyleTestUtils.rule(0, Double.POSITIVE_INFINITY, null);
        assertTrue(MVTStyleScaleSupport.ruleInScale(rule, 0));
        assertTrue(MVTStyleScaleSupport.ruleInScale(rule, 20000));
        assertTrue(MVTStyleScaleSupport.ruleInScale(rule, 1e12));
    }

    @Test
    public void testRuleIsEvaluatedAsRangeAndNotAsOr() {
        // overview rule, the combination that used to be reported as in scale at every zoom
        Rule rule = MVTStyleTestUtils.rule(20000, 10000000, null);
        assertFalse(MVTStyleScaleSupport.ruleInScale(rule, 5000));
        assertFalse(MVTStyleScaleSupport.ruleInScale(rule, 19999));
        assertTrue(MVTStyleScaleSupport.ruleInScale(rule, 500000));
        assertFalse(MVTStyleScaleSupport.ruleInScale(rule, 20000000));
    }

    @Test
    public void testMinIsInclusiveAndMaxIsExclusive() {
        Rule detail = MVTStyleTestUtils.rule(0, 20000, null);
        Rule overview = MVTStyleTestUtils.rule(20000, Double.POSITIVE_INFINITY, null);
        // exactly at the boundary only the overview rule applies, as in the SLD spec
        assertFalse(MVTStyleScaleSupport.ruleInScale(detail, 20000));
        assertTrue(MVTStyleScaleSupport.ruleInScale(overview, 20000));
        // just below it is the other way round
        assertTrue(MVTStyleScaleSupport.ruleInScale(detail, 19999.9));
        assertFalse(MVTStyleScaleSupport.ruleInScale(overview, 19999.9));
    }

    @Test
    public void testHasRuleInScale() {
        Style style = MVTStyleTestUtils.style(
                MVTStyleTestUtils.rule(0, 20000, null), MVTStyleTestUtils.rule(1000000, 10000000, null));
        assertTrue(MVTStyleScaleSupport.hasRuleInScale(style, 5000));
        assertTrue(MVTStyleScaleSupport.hasRuleInScale(style, 2000000));
        // in between both rules, nothing can be rendered
        assertFalse(MVTStyleScaleSupport.hasRuleInScale(style, 500000));
    }

    @Test
    public void testStyleWithoutRulesAndNullStyleDoNotRestrict() {
        assertTrue(MVTStyleScaleSupport.hasRuleInScale(null, 20000));
        Style empty = MVTStyleTestUtils.style();
        assertTrue(MVTStyleScaleSupport.hasRuleInScale(empty, 20000));
        // a style with feature type styles but without any rule must not skip the layer either
        Style emptyFeatureTypeStyle = MVTStyleTestUtils.style();
        FeatureTypeStyle featureTypeStyle = MVTStyleTestUtils.STYLE_FACTORY.createFeatureTypeStyle();
        emptyFeatureTypeStyle.featureTypeStyles().add(featureTypeStyle);
        assertTrue(MVTStyleScaleSupport.hasRuleInScale(emptyFeatureTypeStyle, 20000));
    }
}
