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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.BuildBadgeAction;
import hudson.model.Cause;
import hudson.util.CopyOnWriteList;
import org.cloudbees.literate.jenkins.LiterateBranchBuild;
import org.cloudbees.literate.jenkins.LiterateBranchProject;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.interceptor.RequirePOST;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

/**
 * @author Stephen Connolly
 */
public class PromotionBranchBuildAction implements BuildBadgeAction {

    private static final Logger LOGGER = Logger.getLogger(PromotionBranchBuildAction.class.getName());
    @NonNull
    private final LiterateBranchBuild owner;
    /**
     * Per-process status.
     */
    private final CopyOnWriteList<PromotionStatus> statuses = new CopyOnWriteList<PromotionStatus>();

    public PromotionBranchBuildAction(@NonNull LiterateBranchBuild owner) {
        owner.getClass(); // throw NPE if null
        this.owner = owner;
    }

    public PromotionBranchBuildAction(@NonNull LiterateBranchBuild owner, PromotionStatus firstStatus) {
        this(owner);
        statuses.add(firstStatus);
    }

    public LiterateBranchBuild getOwner() {
        return owner;
    }

    /**
     * Gets the owning project.
     */
    public LiterateBranchProject getProject() {
        return owner.getProject();
    }

    /**
     * Checks if the given criterion is already promoted.
     */
    public boolean contains(PromotionProject process) {
        for (PromotionStatus s : statuses) {
            if (s.isFor(process)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the given criterion is already promoted.
     */
    public boolean contains(String name) {
        for (PromotionStatus s : statuses) {
            if (s.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Called when the build is qualified.
     */
    public synchronized boolean add(PromotionStatus status) throws IOException {
        for (PromotionStatus s : statuses) {
            if (s.getName().equals(status.getName())) {
                return false; // already qualified. noop.
            }
        }

        this.statuses.add(status);
        status.parent = this;
        owner.save();
        return true;
    }

    /**
     * Gets the read-only view of all the promotions that this build achieved.
     */
    @Exported
    public List<PromotionStatus> getPromotions() {
        List<PromotionStatus> result = new ArrayList<PromotionStatus>(statuses.getView());
        final PromotionJobProperty property =
                getProject() != null ? getProject().getProperty(PromotionJobProperty.class) : null;
        if (property != null) {
            Collections.sort(result, new Comparator<PromotionStatus>() {
                List<PromotionConfiguration> processes = property.getProcesses();

                public int compare(PromotionStatus o1, PromotionStatus o2) {
                    int i1 = Integer.MAX_VALUE;
                    int i2 = Integer.MAX_VALUE;
                    PromotionConfiguration c1 = o1.getPromotionProcess().getConfiguration();
                    PromotionConfiguration c2 = o2.getPromotionProcess().getConfiguration();
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
        }
        return result;
    }

    /**
     * Gets the read-only view of all the promotion builds that this build achieved
     * for a PromotionProcess.
     */
    public List<PromotionBuild> getPromotionBuilds(PromotionProject promotionProject) {
        List<PromotionBuild> filtered = new ArrayList<PromotionBuild>();

        for (PromotionStatus s : getPromotions()) {
            if (s.isFor(promotionProject)) {
                filtered.addAll(s.getPromotionBuilds());
            }
        }
        return filtered;
    }

    /**
     * Finds the {@link PromotionStatus} that has matching {@link PromotionStatus#getName()} value.
     * Or null if not found.
     */
    public PromotionStatus getPromotion(String name) {
        for (PromotionStatus s : statuses) {
            if (s.getName().equals(name)) {
                return s;
            }
        }
        return null;
    }

    public boolean hasPromotion() {
        return !statuses.isEmpty();
    }

    public boolean canPromote() {
        return this.getProject().hasPermission(PromotionBuild.PROMOTE);
    }

    /**
     * Gets list of {@link PromotionProject}s that are not yet attained.
     *
     * @return can be empty but never null.
     */
    public List<PromotionProject> getPendingPromotions() {
        final PromotionJobProperty pp = getProject().getProperty(PromotionJobProperty.class);
        if (pp == null) {
            return Collections.emptyList();
        }

        List<PromotionProject> r = new ArrayList<PromotionProject>();
        for (PromotionProject p : pp.getActiveItems()) {
            if (!contains(p)) {
                r.add(p);
            }
        }
        Collections.sort(r, new Comparator<PromotionProject>() {
            List<PromotionConfiguration> processes = pp.getProcesses();

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

        return r;
    }

    /**
     * Get the specified promotion process by name
     */
    public PromotionProject getPromotionProcess(String name) {
        PromotionJobProperty pp = getProject().getProperty(PromotionJobProperty.class);
        if (pp == null) {
            return null;
        }

        for (PromotionProject p : pp.getItems()) {
            if (p.getName().equals(name)) {
                return p;
            }
        }

        return null;
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

    private Object readResolve() {
        if (statuses == null) {
            return new PromotionBranchBuildAction(owner);
        }
        // resurrect the parent pointer when read from disk
        for (PromotionStatus s : statuses) {
            s.parent = this;
        }
        return this;
    }

//
// web methods
//

    /**
     * Binds {@link PromotionStatus} to URL hierarchy by its name.
     */
    public PromotionStatus getDynamic(String name, StaplerRequest req, StaplerResponse rsp) {
        return getPromotion(name);
    }

    /**
     * Force a promotion.
     */
    @RequirePOST
    public HttpResponse doForcePromotion(@QueryParameter("name") String name) throws IOException {

        this.getProject().checkPermission(PromotionBuild.PROMOTE);

        PromotionJobProperty pp = getProject().getProperty(PromotionJobProperty.class);
        if(pp==null)
            throw new IllegalStateException("This project doesn't have any promotion criteria set");

        PromotionProject p = pp.getItem(name);
        if(p==null)
            throw new IllegalStateException("This project doesn't have the promotion criterion called "+name);

        p.promote(owner,new Cause.UserCause(), new ManualPromotionBadge());

        return HttpResponses.redirectToDot();
    }
}
