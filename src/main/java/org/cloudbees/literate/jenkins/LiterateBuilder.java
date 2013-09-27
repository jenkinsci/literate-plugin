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

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.tasks.Shell;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.cloudbees.literate.api.v1.ExecutionEnvironment;
import org.cloudbees.literate.api.v1.ProjectModel;
import org.cloudbees.literate.api.v1.ProjectModelBuildingException;
import org.cloudbees.literate.api.v1.ProjectModelRequest;
import org.cloudbees.literate.api.v1.ProjectModelSource;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * A builder that parses a literate build description and runs a specific execution.
 *
 * @author Stephen Connolly
 */
public class LiterateBuilder extends Builder {

    private final String baseName;

    private final Set<String> environment;

    @DataBoundConstructor
    public LiterateBuilder(String baseName, String environment) {
        this.baseName = Util.fixEmptyAndTrim(baseName);
        this.environment =
                new TreeSet<String>(Arrays.asList(StringUtils.split(StringUtils.defaultString(environment), ",")));
    }

    public String getBaseName() {
        return baseName;
    }

    public String getEnvironment() {
        return StringUtils.join(environment, ", ");
    }

    /**
     * Fix CR/LF and always make it Unix style.
     */
    private static String fixCrLf(String s, boolean wantCRs) {
        // eliminate CR
        int idx;
        while ((idx = s.indexOf("\r\n")) != -1) {
            s = s.substring(0, idx) + s.substring(idx + 1);
        }

        // add CR back if this is for Windows
        if (wantCRs) {
            idx = 0;
            while (true) {
                idx = s.indexOf('\n', idx);
                if (idx == -1) {
                    break;
                }
                s = s.substring(0, idx) + '\r' + s.substring(idx);
                idx += 2;
            }
        }
        return s;
    }

    /**
     * Older versions of bash have a bug where non-ASCII on the first line
     * makes the shell think the file is a binary file and not a script. Adding
     * a leading line feed works around this problem.
     */
    private static String addCrForNonASCII(String s) {
        if (!s.startsWith("#!")) {
            if (s.indexOf('\n') != 0) {
                return "\n" + s;
            }
        }

        return s;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws InterruptedException, IOException {
        FilePath ws = build.getWorkspace();
        assert ws != null : "This method is called from a build, so must have a workspace";
        final FilePathRepository repo = new FilePathRepository(ws);
        ProjectModel model;
        try {
            model = new ProjectModelSource(LiterateBuilder.class.getClassLoader())
                    .submit(
                            ProjectModelRequest.builder(repo)
                                    .withBaseName(baseName)
                                    .build()
                    );
        } catch (ProjectModelBuildingException e) {
            AbortException ae = new AbortException(e.getMessage());
            ae.initCause(e);
            throw ae;
        }

        EnvVars envVars = build.getEnvironment(listener);

        return perform(build, launcher, listener, envVars, model, new ExecutionEnvironment(environment));
    }

    public static boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener,
                                  EnvVars envVars, ProjectModel model, ExecutionEnvironment environment)
            throws IOException, InterruptedException {
        List<String> commands = model.getBuildFor(environment);
        if (commands == null || commands.isEmpty()) {
            throw new AbortException("No build command defined for environment: " + environment.getLabels());
        }

        String extension = launcher.isUnix() ? ".sh" : ".bat";
        for (String command : commands) {
            String contents;
            if (launcher.isUnix()) {
                contents = addCrForNonASCII(fixCrLf(command, false));
            } else {
                contents = fixCrLf(command, true) + "\r\nexit %ERRORLEVEL%";
            }

            FilePath script = null;
            try {
                FilePath ws = build.getWorkspace();
                assert ws != null : "This method is called from a build, so must have a workspace";
                script = ws.createTextTempFile("jenkins", extension, contents, false);

                String[] commandLine;
                if (launcher.isUnix()) {
                    if (command.startsWith("#!")) {
                        // interpreter override
                        int end = command.indexOf('\n');
                        if (end < 0) {
                            end = command.length();
                        }
                        List<String> args = new ArrayList<String>();
                        args.addAll(Arrays.asList(Util.tokenize(command.substring(0, end).trim())));
                        args.add(script.getRemote());
                        args.set(0, args.get(0).substring(2));   // trim off "#!"
                        commandLine = args.toArray(new String[args.size()]);
                    } else {
                        Shell.DescriptorImpl shellDescriptor =
                                Jenkins.getInstance().getDescriptorByType(Shell.DescriptorImpl.class);
                        commandLine =
                                new String[]{
                                        shellDescriptor.getShellOrDefault(ws.getChannel()),
                                        "-xe",
                                        script.getRemote()
                                };
                    }
                } else {
                    commandLine = new String[]{
                            "cmd",
                            "/c",
                            "call",
                            script.getRemote()
                    };
                }
                int r;
                try {
                    // on Windows environment variables are converted to all upper case,
                    // but no such conversions are done on Unix, so to make this cross-platform,
                    // convert variables to all upper cases.
                    for (Map.Entry<String, String> e : build.getBuildVariables().entrySet()) {
                        envVars.put(e.getKey(), e.getValue());
                    }

                    r = launcher.launch().cmds(commandLine).envs(envVars).stdout(listener).pwd(ws).join();
                } catch (IOException e) {
                    Util.displayIOException(e, listener);
                    e.printStackTrace(listener.fatalError(hudson.tasks.Messages.CommandInterpreter_CommandFailed()));
                    r = -1;
                }
                if (r != 0) {
                    return false;
                }
            } finally {
                try {
                    if (script != null) {
                        script.delete();
                    }
                } catch (IOException e) {
                    Util.displayIOException(e, listener);
                    e.printStackTrace(
                            listener.fatalError(hudson.tasks.Messages.CommandInterpreter_UnableToDelete(script)));
                } catch (Exception e) {
                    e.printStackTrace(
                            listener.fatalError(hudson.tasks.Messages.CommandInterpreter_UnableToDelete(script)));
                }
            }
        }
        return true;
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        public String getDisplayName() {
            return Messages.LiterateBuilder_DisplayName();
        }

        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }
    }

}
