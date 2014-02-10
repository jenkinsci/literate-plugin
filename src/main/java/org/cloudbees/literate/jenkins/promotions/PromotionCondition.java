package org.cloudbees.literate.jenkins.promotions;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import org.cloudbees.literate.jenkins.LiterateBranchBuild;

import java.io.Serializable;

/**
 * @author Stephen Connolly
 */
public class PromotionCondition extends AbstractDescribableImpl<PromotionCondition>
        implements ExtensionPoint, Serializable {

    /**
     * Checks if the promotion criteria is met.
     *
     * @param promotionProject The promotion process being evaluated for qualification
     * @param build            The build for which the promotion is considered.
     * @return non-null if the promotion condition is met. This object is then recorded so that
     *         we know how a build was promoted.
     *         {@code null} if otherwise, meaning it shouldn't be promoted.
     */
    @CheckForNull
    public PromotionBadge isMet(PromotionProject promotionProject, LiterateBranchBuild build) {
        return null;
    }

    public PromotionConditionDescriptor getDescriptor() {
        return (PromotionConditionDescriptor) super.getDescriptor();
    }


}
