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

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Items;
import hudson.model.Node;
import hudson.model.Result;
import hudson.slaves.WorkspaceList;
import hudson.tasks.Publisher;
import hudson.tasks.Shell;
import hudson.tools.ToolInstallation;
import jenkins.branch.Branch;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.cloudbees.literate.api.v1.ExecutionEnvironment;
import org.cloudbees.literate.api.v1.ProjectModel;
import org.cloudbees.literate.api.v1.ProjectModelRequest;
import org.cloudbees.literate.api.v1.ProjectModelSource;
import org.cloudbees.literate.jenkins.publishers.Agent;
import org.cloudbees.literate.jenkins.publishers.DefaultXmlAgent;
import org.kohsuke.stapler.Ancestor;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

import static hudson.model.Result.FAILURE;

/**
 * A build of a {@link LiterateEnvironmentProject}.
 *
 * @author Stephen Connolly
 */
public class LiterateEnvironmentBuild extends Build<LiterateEnvironmentProject, LiterateEnvironmentBuild> {

    /**
     * The directory that contains the plugin configuration files.
     */
    public static final String PLUGIN_CONFIG_DIR = ".jenkins";

    /**
     * Constructor.
     *
     * @param job       the parent.
     * @param timestamp the timestamp.
     */
    public LiterateEnvironmentBuild(LiterateEnvironmentProject job, Calendar timestamp) {
        super(job, timestamp);
    }

    /**
     * Constructor.
     *
     * @param project the parent.
     */
    public LiterateEnvironmentBuild(LiterateEnvironmentProject project) throws IOException {
        super(project);
    }

    /**
     * Constructor.
     *
     * @param project  the parent.
     * @param buildDir the directory where the build record is stored.
     */
    public LiterateEnvironmentBuild(LiterateEnvironmentProject project, File buildDir) throws IOException {
        super(project, buildDir);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getUpUrl() {
        StaplerRequest req = Stapler.getCurrentRequest();
        if (req != null) {
            List<Ancestor> ancs = req.getAncestors();
            for (int i = 1; i < ancs.size(); i++) {
                if (ancs.get(i).getObject() == this) {
                    Object parentObj = ancs.get(i - 1).getObject();
                    if (parentObj instanceof LiterateBranchBuild || parentObj instanceof LiterateEnvironmentProject) {
                        return ancs.get(i - 1).getUrl() + '/';
                    }
                }
            }
        }
        return super.getDisplayName();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDisplayName() {
        StaplerRequest req = Stapler.getCurrentRequest();
        if (req != null) {
            List<Ancestor> ancs = req.getAncestors();
            for (int i = 1; i < ancs.size(); i++) {
                if (ancs.get(i).getObject() == this) {
                    if (ancs.get(i - 1).getObject() instanceof LiterateBranchBuild) {
                        return getParent().getEnvironment().getName();
                    }
                }
            }
        }
        return super.getDisplayName();
    }

    /**
     * Gets the parent {@link LiterateBranchBuild}.
     *
     * @return the parent {@link LiterateBranchBuild}.
     */
    public LiterateBranchBuild getParentBuild() {
        return getParent().getParent().getBuildByNumber(getNumber());
    }

    /**
     * The short url for this build.
     *
     * @return the short url for this build.
     */
    public String getShortUrl() {
        return Util.rawEncode(getParent().getEnvironment().toString());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AbstractBuild<?, ?> getRootBuild() {
        return getParentBuild();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LiterateEnvironmentProject getParent() {
        return super.getParent();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run() {
        execute(new RunnerImpl());
    }

    /**
     * Our runner.
     */
    protected class RunnerImpl extends AbstractRunner {
        /**
         * The publishers we will use.
         */
        private List<Publisher> publishers = new ArrayList<Publisher>();

        /**
         * Fix CR/LF and always make it Unix style.
         */
        private String fixCrLf(String s) {
            // eliminate CR
            int idx;
            while ((idx = s.indexOf("\r\n")) != -1) {
                s = s.substring(0, idx) + s.substring(idx + 1);
            }

            //// add CR back if this is for Windows
            //if(isWindows()) {
            //    idx=0;
            //    while(true) {
            //        idx = s.indexOf('\n',idx);
            //        if(idx==-1) break;
            //        s = s.substring(0,idx)+'\r'+s.substring(idx);
            //        idx+=2;
            //    }
            //}
            return s;
        }

        /**
         * Older versions of bash have a bug where non-ASCII on the first line
         * makes the shell think the file is a binary file and not a script. Adding
         * a leading line feed works around this problem.
         */
        private String addCrForNonASCII(String s) {
            if (!s.startsWith("#!")) {
                if (s.indexOf('\n') != 0) {
                    return "\n" + s;
                }
            }

            return s;
        }

        public String[] buildCommandLine(FilePath script, String command) {
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
                return args.toArray(new String[args.size()]);
            } else {
                return new String[]{
                        Jenkins.getInstance().getDescriptorByType(
                                Shell.DescriptorImpl.class).getShellOrDefault(
                                script.getChannel()), "-xe", script.getRemote()
                };
            }
        }

        protected String getContents(String command) {
            return addCrForNonASCII(fixCrLf(command));
        }

        protected String getFileExtension() {
            return ".sh";
        }

        /**
         * Creates a script file in a temporary name in the specified directory.
         */
        public FilePath createScriptFile(FilePath dir, String command) throws IOException, InterruptedException {
            return dir.createTextTempFile("jenkins", getFileExtension(), getContents(command), false);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected Result doRun(final BuildListener listener) throws Exception, RunnerAbortedException {
            FilePath ws = getWorkspace();
            assert ws != null : "we are in a build so must have a workspace";
            final FilePathRepository repo = new FilePathRepository(ws);
            ProjectModel model = new ProjectModelSource(LiterateBranchProject.class.getClassLoader()).submit(
                    ProjectModelRequest.builder(
                            repo).build());
            Map<Class<? extends Publisher>, List<Agent<? extends Publisher>>> agents = new LinkedHashMap<Class<?
                    extends Publisher>, List<Agent<? extends Publisher>>>();
            for (Agent agent : Jenkins.getInstance().getExtensionList(Agent.class)) {
                Class clazz = agent.getPublisherClass();
                List<Agent<? extends Publisher>> list = agents.get(clazz);
                if (list == null) {
                    list = new ArrayList<Agent<? extends Publisher>>();
                    agents.put(clazz, list);
                }
                list.add(agent);
            }
            for (Descriptor<Publisher> d : Publisher.all()) {
                List<Agent<? extends Publisher>> list = agents.get(d.clazz);
                if (list == null) {
                    list = new ArrayList<Agent<? extends Publisher>>();
                    agents.put(d.clazz, list);
                }
                list.add(DefaultXmlAgent.fromDescriptor(d));
                agents.put(d.clazz, list);
            }
            List<String> profiles = ((LiterateEnvironmentProject) getProject()).getGrandparent().getProfileList();
            FilePath configDir = ws.child(PLUGIN_CONFIG_DIR);
            for (Map.Entry<Class<? extends Publisher>, List<Agent<? extends Publisher>>> entry : agents.entrySet()) {
                Publisher currentPublisher = null;
                Agent currentAgent = null;
                boolean merge = false;
                for (Agent agent : entry.getValue()) {
                    FilePath configurationFile = null;
                    String configurationFileName = null;
                    for (String profile : profiles) {
                        FilePath fp = configDir.child(profile).child(agent.getConfigurationFilename());
                        if (fp.exists()) {
                            configurationFile = fp;
                            configurationFileName =
                                    PLUGIN_CONFIG_DIR + "/" + profile + "/" + agent.getConfigurationFilename();
                            break;
                        }
                    }
                    if (configurationFile == null) {
                        // fall back to default
                        FilePath fp = configDir.child(agent.getConfigurationFilename());
                        if (fp.exists()) {
                            configurationFile = fp;
                            configurationFileName = PLUGIN_CONFIG_DIR + "/" + agent.getConfigurationFilename();
                        }
                    }
                    if (configurationFile != null) {
                        agent.log(listener, "Reading configuration from {0}", configurationFileName);
                        Publisher publisher = agent.getPublisher(listener, configurationFile);
                        if (currentAgent == null) {
                            currentAgent = agent;
                            currentPublisher = publisher;
                        } else {
                            currentAgent.log(listener, "Merging configuration provided by {0}", agent.getDisplayName());
                            currentPublisher = currentAgent.merge(listener, currentPublisher, publisher);
                            merge = true;
                        }
                    }
                }
                if (currentPublisher != null) {
                    if (merge) {
                        currentAgent.log(listener, "Final effective configuration (as .xml):");
                        StringWriter sw = new StringWriter();
                        try {
                            Items.XSTREAM.toXML(currentPublisher, sw);
                        } finally {
                            sw.close();
                        }
                        for (String line : sw.toString().split("[\n\r]")) {
                            if (StringUtils.isBlank(line)) {
                                continue;
                            }
                            while (line.endsWith("\r") || line.endsWith("\n")) {
                                line = line.substring(0, line.length() - 1);
                            }
                            if (StringUtils.isBlank(line)) {
                                continue;
                            }
                            currentAgent.log(listener, "    " + line);
                        }

                    }
                    publishers.add(currentPublisher);
                } else if (merge) {
                    currentAgent.log(listener, "Final effective configuration: Disabled");
                }
            }
            // TODO JobProperty and BuildWrapper support
            Branch branch = getParent().getParent().getBranch();
            publishers = new ArrayList<Publisher>(branch.configurePublishers(Descriptor.toMap(publishers)).values());
            final Launcher launcher = createLauncher(listener);
            EnvVars envVars = getEnvironment(listener);
            BuildEnvironment buildEnvironment = getParent().getEnvironment();
            BuildEnvironmentMapper environmentMapper = getParent().getGrandparent().getEnvironmentMapper();
            for (ToolInstallation installation : environmentMapper.getToolInstallations(buildEnvironment)) {
                installation = installation.translate(LiterateEnvironmentBuild.this, listener);
                installation.buildEnvVars(envVars);
            }
            SortedSet<String> components = buildEnvironment.getComponents();
            String command = model.getBuildFor(new ExecutionEnvironment(components));
            if (command == null) {
                listener.getLogger().println(Messages.LiterateEnvironmentBuild_CommandMissing(components));
                return Result.FAILURE;
            }
            FilePath script = null;
            try {
                try {
                    script = createScriptFile(ws, command);
                } catch (IOException e) {
                    Util.displayIOException(e, listener);
                    e.printStackTrace(
                            listener.fatalError(hudson.tasks.Messages.CommandInterpreter_UnableToProduceScript()));
                    return Result.FAILURE;
                }

                int r;
                try {
                    // TODO windows support
                    r = launcher.launch().cmds(buildCommandLine(script, command)).envs(envVars).stdout(listener).pwd(ws)
                            .join();
                } catch (IOException e) {
                    Util.displayIOException(e, listener);
                    e.printStackTrace(
                            listener.fatalError(hudson.tasks.Messages.CommandInterpreter_CommandFailed()));
                    r = -1;
                }
                return r == 0 ? Result.SUCCESS : Result.FAILURE;
            } finally {
                try {
                    if (script != null) {
                        script.delete();
                    }
                } catch (IOException e) {
                    Util.displayIOException(e, listener);
                    e.printStackTrace(
                            listener.fatalError(hudson.tasks.Messages.CommandInterpreter_UnableToDelete(script)));
                }
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected void post2(BuildListener listener) throws Exception {
            if (!performAllBuildSteps(listener, publishers, true)) {
                setResult(FAILURE);
            }

        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected WorkspaceList.Lease decideWorkspace(Node n, WorkspaceList wsl)
                throws InterruptedException, IOException {
            // TODO: this cast is indicative of abstraction problem
            LiterateEnvironmentProject project = (LiterateEnvironmentProject) getProject();
            return wsl.allocate(n.getWorkspaceFor(project.getParent().getParent())
                    .child(project.getParent().getBranch().getName() + "-" + project.getEnvironment().getName()));
        }

    }
}
