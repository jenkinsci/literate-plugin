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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Items;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.scm.NullSCM;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.tasks.LogRotator;
import hudson.util.RunList;
import jenkins.branch.Branch;
import jenkins.branch.BranchProjectFactory;
import jenkins.branch.BranchProjectFactoryDescriptor;
import jenkins.branch.MultiBranchProject;
import jenkins.branch.MultiBranchProjectDescriptor;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * A multiple branch literate build project type.
 *
 * @author Stephen Connolly
 */
public class LiterateMultibranchProject extends
        MultiBranchProject<LiterateBranchProject, LiterateBranchBuild> {

    /**
     * The profile(s) that this build will activate.
     */
    private String profiles;

    /**
     * The environment mapper to use.
     */
    private BuildEnvironmentMapper environmentMapper = new DefaultBuildEnvironmentMapper();

    /**
     * Constructor.
     *
     * @param parent the parent container.
     * @param name   our name.
     */
    public LiterateMultibranchProject(ItemGroup parent, String name) {
        super(parent, name);
    }

    /**
     * Gets the profile(s) that are activated by this build.
     *
     * @return the profile(s) that are activated by this build.
     */
    public String getProfiles() {
        return profiles;
    }

    /**
     * Sets the profile(s) that are activated by this build.
     *
     * @param profiles the profile(s) that are activated by this build.
     */
    public void setProfiles(String profiles) {
        this.profiles = profiles;
    }

    /**
     * Gets the {@link BuildEnvironmentMapper} to use.
     *
     * @return the {@link BuildEnvironmentMapper} to use.
     */
    public BuildEnvironmentMapper getEnvironmentMapper() {
        return environmentMapper;
    }

    /**
     * Sets the {@link BuildEnvironmentMapper} to use.
     *
     * @param environmentMapper the {@link BuildEnvironmentMapper} to use.
     */
    public void setEnvironmentMapper(BuildEnvironmentMapper environmentMapper) {
        this.environmentMapper = environmentMapper;
    }

    /**
     * Returns {@code true} if and only if this build is activating any profiles other than the default.
     *
     * @return {@code true} if and only if this build is activating any profiles other than the default.
     */
    public boolean isSpecifyProfiles() {
        return !StringUtils.isBlank(profiles);
    }

    /**
     * Returns the list of profiles.
     *
     * @return the list of profiles.
     */
    @NonNull
    public List<String> getProfileList() {
        List<String> result = new ArrayList<String>();
        if (isSpecifyProfiles()) {
            Collections.addAll(result, StringUtils.split(profiles, " \t\n\r,"));
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SCMSourceCriteria getSCMSourceCriteria(@NonNull SCMSource source) {
        // TODO per-project marker files
        return new SCMSourceCriteria() {

            public boolean isHead(@NonNull Probe probe, @NonNull TaskListener listener) throws IOException {
                return probe.exists(".cloudbees.md");
            }
        };
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    protected BranchProjectFactory<LiterateBranchProject, LiterateBranchBuild> newProjectFactory() {
        return new ProjectFactoryImpl();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void submit(StaplerRequest req, StaplerResponse rsp)
            throws IOException, ServletException, Descriptor.FormException {
        super.submit(req, rsp);
        JSONObject json = req.getSubmittedForm();
        JSONObject specifyProfiles = json.optJSONObject("specifyProfiles");
        if (specifyProfiles == null) {
            profiles = "";
        } else {
            profiles = specifyProfiles.optString("profiles", "");
        }
        // TODO expose the environment mapper
    }

    /**
     * Our descriptor
     */
    @Extension
    public static class DescriptorImpl extends MultiBranchProjectDescriptor {

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.LiterateMultibranchProject_DisplayName();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public TopLevelItem newInstance(ItemGroup parent, String name) {
            return new LiterateMultibranchProject(parent, name);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        @NonNull
        public List<SCMDescriptor<?>> getSCMDescriptors() {
            List<SCMDescriptor<?>> result = new ArrayList<SCMDescriptor<?>>(SCM.all());
            for (Iterator<SCMDescriptor<?>> iterator = result.iterator(); iterator.hasNext(); ) {
                SCMDescriptor<?> d = iterator.next();
                if (NullSCM.class.equals(d.clazz)) {
                    iterator.remove();
                }
            }
            return result; // todo figure out filtering
        }

        /**
         * Returns the type of project factory supported by this project type.
         *
         * @return the type of project factory supported by this project type.
         */
        @SuppressWarnings("unused") // stapler
        public BranchProjectFactoryDescriptor getProjectFactoryDescriptor() {
            return Jenkins.getInstance().getDescriptorByType(ProjectFactoryImpl.DescriptorImpl.class);
        }
    }

    /**
     * Our {@link BranchProjectFactory}.
     */
    public static class ProjectFactoryImpl extends BranchProjectFactory<LiterateBranchProject, LiterateBranchBuild> {

        /**
         * Constructor.
         */
        @DataBoundConstructor
        public ProjectFactoryImpl() {
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public LiterateBranchProject newInstance(final Branch branch) {
            return branch.configureJob(new LiterateBranchProject((LiterateMultibranchProject) getOwner(), branch));
        }

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public Branch getBranch(@NonNull LiterateBranchProject project) {
            return project.getBranch();
        }

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public LiterateBranchProject setBranch(@NonNull LiterateBranchProject project, @NonNull Branch branch) {
            if (!project.getBranch().equals(branch)) {
                project.setBranch(branch);
                try {
                    project.save();
                } catch (IOException e) {
                    // TODO log
                }
            } else {
                project.setBranch(branch);
            }
            return project;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isProject(Item item) {
            return item instanceof LiterateBranchProject;
        }

        /**
         * Our descriptor.
         */
        @Extension
        @SuppressWarnings("unused")// instantiated by Jenkins
        public static class DescriptorImpl extends BranchProjectFactoryDescriptor {

            /**
             * {@inheritDoc}
             */
            @Override
            public String getDisplayName() {
                return "Fixed configuration";
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean isApplicable(Class<? extends MultiBranchProject> clazz) {
                return LiterateMultibranchProject.class.isAssignableFrom(clazz);
            }
        }
    }

    /**
     * A {@link LogRotator} that handles tidying up in cases where the parent job's build has been removed.
     */
    final static class LinkedLogRotator extends LogRotator {
        /**
         * Constructor.
         *
         * @param artifactDaysToKeep number of days to keep artifacts.
         * @param artifactNumToKeep  number of artifacts to keep.
         */
        LinkedLogRotator(int artifactDaysToKeep, int artifactNumToKeep) {
            super(-1, -1, artifactDaysToKeep, artifactNumToKeep);
        }

        /**
         * {@inheritDoc}
         */
        @SuppressWarnings("deprecation")
        @Override
        public void perform(Job _job) throws IOException, InterruptedException {
            // Let superclass handle clearing artifacts, if configured:
            super.perform(_job);
            LiterateEnvironmentProject job = (LiterateEnvironmentProject) _job;

            // copy it to the array because we'll be deleting builds as we go.
            RunList<LiterateEnvironmentBuild> builds = job.getBuilds();
            for (LiterateEnvironmentBuild r : builds.toArray(new LiterateEnvironmentBuild[builds.size()])) {
                if (job.getParent().getBuildByNumber(r.getNumber()) == null) {
                    r.delete();
                }
            }

            if (!job.isActiveItem() && job.getLastBuild() == null) {
                job.delete();
            }
        }
    }

    /**
     * Give us a nice XML alias
     */
    @Initializer(before = InitMilestone.PLUGINS_STARTED)
    @SuppressWarnings("unused") // called by Jenkins
    public static void registerXStream() {
        Items.XSTREAM.alias("literate-multibranch", LiterateMultibranchProject.class);
    }

}

