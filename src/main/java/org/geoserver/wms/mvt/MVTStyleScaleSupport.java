package org.geoserver.wms.mvt;

import org.geotools.api.style.FeatureTypeStyle;
import org.geotools.api.style.Rule;
import org.geotools.api.style.Style;

/**
 * Scale denominator evaluation shared by the query side ({@link StreamingMVTMap}) and the encoder
 * side ({@link MVTWriter}). Both classes used to carry their own copy of this test with differing
 * semantics, which made layers be queried at scales where none of their rules could ever contribute
 * a feature.
 *
 * <p>The semantics follow the SLD specification: the minimum scale denominator is inclusive, the
 * maximum scale denominator is exclusive. So for two adjacent rules {@code
 * maxScaleDenominator=20000} and {@code minScaleDenominator=20000} only the latter applies at a
 * scale denominator of exactly 20000.
 *
 * <p>Both bounds are lowered by the same small tolerance (as GeoServer's StreamingRenderer does for
 * the lower bound) so that a scale denominator which only misses a bound by floating point noise
 * still matches the rule that was meant for it. Shifting both bounds keeps adjacent rules a gapless
 * and non overlapping partition of the scale axis.
 */
final class MVTStyleScaleSupport {

    /** Same tolerance as the one used by GeoServer's StreamingRenderer */
    private static final double TOLERANCE = 1e-6;

    private MVTStyleScaleSupport() {}

    /**
     * Evaluates a single rule against the current scale denominator.
     *
     * @param rule the rule to be checked
     * @param currentScaleDenominator the current scale denominator
     * @return true if the rule applies at the current scale denominator, otherwise false
     */
    static boolean ruleInScale(Rule rule, double currentScaleDenominator) {
        return (rule.getMinScaleDenominator() - TOLERANCE) <= currentScaleDenominator
                && currentScaleDenominator < (rule.getMaxScaleDenominator() - TOLERANCE);
    }

    /**
     * Checks whether a style can contribute anything at all at the given scale denominator.
     *
     * @param style the style of the layer, may be null
     * @param currentScaleDenominator the current scale denominator
     * @return true if at least one rule of the style applies at the given scale denominator, or if
     *     the style is null or carries no rules at all (in that case the style does not restrict
     *     the features to be encoded)
     */
    static boolean hasRuleInScale(Style style, double currentScaleDenominator) {
        if (style == null) {
            return true;
        }
        boolean hasRules = false;
        for (FeatureTypeStyle featureTypeStyle : style.featureTypeStyles()) {
            for (Rule rule : featureTypeStyle.rules()) {
                hasRules = true;
                if (ruleInScale(rule, currentScaleDenominator)) {
                    return true;
                }
            }
        }
        return !hasRules;
    }
}
