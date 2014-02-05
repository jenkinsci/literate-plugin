/*
 * The MIT License
 *
 * Copyright (c) 2009-2014, Kohsuke Kawaguchi, Stephen Connolly, CloudBees, Inc., and others
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.cloudbees.literate.jenkins.promotions;

import hudson.model.BuildBadgeAction;
import hudson.model.InvisibleAction;
import jenkins.model.Jenkins;
import org.cloudbees.literate.jenkins.LiterateBranchBuild;
import org.cloudbees.literate.jenkins.LiterateBranchProject;
import org.cloudbees.literate.jenkins.LiterateMultibranchProject;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

import java.util.logging.Logger;

/**
 * Remembers what build it's promoting. Attached to {@link PromotionBuild}.
 *
 * @author Kohsuke Kawaguchi
 */
public class PromotionTargetAction extends InvisibleAction implements BuildBadgeAction {
    private static final Logger LOGGER = Logger.getLogger(PromotionTargetAction.class.getName());
    private final String jobName;
    private final String branchName;
    private final int number;

    public PromotionTargetAction(LiterateBranchBuild build) {
        jobName = build.getParent().getParent().getFullName();
        branchName = build.getParent().getName();
        number = build.getNumber();
    }

    public LiterateBranchBuild resolve() {
        LiterateMultibranchProject j =
                Jenkins.getInstance().getItemByFullName(jobName, LiterateMultibranchProject.class);
        if (j == null) {
            return null;
        }
        LiterateBranchProject p = j.getBranch(branchName);
        if (p == null) {
            return null;
        }
        return p.getBuildByNumber(number);
    }

    public LiterateBranchBuild resolve(PromotionProject parent) {
        LiterateBranchProject j = parent.getOwner();
        if (j == null) {
            return null;
        }
        return j.getBuildByNumber(number);
    }

    public LiterateBranchBuild resolve(PromotionBuild parent) {
        return resolve(parent.getParent());
    }

    public LiterateBranchBuild resolveFromRequest() {
        StaplerRequest current = Stapler.getCurrentRequest();
        if (current == null) return null;
        PromotionBuild promotionBuild = current.findAncestorObject(PromotionBuild.class);
        if (promotionBuild == null) return null;
        return resolve(promotionBuild);
    }

    public String getTargetDisplayName() {
        return "#"+number;
    }

    public int getNumber() {
        return number;
    }
}
