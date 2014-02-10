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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.JobProperty;
import jenkins.branch.BranchPropertyDescriptor;
import jenkins.branch.MultiBranchProjectDescriptor;
import jenkins.branch.ProjectDecorator;
import net.jcip.annotations.Immutable;
import org.cloudbees.literate.api.v1.ProjectModelRequest;
import org.cloudbees.literate.jenkins.LiterateBranchBuild;
import org.cloudbees.literate.jenkins.LiterateBranchProject;
import org.cloudbees.literate.jenkins.LiterateBranchProperty;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

/**
 * A property that describes the promotion processes that are associated with the branch.
 *
 * @author Stephen Connolly
 */
@Immutable
public class PromotionBranchProperty extends LiterateBranchProperty {

    private static final Logger LOGGER = Logger.getLogger(PromotionBranchProperty.class.getName());
    @NonNull
    private final List<PromotionConfiguration> configurations;

    @DataBoundConstructor
    public PromotionBranchProperty(PromotionConfiguration[] configurations) {
        this.configurations = configurations == null
                ? Collections.<PromotionConfiguration>emptyList()
                : new ArrayList<PromotionConfiguration>(
                        Arrays.asList(configurations));
    }

    @Override
    public void projectModelRequest(ProjectModelRequest.Builder builder) {
        for (PromotionConfiguration p : configurations) {
            builder.addTaskId(p.getName().toLowerCase());
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
                    if (p instanceof PromotionJobProperty) {
                        iterator.remove();
                    }
                }
                result.add(new PromotionJobProperty(configurations));
                return result;
            }
        };
    }

    @NonNull
    @edu.umd.cs.findbugs.annotations.SuppressWarnings(value = "RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE",
            justification = "migration of legacy data")
    public List<PromotionConfiguration> getConfigurations() {
        return configurations == null ? Collections.<PromotionConfiguration>emptyList() : Collections.unmodifiableList(
                configurations);
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

}
