package org.geoserver.wms.mvt;

import org.geotools.api.filter.Filter;
import org.geotools.api.style.FeatureTypeStyle;
import org.geotools.api.style.Rule;
import org.geotools.api.style.Style;
import org.geotools.api.style.StyleFactory;
import org.geotools.factory.CommonFactoryFinder;

/** Small helpers to build styles for the scale / filter tests. */
final class MVTStyleTestUtils {

    static final StyleFactory STYLE_FACTORY = CommonFactoryFinder.getStyleFactory();

    private MVTStyleTestUtils() {}

    /** Creates a rule with the given scale range and filter, the filter may be null */
    static Rule rule(double minScaleDenominator, double maxScaleDenominator, Filter filter) {
        Rule rule = STYLE_FACTORY.createRule();
        rule.setMinScaleDenominator(minScaleDenominator);
        rule.setMaxScaleDenominator(maxScaleDenominator);
        rule.setFilter(filter);
        return rule;
    }

    /** Creates a style holding all given rules within a single feature type style */
    static Style style(Rule... rules) {
        Style style = STYLE_FACTORY.createStyle();
        if (rules.length > 0) {
            FeatureTypeStyle featureTypeStyle = STYLE_FACTORY.createFeatureTypeStyle();
            for (Rule rule : rules) {
                featureTypeStyle.rules().add(rule);
            }
            style.featureTypeStyles().add(featureTypeStyle);
        }
        return style;
    }
}
