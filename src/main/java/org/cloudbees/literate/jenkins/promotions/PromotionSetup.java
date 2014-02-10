package org.cloudbees.literate.jenkins.promotions;

import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import hudson.model.BuildListener;

/**
 * @author Stephen Connolly
 */
public abstract class PromotionSetup extends AbstractDescribableImpl<PromotionSetup> implements ExtensionPoint {

    @Override
    public PromotionSetupDescriptor getDescriptor() {
        return (PromotionSetupDescriptor) super.getDescriptor();
    }

    public abstract boolean setup(PromotionBuild promotionBuild, BuildListener listener) throws InterruptedException;
}
