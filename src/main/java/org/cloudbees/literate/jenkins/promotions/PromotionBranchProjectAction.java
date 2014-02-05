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

import hudson.model.AbstractBuild;
import hudson.model.Api;
import hudson.model.PermalinkProjectAction;
import hudson.model.ProminentProjectAction;
import org.cloudbees.literate.jenkins.LiterateBranchBuild;
import org.cloudbees.literate.jenkins.LiterateBranchProject;
import org.kohsuke.stapler.export.Exported;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

/**
 * @author Stephen Connolly
 */
public class PromotionBranchProjectAction implements ProminentProjectAction, PermalinkProjectAction {
    private static final Logger LOGGER = Logger.getLogger(PromotionBranchProjectAction.class.getName());

    //TODO externalize to a plugin property?
    private static final int SUMMARY_SIZE = 10;
    private final PromotionJobProperty property;

    public PromotionBranchProjectAction(PromotionJobProperty property) {
        this.property = property;
    }

    public Api getApi() {
        return new Api(this);
    }

    @Exported
    public List<PromotionProject> getProcesses() {
        List<PromotionProject> result = new ArrayList<PromotionProject>(property.getItems());
        Collections.sort(result, new Comparator<PromotionProject>() {
            List<PromotionConfiguration> processes = property.getProcesses();

            public int compare(PromotionProject o1, PromotionProject o2) {
                int i1 = Integer.MAX_VALUE;
                int i2 = Integer.MAX_VALUE;
                PromotionConfiguration c1 = o1.getConfiguration();
                PromotionConfiguration c2 = o2.getConfiguration();
                int i = 0;
                for (PromotionConfiguration c : processes) {
                    if (c.equals(c1)) {
                        i1 = i;
                    } else if (c.equals(c2)) {
                        i2 = i;
                    } else if (i1 != Integer.MAX_VALUE && i2 != Integer.MAX_VALUE) {
                        break;
                    }
                    i++;
                }
                return Integer.compare(i1, i2);
            }
        });
        return result;
    }

    public PromotionProject getProcess(String name) {
        for (PromotionProject pp : getProcesses()) {
            if (pp.getName().equals(name)) {
                return pp;
            }
        }
        return null;
    }

    public AbstractBuild<?, ?> getLatest(PromotionProject p) {
        List<PromotionBuild> list = getPromotions(p);
        return list.size() > 0 ? list.get(0) : null;
    }

    /**
     * Finds the last promoted build under the given criteria.
     */
    public AbstractBuild<?, ?> getLatest(String name) {
        List<PromotionBuild> list = getPromotions(getProcess(name));
        return list.size() > 0 ? list.get(0) : null;
    }

    public List<PromotionBuild> getPromotions(PromotionProject promotionProject) {
        List<PromotionBuild> list = new ArrayList<PromotionBuild>();
        for (LiterateBranchBuild build : property.getOwner().getBuilds()) {
            PromotionBranchBuildAction a = build.getAction(PromotionBranchBuildAction.class);
            if (a != null && a.contains(promotionProject)) {
                list.addAll(a.getPromotionBuilds(promotionProject));
            }
        }
        Collections.sort(list);
        return list;
    }

    /**
     * returns the summary of the latest promotions for a promotion process.
     */
    public List<PromotionBuild> getPromotionsSummary(PromotionProject promotionProject) {
        List<PromotionBuild> promotionList = this.getPromotions(promotionProject);
        if (promotionList.size() > SUMMARY_SIZE) {
            return promotionList.subList(0, SUMMARY_SIZE);
        } else {
            return promotionList;
        }
    }

    public List<Permalink> getPermalinks() {
        List<Permalink> r = new ArrayList<Permalink>();
        for (PromotionProject pp : property.getActiveItems()) {
            r.add(pp.asPermalink());
        }
        return r;
    }

    public String getIconFileName() {
        return "star.png";
    }

    public String getDisplayName() {
        return "Promotion Status";
    }

    public String getUrlName() {
        return "promotion";
    }

    public PromotionJobProperty getJobProperty() {
        return property;
    }

    public LiterateBranchProject getOwner() {
        return property.getOwner();
    }
}
