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
import hudson.model.AbstractDescribableImpl;
import hudson.model.Label;
import hudson.tools.ToolInstallation;

import java.util.List;

/**
 * Maps {@link BuildEnvironment#getComponents()} to a {@link hudson.model.Label} and
 * {@link hudson.tools.ToolInstallation}s.
 *
 * @author Stephen Connolly
 */
public abstract class BuildEnvironmentMapper extends AbstractDescribableImpl<BuildEnvironmentMapper> {

    /**
     * Takes a set of component labels and returns a list of {@link ToolInstallation} instances for all those component
     * labels that have a corresponding {@link ToolInstallation}.
     *
     * @param environment the environment.
     * @return a list of {@link ToolInstallation} instances. The list may be empty, for example if all the component
     *         labels are resolving into the {@link Label}.
     */
    @NonNull
    public abstract List<ToolInstallation> getToolInstallations(@NonNull BuildEnvironment environment);

    /**
     * Takes a set of component labels and returns a {@link Label} which matches to subset of component labels that
     * have a corresponding {@link Label} (or perhaps more correctly, do not have a corresponding
     * {@link ToolInstallation}).
     *
     * @param environment the environment.
     * @return the {@link Label}.
     */
    @CheckForNull
    public abstract Label getLabel(@NonNull BuildEnvironment environment);

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public BuildEnvironmentMapperDescriptor getDescriptor() {
        return (BuildEnvironmentMapperDescriptor) super.getDescriptor();
    }
}
