package org.cloudbees.literate.jenkins;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Action;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.model.ModelObject;
import jenkins.branch.BranchProperty;
import jenkins.branch.BranchPropertyDescriptor;
import jenkins.branch.MultiBranchProjectDescriptor;
import net.jcip.annotations.Immutable;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * A property that describes the promotion processes that are associated with the branch.
 *
 * @author Stephen Connolly
 */
@Immutable
public class LiteratePromotionsBranchProperty extends BranchProperty {

    @NonNull
    private final List<Promotion> promotions;

    @DataBoundConstructor
    public LiteratePromotionsBranchProperty(Promotion[] promotions) {
        this.promotions = promotions == null ? Collections.<Promotion>emptyList() : new ArrayList<Promotion>(
                Arrays.asList(promotions));
    }

    @NonNull
    @Override
    public <JobT extends Job<?, ?>> List<JobProperty<? super JobT>> configureJobProperties(
            @NonNull List<JobProperty<? super JobT>> properties) {
        List<JobProperty<? super JobT>> result = new ArrayList<JobProperty<? super JobT>>(properties);
        // TODO cast seems ugly... but would prefer to avoid adding type 3 params to BranchProperty just to avoid cast
        result.add((JobProperty<? super JobT>) new JobPropertyImpl(promotions));
        return result;
    }

    @NonNull
    public List<Promotion> getPromotions() {
        return Collections.unmodifiableList(promotions);
    }

    @Extension
    public static class DescriptorImpl extends BranchPropertyDescriptor {

        @Override
        protected boolean isApplicable(@NonNull MultiBranchProjectDescriptor projectDescriptor) {
            return LiterateBranchProject.class.isAssignableFrom(projectDescriptor.getClazz());
        }

        @Override
        public String getDisplayName() {
            return Messages.LiteratePromotionsBranchProperty_DisplayName();
        }
    }

    @Immutable
    public static class Promotion extends AbstractDescribableImpl<Promotion> implements Serializable, ModelObject {
        @NonNull
        private final String name;
        @CheckForNull
        private final String displayName;
        @CheckForNull
        private final String environment;

        @DataBoundConstructor
        public Promotion(String name, String displayName, String environment) {
            this.name = name;
            this.displayName = displayName;
            this.environment = environment;
        }

        @NonNull
        public String getName() {
            return name;
        }

        @NonNull
        public String getDisplayName() {
            return StringUtils.defaultIfBlank(displayName, name);
        }

        @CheckForNull
        public String getEnvironment() {
            return environment;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Promotion promotion = (Promotion) o;

            if (environment != null ? !environment.equals(promotion.environment) : promotion.environment != null) {
                return false;
            }
            if (!name.equals(promotion.name)) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = name.hashCode();
            result = 31 * result + (environment != null ? environment.hashCode() : 0);
            return result;
        }

        @Extension
        public static class DescriptorImpl extends Descriptor<Promotion> {

            @Override
            public String getDisplayName() {
                return "Promotion process";
            }
        }
    }

    @Immutable
    public static class JobPropertyImpl extends JobProperty<LiterateBranchProject> {

        private final List<Promotion> promotions;

        public JobPropertyImpl(@CheckForNull List<Promotion> promotions) {
            this.promotions = promotions == null ? new ArrayList<Promotion>() : new ArrayList<Promotion>(promotions);
        }

        @Override
        public Collection<? extends Action> getJobActions(LiterateBranchProject job) {
            if (promotions.isEmpty()) {
                return Collections.emptyList();
            } else {
                return Collections.singleton(new PromotionAction());
            }
        }

        @Extension
        public static class DescriptorImpl extends JobPropertyDescriptor {

            @Override
            public boolean isApplicable(Class<? extends Job> jobType) {
                return LiterateBranchProject.class.isAssignableFrom(jobType);
            }

            @Override
            public String getDisplayName() {
                return "";  // don't need this descriptor for the UI
            }
        }

        public class PromotionAction implements Action {

            // TODO whatever you want

            public String getIconFileName() {
                return "star.png";
            }

            public String getDisplayName() {
                return "Promotions";
            }

            public String getUrlName() {
                return "promotions";
            }
        }

    }

}
