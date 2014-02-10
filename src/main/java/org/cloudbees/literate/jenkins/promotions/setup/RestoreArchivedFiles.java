package org.cloudbees.literate.jenkins.promotions.setup;

import hudson.Extension;
import hudson.FilePath;
import hudson.Util;
import hudson.console.HyperlinkNote;
import hudson.model.BuildListener;
import hudson.model.Result;
import org.apache.commons.lang.StringUtils;
import org.cloudbees.literate.jenkins.LiterateBranchBuild;
import org.cloudbees.literate.jenkins.LiterateEnvironmentBuild;
import org.cloudbees.literate.jenkins.promotions.PromotionBuild;
import org.cloudbees.literate.jenkins.promotions.PromotionConfiguration;
import org.cloudbees.literate.jenkins.promotions.PromotionSetup;
import org.cloudbees.literate.jenkins.promotions.PromotionSetupDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
import java.io.IOException;
import java.util.Set;

/**
 * @author Stephen Connolly
 */
public class RestoreArchivedFiles extends PromotionSetup {

    /**
     * Possibly null 'includes' pattern as in Ant.
     */
    private final String includes;
    /**
     * Possibly null 'excludes' pattern as in Ant.
     */
    private final String excludes;
    /**
     * Possibly null list of environments;
     */
    private final String environments;

    @DataBoundConstructor
    public RestoreArchivedFiles(String includes, String excludes, String environments) {
        this.includes = includes;
        this.excludes = excludes;
        this.environments = PromotionConfiguration.normalizeEnvironment(environments);
    }

    public String getExcludes() {
        return excludes;
    }

    public String getIncludes() {
        return includes;
    }

    public String getEnvironments() {
        return environments;
    }

    @Override
    public boolean setup(PromotionBuild promotionBuild, BuildListener listener) throws InterruptedException {
        FilePath ws = promotionBuild.getWorkspace();
        if (ws == null) { // #3330: slave down?
            listener.fatalError(Messages.RestoreArchivedFiles_workspaceMissing());
            return false;
        }

        LiterateBranchBuild baseBuild = promotionBuild.getTarget();
        listener.getLogger().println(Messages.RestoreArchivedFiles_restoringArtifactsFrom(HyperlinkNote.encodeTo(baseBuild.getUrl(), baseBuild.getFullDisplayName())));
        Set<String> environments = PromotionConfiguration.asEnvironments(this.environments);
        for (LiterateEnvironmentBuild envBuild : baseBuild.getItems()) {
            if (environments != null) {
                boolean match = false;
                for (String c : envBuild.getParent().getEnvironment().getComponents()) {
                    if (environments.contains(c)) {
                        match = true;
                        break;
                    }
                }
                if (!match) {
                    listener.getLogger().println(Messages.RestoreArchivedFiles_ignoringArtifactsFrom(
                            HyperlinkNote.encodeTo('/' + envBuild.getUrl(), envBuild.getFullDisplayName())));
                    continue;
                }
            }
            if (environments == null || environments.contains(envBuild.getParent().getName())) {
                listener.getLogger().println(Messages.RestoreArchivedFiles_copyingArtifactsFrom(
                        HyperlinkNote.encodeTo('/' + envBuild.getUrl(), envBuild.getFullDisplayName())));
            }
            File dir = envBuild.getArtifactsDir();
            try {
                int count = new FilePath(dir).copyRecursiveTo(
                        StringUtils.defaultIfBlank(includes, "**/*"),
                        Util.fixEmptyAndTrim(excludes),
                        ws);
                listener.getLogger().println(Messages.RestoreArchivedFiles_copiedArtifactsFrom(count,
                        HyperlinkNote.encodeTo('/' + envBuild.getUrl(), envBuild.getFullDisplayName())));
                ;
            } catch (IOException e) {
                Util.displayIOException(e, listener);
                e.printStackTrace(listener.error(Messages.RestoreArchivedFiles_failedArtifactsFrom(
                        HyperlinkNote.encodeTo('/' + envBuild.getUrl(), envBuild.getFullDisplayName()))));
                promotionBuild.setResult(Result.FAILURE);
                return false;
            }
        }
        return true;
    }

    @Extension
    public static class DescriptorImpl extends PromotionSetupDescriptor {

        @Override
        public String getDisplayName() {
            return Messages.RestoreArchivedFiles_displayName();
        }

        public boolean isDefault(RestoreArchivedFiles instance) {
            return instance == null || (StringUtils.isBlank(instance.getIncludes()) && StringUtils
                    .isBlank(instance.getExcludes()) && StringUtils.isBlank(instance.getEnvironments()));
        }
    }
}
