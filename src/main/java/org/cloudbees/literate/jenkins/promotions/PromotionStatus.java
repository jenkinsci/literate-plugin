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

import hudson.EnvVars;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.Cause;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.Result;
import hudson.util.Iterators;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.cloudbees.literate.jenkins.LiterateBranchBuild;
import org.cloudbees.literate.jenkins.LiterateBranchProject;
import org.cloudbees.literate.jenkins.promotions.conditions.ManualCondition;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.concurrent.Future;
import java.util.logging.Logger;

/**
 * @author Stephen Connolly
 */
@ExportedBean
public class PromotionStatus {
    private static final Logger LOGGER = Logger.getLogger(PromotionStatus.class.getName());
    /**
     * When did the build qualify for a promotion?
     */
    public final Calendar timestamp = new GregorianCalendar();
    /**
     * Matches with {@link PromotionProject#name}.
     */
    private final String name;
    private final PromotionBadge[] badges;
    /*package*/ transient PromotionBranchBuildAction parent;
    /**
     * If the build is successfully promoted, the build number of {@link PromotionConfiguration}
     * that represents that record.
     * <p/>
     * -1 to indicate that the promotion was not successful yet.
     */
    private int promotion = -1;
    /**
     * Bulid numbers of {@link PromotionConfiguration}s that are attempted.
     * If {@link PromotionConfiguration} fails, this field can have multiple values.
     * Sorted in the ascending order.
     */
    private List<Integer> promotionAttempts = new ArrayList<Integer>();

    public PromotionStatus(PromotionProject process, Collection<? extends PromotionBadge> badges) {
        this.name = process.getName();
        this.badges = badges.toArray(new PromotionBadge[badges.size()]);
    }

    @Exported
    public String getName() {
        return name;
    }

    /**
     * Gets the parent {@link PromotionStatus} that owns this object.
     */
    public PromotionBranchBuildAction getParent() {
        return parent;
    }

    /**
     * Gets the {@link PromotionProject} that this object deals with.
     */
    @Exported
    public PromotionProject getPromotionProcess() {
        assert parent != null : name;
        LiterateBranchProject project = parent.getOwner().getProject();
        assert project != null : parent;
        PromotionJobProperty
                jp = project.getProperty(PromotionJobProperty.class);
        if (jp == null) {
            return null;
        }
        return jp.getItem(name);
    }

    /**
     * Gets the icon that should represent this promotion (that is potentially attempted but failed.)
     */
    public String getIcon(String size) {
        String baseName;

        PromotionProject p = getPromotionProcess();
        if (p == null) {
            // promotion process undefined (perhaps deleted?). fallback to the default icon
            baseName = PromotionProject.ICON_NAMES.get(0);
        } else {
            PromotionBuild l = getLast();
            if (l != null && (l.isBuilding() || l.getResult() == null)) {
                baseName = p.getEmptyIcon();
            } else if (l != null && l.getResult() != Result.SUCCESS) {
                if (l.getResult() == Result.NOT_BUILT) {
                    baseName = p.getEmptyIcon();
                } else {
                    return Jenkins.RESOURCE_PATH + "/images/" + size + "/error.png";
                }
            } else {
                baseName = p.getIcon();
            }
        }
        return Jenkins.RESOURCE_PATH + "/plugin/literate/images/" + size + "/" + baseName + ".png";
    }

    /**
     * Gets the build that was qualified for a promotion.
     */
    public LiterateBranchBuild getTarget() {
        return getParent().getOwner();
    }

    /**
     * Called by {@link PromotionConfiguration} to allow status to contribute environment variables.
     *
     * @param build The calling build. Never null.
     * @param env   Environment variables should be added to this map.
     */
    public void buildEnvVars(AbstractBuild<?, ?> build, EnvVars env) {
        for (PromotionBadge badge : badges) {
            badge.buildEnvVars(build, env);
        }
    }

    /**
     * Gets the string that says how long since this promotion had happened.
     *
     * @return string like "3 minutes" "1 day" etc.
     */
    public String getTimestampString() {
        long duration = new GregorianCalendar().getTimeInMillis() - timestamp.getTimeInMillis();
        return Util.getTimeSpanString(duration);
    }

    /**
     * Gets the string that says how long did it toook for this build to be promoted.
     */
    public String getDelayString(AbstractBuild<?, ?> owner) {
        long duration = timestamp.getTimeInMillis() - owner.getTimestamp().getTimeInMillis() - owner.getDuration();
        return Util.getTimeSpanString(duration);
    }

    public boolean isFor(PromotionProject process) {
        return process != null && process.getName().equals(this.name);
    }

    /**
     * Returns the {@link PromotionConfiguration} object that represents the successful promotion.
     *
     * @return null if the promotion has never been successful, or if it was but
     *         the record is already lost.
     */
    public PromotionBuild getSuccessfulPromotion(PromotionJobProperty jp) {
        if (promotion >= 0) {
            PromotionProject p = jp.getItem(name);
            if (p != null) {
                return p.getBuildByNumber(promotion);
            }
        }
        return null;
    }

    /**
     * Returns true if the promotion was successfully completed.
     */
    public boolean isPromotionSuccessful() {
        return promotion >= 0;
    }

    /**
     * Returns true if at least one {@link PromotionConfiguration} activity is attempted.
     * False if none is executed yet (this includes the case where it's in the queue.)
     */
    public boolean isPromotionAttempted() {
        return !promotionAttempts.isEmpty();
    }

    /**
     * Returns true if the promotion for this is pending in the queue,
     * waiting to be executed.
     */
    public boolean isInQueue() {
        PromotionProject p = getPromotionProcess();
        return p != null && p.isInQueue(getTarget());
    }

    /**
     * Gets the badges indicating how did a build qualify for a promotion.
     */
    @Exported
    public List<PromotionBadge> getBadges() {
        return Arrays.asList(badges);
    }

    /**
     * Called when a new promotion attempts for this build starts.
     */
    /*package*/ void addPromotionAttempt(PromotionBuild p) {
        promotionAttempts.add(p.getNumber());
    }

    /**
     * Called when a promotion succeeds.
     */
    /*package*/ void onSuccessfulPromotion(PromotionBuild p) {
        promotion = p.getNumber();
    }

    //
    // web bound methods
    //

    /**
     * Gets the last successful {@link PromotionConfiguration}.
     */
    public PromotionBuild getLastSuccessful() {
        PromotionProject p = getPromotionProcess();
        for (Integer n : Iterators.reverse(promotionAttempts)) {
            PromotionBuild b = p.getBuildByNumber(n);
            if (b != null && b.getResult() == Result.SUCCESS) {
                return b;
            }
        }
        return null;
    }

    /**
     * Gets the last successful {@link PromotionConfiguration}.
     */
    public PromotionBuild getLastFailed() {
        PromotionProject p = getPromotionProcess();
        for (Integer n : Iterators.reverse(promotionAttempts)) {
            PromotionBuild b = p.getBuildByNumber(n);
            if (b != null && b.getResult() != Result.SUCCESS) {
                return b;
            }
        }
        return null;
    }

    public PromotionBuild getLast() {
        PromotionProject p = getPromotionProcess();
        for (Integer n : Iterators.reverse(promotionAttempts)) {
            PromotionBuild b = p.getBuildByNumber(n);
            if (b != null) {
                return b;
            }
        }
        return null;
    }

    /**
     * Gets all the promotion builds.
     */
    @Exported
    public List<PromotionBuild> getPromotionBuilds() {
        List<PromotionBuild> builds = new ArrayList<PromotionBuild>();
        PromotionProject p = getPromotionProcess();
        if (p != null) {
            for (Integer n : Iterators.reverse(promotionAttempts)) {
                PromotionBuild b = p.getBuildByNumber(n);
                if (b != null) {
                    builds.add(b);
                }
            }
        }
        return builds;
    }

    /**
     * Gets the promotion build by build number.
     *
     * @param number build number
     * @return promotion build
     */
    public PromotionBuild getPromotionBuild(int number) {
        PromotionProject p = getPromotionProcess();
        return p.getBuildByNumber(number);
    }

    /**
     * Schedules a new build.
     */
    public void doBuild(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        if (!getTarget().hasPermission(PromotionBuild.PROMOTE)) {
            return;
        }
        JSONObject formData = req.getSubmittedForm();

        List<ParameterValue> paramValues = null;
        if (formData != null) {
            paramValues = new ArrayList<ParameterValue>();
            ManualCondition manualCondition = (ManualCondition) getPromotionProcess().getPromotionCondition(
                    ManualCondition.class.getName());
            if (manualCondition != null) {
                List<ParameterDefinition> parameterDefinitions =
                        manualCondition.getParameterDefinitions(getPromotionProcess(), getTarget());
                if (!parameterDefinitions.isEmpty()) {
                    JSONArray a = JSONArray.fromObject(formData.get("parameter"));

                    for (Object o : a) {
                        JSONObject jo = (JSONObject) o;
                        String name = jo.getString("name");

                        ParameterDefinition d = ManualCondition.getParameterDefinition(parameterDefinitions, name);
                        if (d == null) {
                            throw new IllegalArgumentException("No such parameter definition: " + name);
                        }

                        paramValues.add(d.createValue(req, jo));
                    }
                }
            }
        }
        if (paramValues == null) {
            paramValues = new ArrayList<ParameterValue>();
        }
        Future<PromotionBuild> f =
                getPromotionProcess().scheduleBuild2(getTarget(), new Cause.UserCause(), paramValues);
        if (f == null) {
            LOGGER.warning("Failing to schedule the promotion of " + getTarget());
        }
        // TODO: we need better visual feed back so that the user knows that the build happened.
        rsp.forwardToPreviousPage(req);
    }

    public static class ComparatorImpl implements Comparator<PromotionStatus> {
        Iterable<PromotionConfiguration> processes;

        public ComparatorImpl(Iterable<PromotionConfiguration> processes) {
            this.processes = processes;
        }

        public int compare(PromotionStatus o1, PromotionStatus o2) {
            return PromotionConfiguration.compare(
                    o1.getPromotionProcess().getConfiguration(),
                    o2.getPromotionProcess().getConfiguration(),
                    processes
            );
        }
    }
}
