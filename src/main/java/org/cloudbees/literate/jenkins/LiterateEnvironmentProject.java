/*
 * The MIT License
 *
 * Copyright (c) 2013, CloudBees, Inc.
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
package org.cloudbees.literate.jenkins;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.DependencyGraph;
import hudson.model.Descriptor;
import hudson.model.Executor;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.JDK;
import hudson.model.Label;
import hudson.model.ParametersAction;
import hudson.model.Project;
import hudson.model.Queue;
import hudson.model.SCMedItem;
import hudson.model.TaskListener;
import hudson.scm.SCM;
import hudson.tasks.BuildWrapper;
import hudson.tasks.Builder;
import hudson.tasks.LogRotator;
import hudson.tasks.Publisher;
import hudson.tools.ToolInstallation;
import hudson.util.AlternativeUiTextProvider;
import hudson.util.DescribableList;
import jenkins.branch.Branch;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMRevisionAction;
import jenkins.scm.api.SCMSource;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * Represents a specific environment defined for the literate build.
 *
 * @author Stephen Connolly
 */
public class LiterateEnvironmentProject extends Project<LiterateEnvironmentProject, LiterateEnvironmentBuild>
        implements SCMedItem,
        Queue.NonBlockingTask {
    /**
     * Our environment.
     */
    @CheckForNull
    private transient BuildEnvironment environment;

    /**
     * Constructor.
     *
     * @param parent      the parent.
     * @param environment the environment.
     */
    public LiterateEnvironmentProject(@NonNull LiterateBranchProject parent, @NonNull BuildEnvironment environment) {
        super(parent, environment.getName()); // todo get proper name
        this.environment = environment;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onLoad(ItemGroup<? extends Item> parent, String name) throws IOException {
        super.onLoad(parent, name);    // todo proper name
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isConcurrentBuild() {
        return getParent().isConcurrentBuild();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setConcurrentBuild(boolean b) throws IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the environment.
     *
     * @return the environment.
     */
    @NonNull
    public BuildEnvironment getEnvironment() {
        if (environment == null) {
            environment = BuildEnvironment.fromString(getName());
        }
        return environment;
    }

    /**
     * Sets the environment.
     *
     * @param environment the environment.
     */
    public void setEnvironment(@NonNull BuildEnvironment environment) {
        this.environment = environment;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNextBuildNumber() {
        AbstractBuild<?, ?> lb = getParent().getLastBuild();

        while (lb != null && lb.isBuilding()) {
            lb = lb.getPreviousBuild();
        }
        if (lb == null) {
            return 0;
        }

        int n = lb.getNumber() + 1;

        lb = getLastBuild();
        if (lb != null) {
            n = Math.max(n, lb.getNumber() + 1);
        }

        return n;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized int assignBuildNumber() throws IOException {
        int nb = getNextBuildNumber();
        LiterateEnvironmentBuild r = getLastBuild();
        if (r != null && r.getNumber() >= nb) // make sure we don't schedule the same build twice
        {
            throw new IllegalStateException("Build #" + nb + " is already completed");
        }
        return nb;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDisplayName() {
        return super.getDisplayName();    // todo
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LiterateBranchProject getParent() {
        return (LiterateBranchProject) super.getParent();
    }

    /**
     * Returns the owning {@link LiterateMultibranchProject}.
     *
     * @return the owning {@link LiterateMultibranchProject}.
     */
    public LiterateMultibranchProject getGrandparent() {
        return (LiterateMultibranchProject) (((LiterateBranchProject) getParent()).getParent());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getQuietPeriod() {
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getScmCheckoutRetryCount() {
        return getParent().getScmCheckoutRetryCount();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isConfigurable() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Class<LiterateEnvironmentBuild> getBuildClass() {
        return LiterateEnvironmentBuild.class;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected synchronized LiterateEnvironmentBuild newBuild() throws IOException {
        List<Action> actions = Executor.currentExecutor().getCurrentWorkUnit().context.actions;
        LiterateBranchBuild lb = getParent().getLastBuild();
        for (Action a : actions) {
            if (a instanceof ParentLiterateBranchBuildAction) {
                lb = ((ParentLiterateBranchBuildAction) a).getParent();
            }
        }

        // for every RunImpl there should be a parent BuildImpl
        LiterateEnvironmentBuild lastBuild = new LiterateEnvironmentBuild(this, lb.getTimestamp());

        lastBuild.number = lb.getNumber();

        builds.put(lastBuild);
        return lastBuild;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void buildDependencyGraph(DependencyGraph graph) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LiterateEnvironmentProject asProject() {
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Label getAssignedLabel() {
        return getGrandparent().getEnvironmentMapper().getLabel(getEnvironment());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAssignedLabelString() {
        Label label = getAssignedLabel();
        return label == null ? null : label.toString();
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public String getPronoun() {
        return AlternativeUiTextProvider.get(PRONOUN, this, "Environment");
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public List<Builder> getBuilders() {
        return getParent().getBuilders();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<Descriptor<Publisher>, Publisher> getPublishers() {
        return getParent().getPublishers();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DescribableList<Builder, Descriptor<Builder>> getBuildersList() {
        return getParent().getBuildersList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DescribableList<Publisher, Descriptor<Publisher>> getPublishersList() {
        return getParent().getPublishersList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<Descriptor<BuildWrapper>, BuildWrapper> getBuildWrappers() {
        return getParent().getBuildWrappers();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DescribableList<BuildWrapper, Descriptor<BuildWrapper>> getBuildWrappersList() {
        return getParent().getBuildWrappersList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Publisher getPublisher(Descriptor<Publisher> descriptor) {
        return getParent().getPublisher(descriptor);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LogRotator getLogRotator() {
        LogRotator lr = getParent().getLogRotator();
        return new LiterateMultibranchProject.LinkedLogRotator(lr != null ? lr.getArtifactDaysToKeep() : -1,
                lr != null ? lr.getArtifactNumToKeep() : -1);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLogRotator(LogRotator logRotator) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JDK getJDK() {
        for (ToolInstallation t : getGrandparent().getEnvironmentMapper().getToolInstallations(getEnvironment())) {
            if (t instanceof JDK) {
                return (JDK) t;
            }
        }
        return super.getJDK();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setJDK(JDK jdk) throws IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SCM getScm() {
        return getParent().getScm();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean checkout(AbstractBuild build, Launcher launcher, BuildListener listener, File changelogFile)
            throws IOException, InterruptedException {
        ParentLiterateBranchBuildAction parentBuild = getAction(ParentLiterateBranchBuildAction.class);
        SCMRevisionAction hashAction = parentBuild != null
                ? parentBuild.getParent().getAction(SCMRevisionAction.class)
                : null;
        if (hashAction != null) {
            Branch branch = getParent().getBranch();
            SCMSource source = getParent().getParent().getSCMSource(branch.getSourceId());
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
     * Returns {@code true} if this is an active item.
     *
     * @return {@code true} if this is an active item.
     */
    public boolean isActiveItem() {
        return getParent().getActiveEnvironments().contains(getEnvironment());
    }

    /**
     * @deprecated Use {@link #scheduleBuild(hudson.model.ParametersAction, hudson.model.Cause)}.  Since 1.283
     */
    @Deprecated
    public boolean scheduleBuild(ParametersAction parameters) {
        return scheduleBuild(parameters, new Cause.LegacyCodeCause());
    }

    /**
     * @param parameters Can be null.
     */
    public boolean scheduleBuild(ParametersAction parameters, Cause c) {
        return Jenkins.getInstance().getQueue()
                .schedule(this, getQuietPeriod(), parameters, new CauseAction(c),
                        new ParentLiterateBranchBuildAction()) != null;
    }

}
