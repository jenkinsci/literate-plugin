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
import hudson.CopyOnWrite;
import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.XmlFile;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Items;
import hudson.model.Label;
import hudson.model.Project;
import hudson.model.Queue;
import hudson.model.SCMedItem;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import hudson.scm.SCM;
import hudson.security.Permission;
import hudson.util.AlternativeUiTextProvider;
import hudson.util.CopyOnWriteMap;
import jenkins.branch.Branch;
import jenkins.model.Jenkins;
import jenkins.scm.SCMCheckoutStrategy;
import jenkins.scm.SCMCheckoutStrategyDescriptor;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMRevisionAction;
import jenkins.scm.api.SCMSource;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.TokenList;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Represents a specific branch of a literate build project.
 *
 * @author Stephen Connolly
 */
public class LiterateBranchProject extends Project<LiterateBranchProject, LiterateBranchBuild>
        implements TopLevelItem, SCMedItem,
        ItemGroup<LiterateEnvironmentProject>, Queue.FlyweightTask {

    /**
     * Hack to prevent the Configure link showing up in the sidebar.
     */
    public static final Permission CONFIGURE = null;

    /**
     * The branch that we are tracking.
     */
    @NonNull
    private Branch branch;

    /**
     * The environments that have been built for this branch.
     */
    @NonNull
    private transient Map<BuildEnvironment, LiterateEnvironmentProject> environments =
            new CopyOnWriteMap.Tree<BuildEnvironment, LiterateEnvironmentProject>();

    /**
     * The subset of environments that were built for the most recent build.
     */
    @CopyOnWrite
    @NonNull
    private transient volatile Set<BuildEnvironment> activeEnvironments = new TreeSet<BuildEnvironment>();

    /**
     * Constructor.
     *
     * @param parent the parent.
     * @param branch the branch.
     */
    public LiterateBranchProject(@NonNull LiterateMultibranchProject parent, @NonNull Branch branch) {
        super(parent, branch.getName());
        this.branch = branch;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Label getAssignedLabel() {
        return null; // we are a flyweight task, don't care where our "placeholder" builds.
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAssignedLabelString() {
        return null; // we are a flyweight task, don't care where our "placeholder" builds.
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onLoad(@NonNull ItemGroup<? extends Item> parent, @NonNull String name) throws IOException {
        super.onLoad(parent, name);
        environments = new CopyOnWriteMap.Tree<BuildEnvironment, LiterateEnvironmentProject>();
        getBuildersList().setOwner(this);
        getPublishersList().setOwner(this);
        getBuildWrappersList().setOwner(this);

        rebuildEnvironments(null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public LiterateMultibranchProject getParent() {
        return (LiterateMultibranchProject) super.getParent();
    }

    /**
     * Returns the branch.
     *
     * @return the branch.
     */
    public synchronized Branch getBranch() {
        return branch;
    }

    /**
     * Sets the branch.
     *
     * @param branch the branch.
     */
    public synchronized void setBranch(@NonNull Branch branch) {
        branch.getClass();
        this.branch = branch;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public synchronized String getName() {
        return branch.getName();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public synchronized SCM getScm() {
        return branch.getScm();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public SCMCheckoutStrategy getScmCheckoutStrategy() {
        return Jenkins.getInstance().getDescriptorByType(SCMCheckoutStrategyImpl.DescriptorImpl.class).getInstance();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized boolean isBuildable() {
        return super.isBuildable() && branch != null && branch.isBuildable();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isNameEditable() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    protected Class<LiterateBranchBuild> getBuildClass() {
        return LiterateBranchBuild.class;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public String getPronoun() {
        return AlternativeUiTextProvider.get(PRONOUN, this, "Branch");
    }

    /**
     * Gets the directory that holds all the environments.
     *
     * @return the directory that holds all the environments.
     */
    @NonNull
    public File getEnvironmentsDir() {
        return new File(getRootDir(), "environments");
    }

    /**
     * Gets a named item.
     *
     * @param name the name.
     * @return the item.
     */
    @CheckForNull
    public LiterateEnvironmentProject getItem(@NonNull String name) {
        try {
            // fast path for nice environments
            BuildEnvironment fastPath = BuildEnvironment.fromString(name);
            LiterateEnvironmentProject configuration = environments.get(fastPath);
            if (configuration != null) {
                return configuration;
            }
            // need to search in depth as name could still contain an environment that contain ","
            // just where the sort is order preserved
        } catch (IllegalArgumentException e) {
            // need to search in depth as name looks like it contains an environment that contain ","
        }
        // search in depth
        for (Map.Entry<BuildEnvironment, LiterateEnvironmentProject> e : environments.entrySet()) {
            if (e.getKey().getName().equals(name)) {
                return e.getValue();
            }
        }
        return null;
    }

    /**
     * Returns the active items.
     *
     * @return the active items.
     */
    @SuppressWarnings("unused") // accessed by Jelly EL
    public Collection<LiterateEnvironmentProject> getActiveItems() {
        Map<BuildEnvironment, LiterateEnvironmentProject> result =
                new LinkedHashMap<BuildEnvironment, LiterateEnvironmentProject>(environments);
        result.keySet().retainAll(activeEnvironments);
        return result.values();
    }

    /**
     * Returns the active {@link BuildEnvironment}s.
     *
     * @return the active {@link BuildEnvironment}s.
     */
    public Collection<BuildEnvironment> getActiveEnvironments() {
        return activeEnvironments;
    }

    /**
     * {@inheritDoc}
     */
    public Collection<LiterateEnvironmentProject> getItems() {
        return environments.values();
    }

    /**
     * {@inheritDoc}
     */
    public String getUrlChildPrefix() {
        return ".";
    }

    /**
     * {@inheritDoc}
     */
    public File getRootDirFor(LiterateEnvironmentProject child) {
        return getRootDir(child.getEnvironment());
    }

    /**
     * Gets the root directory for a specific environment.
     *
     * @param environment the environment.
     * @return the root directory of that environment.
     */
    private File getRootDir(BuildEnvironment environment) {
        File f = getEnvironmentsDir();
        if (environment.isDefault()) {
            f = new File(f, "env-");
        } else {
            for (String env : environment.getComponents()) {
                f = new File(f, "env-" + Util.rawEncode(env));
            }
        }
        return f;
    }

    /**
     * {@inheritDoc}
     */
    public void onRenamed(LiterateEnvironmentProject item, String oldName, String newName)
            throws IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    public void onDeleted(LiterateEnvironmentProject item) throws IOException {
        // no-op
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public Object getDynamic(String token, StaplerRequest req, StaplerResponse rsp) {
        try {
            LiterateEnvironmentProject item = getItem(token);
            if (item != null) {
                return item;
            }
        } catch (IllegalArgumentException _) {
            // failed to parse the token as BuildEnvironment. Must be something else
        }
        return super.getDynamic(token, req, rsp);
    }

    /**
     * Returns the specific environment's project.
     *
     * @param components the environment's components.
     * @return the specific environment's project
     */
    public LiterateEnvironmentProject getEnvironment(Set<String> components) {
        return getEnvironment(new BuildEnvironment(components));
    }

    /**
     * Returns the specific environment's project.
     *
     * @param environment the environment.
     * @return the specific environment's project
     */
    public LiterateEnvironmentProject getEnvironment(BuildEnvironment environment) {
        return environments.get(environment);
    }

    /**
     * Load's the environments from the specified directory. This method gets recursively invoked, so the
     * {@code environments} parameter is used to track the state of the recursion.
     *
     * @param dir         the directory to load from.
     * @param result      the map to store the result in.
     * @param environment the environments to add to all child environments of the directory.
     */
    private void loadEnvironments(File dir, CopyOnWriteMap<BuildEnvironment, LiterateEnvironmentProject> result,
                                  Set<String> environment) {
        File[] environmentDirs = dir.listFiles(new FileFilter() {
            public boolean accept(File child) {
                return child.isDirectory() && child.getName().startsWith("env-");
            }
        });
        if (environmentDirs == null) {
            return;
        }
        for (File v : environmentDirs) {
            Set<String> c = new TreeSet<String>(environment);
            String id = TokenList.decode(v.getName().substring("env-".length()));
            if (!StringUtils.isBlank(id)) {
                c.add(id);
            }
            try {
                XmlFile config = Items.getConfigFile(v);
                if (config.exists()) {
                    BuildEnvironment env = new BuildEnvironment(c);
                    LiterateEnvironmentProject item = null;
                    if (this.environments != null) {
                        item = this.environments.get(env);
                    }
                    if (item == null) {
                        item = (LiterateEnvironmentProject) config.read();
                        item.setEnvironment(env);
                        item.onLoad(this, env.getName());
                    }
                    result.put(env, item);
                }
            } catch (IOException e) {
                // todo LOGGER.log(Level.WARNING, "Failed to load branch environment " + v, e);
            }
            loadEnvironments(v, result, c);
        }
    }

    /**
     * Rebuilds the set of environments for the specified context.
     *
     * @param context the contex.
     * @return the set of {@link BuildEnvironment}s.
     * @throws IOException if something goes wrong.
     */
    Set<BuildEnvironment> rebuildEnvironments(LiterateBranchBuild context) throws IOException {
        CopyOnWriteMap.Tree<BuildEnvironment, LiterateEnvironmentProject> environments = new CopyOnWriteMap
                .Tree<BuildEnvironment, LiterateEnvironmentProject>();
        loadEnvironments(getEnvironmentsDir(), environments, Collections.<String>emptySet());
        this.environments = environments;
        if (context == null) {
            context = getLastBuild();
        }
        Iterable<BuildEnvironment> activeBuildEnvironments;
        if (context != null) {
            activeBuildEnvironments = context.getBuildEnvironments();
        } else {
            activeBuildEnvironments = Collections.emptyList();
        }

        Set<BuildEnvironment> activeEnvironments = new TreeSet<BuildEnvironment>();
        for (BuildEnvironment buildEnv : activeBuildEnvironments) {
            activeEnvironments.add(buildEnv);
            LiterateEnvironmentProject config = this.environments.get(buildEnv);
            if (config == null) {
                // todo LOGGER.fine("Adding configuration: " + env);
                config = branch.configureJob(new LiterateEnvironmentProject(this, buildEnv));
                config.onCreatedFromScratch();
                config.save();
                this.environments.put(buildEnv, config);
            } else {
                branch.configureJob(config);
                config.save();
            }
        }
        this.activeEnvironments = activeEnvironments;
        return activeEnvironments;
    }

    /**
     * Updates the list of active environments.
     *
     * @param environments the new list of active environments.
     * @throws IOException if something goes wrong.
     */
    public void setActiveEnvironments(List<Set<String>> environments) throws IOException {
        Set<BuildEnvironment> activeEnvironments = new TreeSet<BuildEnvironment>();
        for (Set<String> env : environments) {
            BuildEnvironment buildEnv = new BuildEnvironment(env);
            activeEnvironments.add(buildEnv);
            LiterateEnvironmentProject config = this.environments.get(buildEnv);
            if (config == null) {
                // todo LOGGER.fine("Adding configuration: " + env);
                config = branch.configureJob(new LiterateEnvironmentProject(this, buildEnv));
                config.onCreatedFromScratch();
                config.save();
                this.environments.put(buildEnv, config);
            }
        }
        this.activeEnvironments = activeEnvironments;
    }

    /**
     * We all hate monkey patching!
     */
    @Override
    public boolean checkout(AbstractBuild build, Launcher launcher, BuildListener listener, File changelogFile)
            throws IOException, InterruptedException {
        final Branch branch = getBranch();
        SCMSource source = getParent().getSCMSource(branch.getSourceId());
        if (source != null) {
            SCMHead head = branch.getHead();
            SCMRevision revision = source.fetch(head, listener);
            if (revision != null) {
                build.addAction(new SCMRevisionAction(revision));
                if (revision.isDeterministic()) {
                    SCM scm = source.build(head, revision);

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
     * {@inheritDoc}
     */
    // TODO - Hack - child items of an item group that is a view container must to implement TopLevelItem
    public TopLevelItemDescriptor getDescriptor() {
        return (TopLevelItemDescriptor) Jenkins.getInstance().getDescriptorOrDie(LiterateBranchProject.class);
    }

    /**
     * Our descriptor
     */
    // TODO - Hack - child items of an item group that is a view container must to implement TopLevelItem

    @Extension
    public static class DescriptorImpl extends AbstractProjectDescriptor {

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return null;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public TopLevelItem newInstance(ItemGroup parent, String name) {
            throw new UnsupportedOperationException();
        }

        /**
         * Method that removes this descriptor from the list of {@link TopLevelItemDescriptor}s because
         * we don't want to appear as one.
         *
         * @throws Exception if the hack fails.
         */
        // TODO - Hack - child items of an item group that is a view container must to implement TopLevelItem
        @Initializer(after = InitMilestone.JOB_LOADED, before = InitMilestone.COMPLETED)
        @SuppressWarnings("unused") // invoked by Jenkins
        public static void postInitialize() throws Exception {
            DescriptorExtensionList<TopLevelItem, TopLevelItemDescriptor> all = Items.all();
            all.remove(all.get(DescriptorImpl.class));
        }
    }

    /**
     * The branch specific checkout strategy.
     */
    public static class SCMCheckoutStrategyImpl extends SCMCheckoutStrategy {
        /**
         * {@inheritDoc}
         */
        @Override
        public void preCheckout(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
                throws IOException, InterruptedException {
            if (build instanceof LiterateBranchBuild) {
                LiterateBranchProject project = ((LiterateBranchBuild) build).getParent();
                Branch branch = project.getBranch();
                SCMSource source = project.getParent().getSCMSource(branch.getSourceId());
                if (source != null) {
                    SCMRevision revision = source.fetch(branch.getHead(), listener);
                    project.setScm(source.build(branch.getHead(), revision));
                }
            }
            super.preCheckout(build, launcher, listener);
        }

        /**
         * Our descriptor.
         */
        @Extension
        public static class DescriptorImpl extends SCMCheckoutStrategyDescriptor {
            /**
             * Our singleton.
             */
            private final SCMCheckoutStrategy instance = new SCMCheckoutStrategyImpl();

            /**
             * Returns the singleton.
             *
             * @return the singleton.
             */
            public SCMCheckoutStrategy getInstance() {
                return instance;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean isApplicable(AbstractProject project) {
                return project instanceof LiterateBranchProject;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getDisplayName() {
                return "Literate Build Checkout Strategy";
            }
        }
    }

    /**
     * Give us a nice XML alias
     */
    @Initializer(before = InitMilestone.PLUGINS_STARTED)
    @SuppressWarnings("unused") // called by Jenkins
    public static void registerXStream() {
        Items.XSTREAM.alias("literate-branch", LiterateMultibranchProject.class);
    }
}
