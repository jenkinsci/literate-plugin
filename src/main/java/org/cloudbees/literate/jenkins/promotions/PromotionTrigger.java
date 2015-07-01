package org.cloudbees.literate.jenkins.promotions;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.AutoCompletionCandidates;
import hudson.model.Item;
import hudson.model.Items;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.ListBoxModel.Option;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.cloudbees.literate.jenkins.LiterateBranchProject;
import org.cloudbees.literate.jenkins.LiterateMultibranchProject;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.util.List;

/**
 * {@link hudson.triggers.Trigger} that starts a build when a promotion happens.
 *
 * @author Kohsuke Kawaguchi
 */
public class PromotionTrigger extends Trigger<AbstractProject> {
    private final String jobName;
    private final String branchName;
    private final String process;

    @DataBoundConstructor
    public PromotionTrigger(String jobName, String branchName, String process) {
        this.jobName = jobName;
        this.branchName = branchName;
        this.process = process;
    }

    public String getJobName() {
        return jobName;
    }

    public String getBranchName() {
        return branchName;
    }

    public String getProcess() {
        return process;
    }

    public boolean appliesTo(PromotionProject proc) {
        return proc.getName().equals(process)
                && proc.getParent().getOwner().getName().equals(branchName)
                && proc.getParent().getOwner().getParent().getFullName().equals(jobName);
    }

    public void consider(PromotionBuild p) {
        if (appliesTo(p.getParent())) {
            job.scheduleBuild2(job.getQuietPeriod());
        }
    }

    @Extension
    public static class DescriptorImpl extends TriggerDescriptor {
        @Override
        public boolean isApplicable(Item item) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Build when a literate project branch is promoted";
        }

        /**
         * Checks the job name.
         */
        public FormValidation doCheckJobName(@AncestorInPath Item project, @QueryParameter String value) {
            project.checkPermission(Item.CONFIGURE);

            if (StringUtils.isNotBlank(value)) {
                LiterateMultibranchProject p =
                        Jenkins.getActiveInstance().getItem(value, project, LiterateMultibranchProject.class);
                if (p == null) {
                    LiterateMultibranchProject nearest = Items.findNearest(LiterateMultibranchProject.class, value, project.getParent());
                    String relativeName = nearest != null ? nearest.getRelativeNameFrom(project) : "?";
                    return FormValidation.error(hudson.tasks.Messages.BuildTrigger_NoSuchProject(value, relativeName));
                }

            }

            return FormValidation.ok();
        }

        public AutoCompletionCandidates doAutoCompleteJobName(@QueryParameter String value) {
            AutoCompletionCandidates candidates = new AutoCompletionCandidates();
            List<AbstractProject> jobs = Jenkins.getActiveInstance().getItems(AbstractProject.class);
            for (AbstractProject job : jobs) {
                if (job.getFullName().startsWith(value)) {
                    if (job.hasPermission(Item.READ)) {
                        candidates.add(job.getFullName());
                    }
                }
            }
            return candidates;
        }

        /**
         * Fills in the available promotion processes.
         */
        public ListBoxModel doFillBranchNameItems(@AncestorInPath Item defaultJob,
                                                  @QueryParameter("jobName") String jobName) {
            defaultJob.checkPermission(Item.CONFIGURE);

            LiterateMultibranchProject j = null;
            if (jobName != null) {
                j = Jenkins.getActiveInstance().getItem(jobName, defaultJob, LiterateMultibranchProject.class);
            }

            ListBoxModel r = new ListBoxModel();
            if (j != null) {
                for (LiterateBranchProject jj : j.getItems()) {
                    r.add(jj.getDisplayName(), jj.getName());
                }
            }
            return r;
        }

        /**
         * Fills in the available promotion processes.
         */
        public ListBoxModel doFillProcessItems(@AncestorInPath Item defaultJob,
                                               @QueryParameter("branchName") String branchName,
                                               @QueryParameter("jobName") String jobName) {
            defaultJob.checkPermission(Item.CONFIGURE);

            LiterateMultibranchProject j = null;
            if (jobName != null) {
                j = Jenkins.getActiveInstance().getItem(jobName, defaultJob, LiterateMultibranchProject.class);
            }
            LiterateBranchProject jj = j == null ? null : j.getBranch(branchName);

            ListBoxModel r = new ListBoxModel();
            if (jj != null) {
                PromotionJobProperty pp = jj.getProperty(PromotionJobProperty.class);
                if (pp != null) {
                    for (PromotionProject proc : pp.getActiveItems()) {
                        r.add(new Option(proc.getDisplayName(), proc.getName()));
                    }
                }
            }
            return r;
        }
    }
}
