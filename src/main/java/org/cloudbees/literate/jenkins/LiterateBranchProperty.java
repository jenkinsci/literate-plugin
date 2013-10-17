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
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import jenkins.branch.ProjectDecorator;
import jenkins.branch.BranchProperty;
import org.cloudbees.literate.api.v1.ExecutionEnvironment;
import org.cloudbees.literate.api.v1.ProjectModelRequest;

import java.util.List;

/**
 * A {@link BranchProperty} that is specifically for {@link LiterateMultibranchProject}s.
 *
 * @author Stephen Connolly
 */
public abstract class LiterateBranchProperty extends BranchProperty {

    /**
     * This method is an extension point whereby a {@link LiterateBranchProperty} can enhance the
     * {@link ProjectModelRequest} to be used by the {@link LiterateBranchBuild}.
     *
     * @param requestBuilder the builder for the {@link ProjectModelRequest}s.
     */
    public void projectModelRequest(@NonNull ProjectModelRequest.Builder requestBuilder) {
    }

    /**
     * This method is an extension point whereby a {@link LiterateBranchProperty} can restrict/control  the
     * environments that will be built
     *
     * @param environments a possibly immutable list of environments.
     * @return either the provided list of environments or a replacement list.
     */
    @NonNull
    public List<ExecutionEnvironment> environments(@NonNull List<ExecutionEnvironment> environments) {
        return environments;
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public final <P extends AbstractProject<P, B>, B extends AbstractBuild<P, B>> ProjectDecorator<P, B> decorator(
            Class<P> jobType) {
        if (LiterateBranchProject.class.isAssignableFrom(jobType)) {
            return (ProjectDecorator<P, B>) branchDecorator();
        }
        if (LiterateEnvironmentProject.class.isAssignableFrom(jobType)) {
            return (ProjectDecorator<P, B>) environmentDecorator();
        }
        return null;
    }

    @CheckForNull
    public ProjectDecorator<LiterateBranchProject, LiterateBranchBuild> branchDecorator() {
        return null;
    }

    @CheckForNull
    public ProjectDecorator<LiterateEnvironmentProject, LiterateEnvironmentBuild> environmentDecorator() {
        return null;
    }
}
