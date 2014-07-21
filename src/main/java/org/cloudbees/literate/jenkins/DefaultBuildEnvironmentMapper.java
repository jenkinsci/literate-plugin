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
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.labels.LabelAtom;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import jenkins.model.Jenkins;

import java.util.ArrayList;
import java.util.List;

/**
 * The default {@link BuildEnvironmentMapper} which just maps component labels straight to the names of the
 * {@link ToolInstallation}s and if any fail to match it assumes they are labels.
 *
 * @author Stephen Connolly
 */
public class DefaultBuildEnvironmentMapper extends BuildEnvironmentMapper {

    @Override
    public void buildEnvVars(@NonNull BuildEnvironment environment, EnvVars envVars) {
        for (String component : environment.getComponents()) {
            if(component.contains("=")) {
                envVars.addLine(component);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    public List<ToolInstallation> getToolInstallations(@NonNull BuildEnvironment environment) {
        List<ToolInstallation> result = new ArrayList<ToolInstallation>();
        for (String component : environment.getComponents()) {
            descriptors:
            for (Descriptor<ToolInstallation> d : Jenkins.getInstance().getDescriptorList(ToolInstallation.class)) {
                if (d instanceof ToolDescriptor) {
                    final ToolDescriptor descriptor = (ToolDescriptor) d;
                    for (ToolInstallation t : descriptor.getInstallations()) {
                        if (component.equalsIgnoreCase(t.getName())) {
                            result.add(t);
                            break descriptors;
                        }
                    }
                }
            }
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @CheckForNull
    public Label getLabel(@NonNull BuildEnvironment environment) {
        Label result = null;
        for (String component : environment.getComponents()) {
            if(component.contains("=")) continue;
            boolean isToolInstallation = false;
            descriptors:
            for (Descriptor<ToolInstallation> d : Jenkins.getInstance().getDescriptorList(ToolInstallation.class)) {
                if (d instanceof ToolDescriptor) {
                    final ToolDescriptor descriptor = (ToolDescriptor) d;
                    for (ToolInstallation t : descriptor.getInstallations()) {
                        if (component.equalsIgnoreCase(t.getName())) {
                            isToolInstallation = true;
                            break descriptors;
                        }
                    }
                }
            }
            if (!isToolInstallation) {
                LabelAtom labelAtom = LabelAtom.get(component);
                if (labelAtom == null) {
                    continue;
                }
                if (result == null) {
                    result = labelAtom;
                } else {
                    result = result.and(labelAtom);
                }
            }
        }
        return result;
    }

    /**
     * Our descriptor.
     */
    @Extension
    @SuppressWarnings("unused") // instantiated by Jenkins
    public static class DescriptorImpl extends BuildEnvironmentMapperDescriptor {

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return "Default";
        }
    }

}
