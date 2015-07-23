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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.DependencyGraph;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.ItemGroup;
import hudson.model.JDK;
import hudson.model.Job;
import hudson.model.Label;
import hudson.model.ParameterValue;
import hudson.model.PermalinkProjectAction;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.Saveable;
import hudson.model.TaskListener;
import hudson.scm.SCM;
import hudson.tasks.Publisher;
import hudson.util.DescribableList;
import jenkins.branch.Branch;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMRevisionAction;
import jenkins.scm.api.SCMSource;
import net.jcip.annotations.Immutable;
import org.apache.commons.lang.StringUtils;
import org.cloudbees.literate.jenkins.BuildEnvironment;
import org.cloudbees.literate.jenkins.LiterateBranchBuild;
import org.cloudbees.literate.jenkins.LiterateBranchProject;
import org.cloudbees.literate.jenkins.promotions.conditions.ManualCondition;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.logging.Logger;

/**
 * @author Stephen Connolly
 */
@Immutable
public class PromotionProject
        extends AbstractProject<PromotionProject, PromotionBuild>
        implements Saveable, Describable<PromotionProject> {
    private static final Logger LOGGER = Logger.getLogger(PromotionProject.class.getName());

    /*package*/ static final List<String> ICON_NAMES = Arrays.asList(
            "star-gold",
            "star-silver",
            "star-blue",
            "star-red",
            "star-green",
            "star-purple",
            "star-gold-w",
            "star-silver-w",
            "star-blue-w",
            "star-red-w",
            "star-green-w",
            "star-purple-w"
    );

    @NonNull
    private final PromotionConfiguration configuration;

    private transient volatile String iconName;

    public PromotionProject(ItemGroup owner, PromotionConfiguration configuration) {
        super(owner, configuration.getName());
        this.configuration = configuration;
    }

    @Override
    public String getPronoun() {
        return "Promotion";
    }

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    public String getDisplayName() {
        return StringUtils.defaultIfBlank(getConfiguration().getDisplayName(), name);
    }

    @CheckForNull
    public String getEnvironment() {
        return configuration.getEnvironment();
    }

    @NonNull
    public PromotionConfiguration getConfiguration() {
        PromotionConfiguration c = getParent().getProcess(configuration.getName());
        return c == null ? configuration : c;
    }

    /**
     * Returns the root project value.
     *
     * @return the root project value.
     */
    @Override
    public LiterateBranchProject getRootProject() {
        return getParent().getOwner();
    }

    @Override
    public PromotionJobProperty getParent() {
        return (PromotionJobProperty) super.getParent();
    }

    /**
     * Gets the owner {@link LiterateBranchProject} that configured {@link PromotionJobProperty} as
     * a job property.
     */
    public LiterateBranchProject getOwner() {
        return getParent().getOwner();
    }

    /**
     * Get the promotion condition by referencing it fully qualified class name
     */
    public PromotionCondition getPromotionCondition(String promotionClassName) {
        for (PromotionCondition condition : getConfiguration().getConditions()) {
            if (condition.getClass().getName().equals(promotionClassName)) {
                return condition;
            }
        }

        return null;
    }

    @Override
    public DescribableList<Publisher, Descriptor<Publisher>> getPublishersList() {
        return new DescribableList<Publisher, Descriptor<Publisher>>(this);
    }

    @Override
    protected Class<PromotionBuild> getBuildClass() {
        return PromotionBuild.class;
    }

    @Override
    public SCM getScm() {
        return getParent().getOwner().getScm();
    }

    @Override
    public boolean checkout(AbstractBuild build, Launcher launcher, BuildListener listener, File changelogFile)
            throws IOException, InterruptedException {
        if (build instanceof PromotionBuild) {
            LiterateBranchBuild parentBuild = ((PromotionBuild) build).getTarget();
            SCMRevisionAction hashAction = parentBuild != null
                    ? parentBuild.getAction(SCMRevisionAction.class)
                    : null;
            if (hashAction != null) {
                Branch branch = parentBuild.getParent().getBranch();
                SCMSource source = parentBuild.getParent().getParent().getSCMSource(branch.getSourceId());
                if (source != null) {
                    SCMRevision revisionHash = hashAction.getRevision();
                    SCMHead head = revisionHash.getHead();
                    SCM scm = source.build(head, revisionHash);

                    FilePath workspace = build.getWorkspace();
                    assert workspace != null : "we are in a build so must have a workspace";
                    workspace.mkdirs();

                    boolean r = scm.checkout(build, launcher, workspace, listener, changelogFile);
                    if (r) {
                        // Only calcRevisionsFromBuild if checkout was successful. Note that modern SCM implementations
                        // won't reach this line anyway, as they throw AbortExceptions on checkout failure.
                        calcPollingBaseline(build, launcher, listener);
                    }
                    return r;
                }
            }
        }
        listener.getLogger().println("Could not check out exact revision, falling back to current");
        return super.checkout(build, launcher, listener, changelogFile);
    }

    /**
     * We all hate monkey patching!
     */
    private void calcPollingBaseline(AbstractBuild build, Launcher launcher, TaskListener listener)
            throws IOException, InterruptedException {
        try {
            Method superMethod = AbstractProject.class
                    .getDeclaredMethod("calcPollingBaseline", AbstractBuild.class, Launcher.class, TaskListener.class);
            superMethod.setAccessible(true);
            superMethod.invoke(this, build, launcher, listener);
        } catch (NoSuchMethodException e) {
            // TODO remove screaming ugly hack when method is exposed in base Jenkins
        } catch (InvocationTargetException e) {
            // TODO remove screaming ugly hack when method is exposed in base Jenkins
        } catch (IllegalAccessException e) {
            // TODO remove screaming ugly hack when method is exposed in base Jenkins
        }
    }

    /**
     * Gets the textual representation of the assigned label as it was entered by the user.
     */
    @Override
    public String getAssignedLabelString() {
        // TODO properlt generate a label string
        Label assignedLabel = getAssignedLabel();
        return assignedLabel == null ? null : assignedLabel.getDisplayName();
    }

    @Override
    public Label getAssignedLabel() {
        Set<String> environment = configuration.asEnvironments();
        return environment == null
                ? null
                : getOwner().getParent().getEnvironmentMapper().getLabel(new BuildEnvironment(environment));
    }

    @Override
    public JDK getJDK() {
        return getOwner().getJDK();
    }

    /**
     * Get the icon name, without the extension. It will always return a non null
     * and non empty string, as <code>"star-gold"</code> is used for compatibility
     * for older promotions configurations.
     *
     * @return the icon name
     */
    public String getIcon() {
        String iconName = this.iconName;
        if (StringUtils.isBlank(iconName)) {
            int index = 0;
            iconName = "star-unknown";
            String name = configuration.getName();
            for (PromotionConfiguration p : getParent().getProcesses()) {
                if (PromotionConfiguration.nameEquals(name, p.getName())) {
                    iconName = ICON_NAMES.get(index % ICON_NAMES.size());
                    break;
                }
                index++;
            }
            this.iconName = iconName;
        }
        return iconName;
    }

    public String getEmptyIcon() {
        String baseName = getIcon();
        baseName = (baseName.endsWith("-w") ? baseName.substring(0, baseName.length() - 2) : baseName) + "-e";
        return baseName;
    }

    /**
     * Get the badges of conditions that were passed for this promotion for the build
     */
    public List<PromotionBadge> getMetQualifications(LiterateBranchBuild build) {
        List<PromotionBadge> badges = new ArrayList<PromotionBadge>();
        for (PromotionCondition cond : getConfiguration().getConditions()) {
            PromotionBadge b = cond.isMet(this, build);

            if (b != null) {
                badges.add(b);
            }
        }
        return badges;
    }

    /**
     * Get the conditions that have not been met for this promotion for the build
     */
    public List<PromotionCondition> getUnmetConditions(LiterateBranchBuild build) {
        List<PromotionCondition> unmetConditions = new ArrayList<PromotionCondition>();

        for (PromotionCondition cond : getConfiguration().getConditions()) {
            if (cond.isMet(this, build) == null) {
                unmetConditions.add(cond);
            }
        }

        return unmetConditions;
    }

    /**
     * Checks if all the conditions to promote a build is met.
     *
     * @return null if promotion conditions are not met.
     *         otherwise returns a list of badges that record how the promotion happened.
     */
    public PromotionStatus isMet(LiterateBranchBuild build) {
        List<PromotionBadge> badges = new ArrayList<PromotionBadge>();
        for (PromotionCondition cond : getConfiguration().getConditions()) {
            PromotionBadge b = cond.isMet(this, build);
            if (b == null) {
                return null;
            }
            badges.add(b);
        }
        return new PromotionStatus(this, badges);
    }

    /**
     * Checks if the build is promotable, and if so, promote it.
     *
     * @return null if the build was not promoted, otherwise Future that kicks in when the build is completed.
     */
    public Future<PromotionBuild> considerPromotion(LiterateBranchBuild build) throws IOException {
        if (!isActive()) {
            return null;    // not active
        }

        PromotionBranchBuildAction a = build.getAction(PromotionBranchBuildAction.class);

        // if it's already promoted, no need to do anything.
        if (a != null && a.contains(this)) {
            return null;
        }

        LOGGER.fine("Considering the promotion of " + build + " via " + getName());
        PromotionStatus qualification = isMet(build);
        if (qualification == null) {
            return null; // not this time
        }

        LOGGER.fine("Promotion condition of " + build + " is met: " + qualification);
        Future<PromotionBuild> f = promote(build, new Cause.UserCause(), qualification); // TODO: define promotion cause
        if (f == null) {
            LOGGER.warning(build + " qualifies for a promotion but the queueing failed.");
        }
        return f;
    }

    /**
     * Promote the given build by using the given qualification.
     *
     * @param cause Why the build is promoted?
     * @return Future to track the completion of the promotion.
     */
    public Future<PromotionBuild> promote(LiterateBranchBuild build, Cause cause, PromotionStatus qualification)
            throws IOException {
        PromotionBranchBuildAction a = build.getAction(PromotionBranchBuildAction.class);
        // build is qualified for a promotion.
        if (a != null) {
            a.add(qualification);
        } else {
            build.addAction(new PromotionBranchBuildAction(build, qualification));
            build.save();
        }

        // schedule promotion activity.
        return scheduleBuild2(build, cause);
    }

    /**
     * @deprecated You need to be using {@link #scheduleBuild(LiterateBranchBuild)}
     */
    public boolean scheduleBuild() {
        return super.scheduleBuild();
    }

    public boolean scheduleBuild(LiterateBranchBuild build) {
        return scheduleBuild(build, new Cause.LegacyCodeCause());
    }

    /**
     * @deprecated Use {@link #scheduleBuild2(LiterateBranchBuild, Cause)}
     */
    public boolean scheduleBuild(LiterateBranchBuild build, Cause cause) {
        return scheduleBuild2(build, cause) != null;
    }

    public Future<PromotionBuild> scheduleBuild2(LiterateBranchBuild build, Cause cause, List<ParameterValue> params) {
        assert build.getProject() == getOwner();

        // Get the parameters, if any, used in the target build and make these
        // available as part of the promotion steps
        List<Action> actions = new ArrayList<Action>();
        PromotionBuild.buildParametersAction(actions, build, params);
        actions.add(new PromotionTargetAction(build));

        // remember what build we are promoting
        return super.scheduleBuild2(0, cause, actions.toArray(new Action[actions.size()]));
    }

    public Future<PromotionBuild> scheduleBuild2(LiterateBranchBuild build, Cause cause) {
        List<ParameterValue> params = new ArrayList<ParameterValue>();
        List<ManualCondition.ManualApproval> approvals = build.getActions(ManualCondition.ManualApproval.class);
        if (approvals != null) {
            for (ManualCondition.ManualApproval approval : approvals) {
                params.addAll(approval.badge.getParameterValues());
            }
        }

        // remember what build we are promoting
        return scheduleBuild2(build, cause, params);
    }

    public boolean isInQueue(LiterateBranchBuild build) {
        Jenkins j = Jenkins.getInstance();
        if (j == null) {
            return false;
        }
        for (Queue.Item item : j.getQueue().getItems(this)) {
            if (item.getAction(PromotionTargetAction.class).resolve(this) == build) {
                return true;
            }
        }
        return false;
    }

    //
    // these are dummy implementations to implement abstract methods.
    // need to think about what the implications are.
    //
    public boolean isFingerprintConfigured() {
        throw new UnsupportedOperationException();
    }

    protected void buildDependencyGraph(DependencyGraph graph) {
        throw new UnsupportedOperationException();
    }

    public PermalinkProjectAction.Permalink asPermalink() {
        return new PermalinkProjectAction.Permalink() {
            @Override
            public String getDisplayName() {
                return Messages.LiteratePromotionsBranchProperty_PermalinkDisplayName(
                        PromotionProject.this.getDisplayName());
            }

            @Override
            public String getId() {
                return PromotionProject.this.getName();
            }

            @Override
            public Run<?, ?> resolve(Job<?, ?> job) {
                String id = getId();
                for (Run<?, ?> build : job.getBuilds()) {
                    PromotionBranchBuildAction a = build.getAction(PromotionBranchBuildAction.class);
                    if (a != null && a.contains(id)) {
                        return build;
                    }
                }
                return null;
            }
        };
    }

    public DescriptorImpl getDescriptor() {
        Jenkins j = Jenkins.getInstance();
        if (j == null) {
            throw new IllegalStateException(); // TODO 1.590+ getActiveInstance
        }
        return (DescriptorImpl) j.getDescriptorOrDie(getClass());
    }

    @Override
    public String getShortUrl() {
        // Must be overridden since PromotionJobProperty.getUrlChildPrefix is "" not "process" as you might expect
        // (also see e50f0f5 in 1.519)
        return "process/" + Util.rawEncode(getName()) + '/';
    }

    public boolean isActive() {
        return !isDisabled();
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<PromotionProject> {

        @Override
        public String getDisplayName() {
            return Messages.PromotionProject_displayName();
        }
    }

    public static class ComparatorImpl implements Comparator<PromotionProject> {
        private final Iterable<PromotionConfiguration> processes;

        public ComparatorImpl(Iterable<PromotionConfiguration> processes) {
            this.processes = processes;
        }

        public int compare(PromotionProject o1, PromotionProject o2) {
            return PromotionConfiguration.compare(o1.getConfiguration(), o2.getConfiguration(), processes);
        }
    }
}
