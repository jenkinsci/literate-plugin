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

import edu.umd.cs.findbugs.annotations.*;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Action;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.model.ModelObject;
import jenkins.branch.BranchPropertyDescriptor;
import jenkins.branch.MultiBranchProjectDescriptor;
import jenkins.branch.ProjectDecorator;
import net.jcip.annotations.Immutable;
import org.apache.commons.lang.StringUtils;
import org.cloudbees.literate.api.v1.ProjectModel;
import org.cloudbees.literate.api.v1.ProjectModelRequest;
import org.cloudbees.literate.api.v1.TaskCommands;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * A property that describes the promotion processes that are associated with the branch.
 *
 * @author Stephen Connolly
 */
@Immutable
public class LiteratePromotionsBranchProperty extends LiterateBranchProperty {

    @NonNull
    private final List<Promotion> processes;

    @DataBoundConstructor
    public LiteratePromotionsBranchProperty(Promotion[] processes) {
        this.processes = processes == null ? Collections.<Promotion>emptyList() : new ArrayList<Promotion>(
                Arrays.asList(processes));
    }

    @Override
    public void projectModelRequest(ProjectModelRequest.Builder builder) {
        for (Promotion p : processes) {
            builder.addTaskId(p.getName());
        }
    }

    @Override
    public ProjectDecorator<LiterateBranchProject, LiterateBranchBuild> branchDecorator() {
        return new ProjectDecorator<LiterateBranchProject, LiterateBranchBuild>() {
            @NonNull
            @Override
            public List<JobProperty<? super LiterateBranchProject>> jobProperties(
                    @NonNull List<JobProperty<? super LiterateBranchProject>> jobProperties) {
                List<JobProperty<? super LiterateBranchProject>> result = asArrayList(jobProperties);
                for (Iterator<JobProperty<? super LiterateBranchProject>> iterator = result.iterator();
                     iterator.hasNext(); ) {
                    JobProperty<? super LiterateBranchProject> p = iterator.next();
                    if (p instanceof JobPropertyImpl) {
                        iterator.remove();
                    }
                }
                result.add(new JobPropertyImpl(processes));
                return result;
            }
        };
    }

    @NonNull
    @edu.umd.cs.findbugs.annotations.SuppressWarnings(value = "RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE",
            justification = "migration of legacy data")
    public List<Promotion> getProcesses() {
        return processes == null ? Collections.<Promotion>emptyList() : Collections.unmodifiableList(processes);
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

        @NonNull
        private final List<Promotion> processes;

        @CheckForNull
        private transient Collection<PromotionProjectAction> jobActions;

        public JobPropertyImpl(@CheckForNull List<Promotion> processes) {
            this.processes = processes == null ? new ArrayList<Promotion>() : new ArrayList<Promotion>(processes);
        }

        @Override
        @edu.umd.cs.findbugs.annotations.SuppressWarnings(value = "RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE",
                justification = "migration of legacy data")
        public Collection<? extends Action> getJobActions(LiterateBranchProject job) {
            if (jobActions == null) {
                if (processes == null || processes.isEmpty()) {
                    jobActions = Collections.emptyList();
                } else {
                    jobActions = Collections.singleton(new PromotionProjectAction(this));
                }
            }
            return jobActions;
        }

        public LiterateBranchProject getOwner() {
            return owner;
        }

        @edu.umd.cs.findbugs.annotations.SuppressWarnings(value = "RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE",
                justification = "migration of legacy data")
        public List<Promotion> getProcesses() {
            return processes == null ? Collections.<Promotion>emptyList() : Collections.unmodifiableList(processes);
        }

        public ProjectModel getModel(LiterateBranchBuild build) {
            ProjectModelAction action = build.getAction(ProjectModelAction.class);
            return action == null ? null : action.getModel();
        }

        public TaskCommands getTask(LiterateBranchBuild build, Promotion promotion) {
            ProjectModel model = getModel(build);
            if (model == null) {
                return null;
            }
            return model.getTask(promotion.getName());
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

    }

    public static class PromotionProjectAction implements Action {

        private final JobPropertyImpl property;

        public PromotionProjectAction(JobPropertyImpl property) {
            this.property = property;
        }

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

        public JobPropertyImpl getJobProperty() {
            return property;
        }

        public LiterateBranchProject getOwner() {
            return property.getOwner();
        }
    }

}
