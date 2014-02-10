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
package org.cloudbees.literate.jenkins.publishers;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.BuildListener;
import hudson.tasks.ArtifactArchiver;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;

/**
 * An {@link Agent} that archives artifacts based on a simple list of Globs file.
 */
@Extension
public class ArtifactsAgent extends Agent<ArtifactArchiver> {
    /**
     * Constructor.
     */
    @SuppressWarnings("unused") // instantiated by Jenkins
    public ArtifactsAgent() {
        super(ArtifactArchiver.class);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public String getConfigurationFilename() {
        return "artifacts.lst";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ArtifactArchiver getPublisher(@NonNull BuildListener listener, @NonNull FilePath configurationFile)
            throws IOException {
        StringBuilder globs = new StringBuilder();
        String config = configurationFile.readToString();
        boolean first = true;
        if (StringUtils.isNotBlank(config)) {
            for (String glob : StringUtils.split(config, "\n\r")) {
                if (StringUtils.isBlank(glob)) {
                    continue;
                }
                if (glob.startsWith("#")) {
                    continue;
                }
                log(listener, "    " + glob);
                if (first) {
                    first = false;
                } else {
                    globs.append(',');
                }
                globs.append(glob);
            }
        }
        if (first) {
            log(listener, "Disabled: configuration file is empty");
            return null;
        }
        return new ArtifactArchiver(globs.toString(), null, false, true);
    }
}
