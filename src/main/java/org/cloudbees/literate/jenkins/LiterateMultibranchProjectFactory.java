package org.cloudbees.literate.jenkins;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.ItemGroup;
import hudson.model.TaskListener;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.Map;
import javax.annotation.Nonnull;
import jenkins.branch.MultiBranchProject;
import jenkins.branch.MultiBranchProjectFactory;
import jenkins.branch.MultiBranchProjectFactoryDescriptor;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import org.apache.commons.lang.StringUtils;
import org.cloudbees.literate.api.v1.ProjectModelSource;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Provides support for organization folders
 *
 * @since 0.3
 */
public class LiterateMultibranchProjectFactory extends MultiBranchProjectFactory.BySCMSourceCriteria {

    /**
     * The name of the marker file.
     */
    private String markerFile;

    /**
     * The profile(s) that this build will activate.
     */
    private String profiles;

    @DataBoundConstructor
    public LiterateMultibranchProjectFactory(boolean specifyMarkerFile, String markerFile, boolean specifyProfiles, String profiles) {
        this.markerFile = specifyMarkerFile ? markerFile : null;
        this.profiles = specifyProfiles ? profiles : null;
    }

    public boolean isSpecifyMarkerFile() {
        return StringUtils.isNotBlank(markerFile) && !StringUtils.equals(markerFile, getDescriptor().getMarkerFile());
    }

    public boolean isSpecifyProfiles() {
        return StringUtils.isNotBlank(profiles);
    }

    /**
     * Gets the profile(s) that are activated by this build.
     *
     * @return the profile(s) that are activated by this build.
     */
    public String getProfiles() {
        return StringUtils.defaultString(profiles);
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
     * Gets the project marker file.
     *
     * @return the project marker file.
     */
    public String getMarkerFile() {
        return StringUtils.isBlank(markerFile) ? getDescriptor().getMarkerFile() : markerFile;
    }

    /**
     * Sets the project marker file.
     *
     * @param markerFile the project marker file.
     */
    public void setMarkerFile(String markerFile) {
        this.markerFile = StringUtils.isBlank(markerFile)
                || StringUtils.equals(markerFile, getDescriptor().getMarkerFile())
                ? null
                : markerFile.trim();
    }

    /**
     * {@inheritDoc}
     */
    @Nonnull
    @Override
    protected SCMSourceCriteria getSCMSourceCriteria(@Nonnull SCMSource source) {
        final Collection<String> markerFiles = new ProjectModelSource(LiterateBranchProject.class.getClassLoader())
                .markerFiles(getMarkerFile());
        return new SCMSourceCriteria() {
            public boolean isHead(@NonNull Probe probe, @NonNull TaskListener listener) throws IOException {
                for (String markerFile : markerFiles) {
                    if (probe.exists(markerFile)) {
                        return true;
                    }
                    listener.getLogger().println(MessageFormat.format("No {0} marker file", markerFile));
                }
                return false;
            }
        };
    }

    /**
     * {@inheritDoc}
     */
    @Nonnull
    @Override
    protected MultiBranchProject<?, ?> doCreateProject(@Nonnull ItemGroup<?> parent, @Nonnull String name,
                                                       @Nonnull Map<String, Object> attributes) {
        final LiterateMultibranchProject project = new LiterateMultibranchProject(parent, name);
        project.setMarkerFile(getMarkerFile());
        project.setProfiles(getProfiles());
        return project;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static class DescriptorImpl extends MultiBranchProjectFactoryDescriptor {

        /**
         * {@inheritDoc}
         */
        @Override
        public MultiBranchProjectFactory newInstance() {
            return new LiterateMultibranchProjectFactory(false, null, false, null);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.LiterateMultibranchProjectFactory_DisplayName();
        }

        /**
         * Returns the global default marker file.
         *
         * @return the global default marker file.
         */
        public String getMarkerFile() {
            final LiterateMultibranchProject.DescriptorImpl d =
                    Jenkins.getActiveInstance().getDescriptorByType(LiterateMultibranchProject.DescriptorImpl.class);
            if (d == null) {
                throw new AssertionError(LiterateMultibranchProject.class.getName() + " is missing its descriptor");
            }

            return d.getMarkerFile();
        }


    }
}
