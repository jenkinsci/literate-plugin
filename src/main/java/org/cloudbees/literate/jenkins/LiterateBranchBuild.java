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
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.FilePath;
import hudson.Functions;
import hudson.console.ModelHyperlinkNote;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.Executor;
import hudson.model.Node;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.StringParameterValue;
import hudson.slaves.WorkspaceList;
import hudson.util.HttpResponses;
import hudson.util.IOUtils;
import jenkins.branch.BranchProperty;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.cloudbees.literate.api.v1.ExecutionEnvironment;
import org.cloudbees.literate.api.v1.Parameter;
import org.cloudbees.literate.api.v1.ProjectModel;
import org.cloudbees.literate.api.v1.ProjectModelRequest;
import org.cloudbees.literate.api.v1.ProjectModelSource;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static hudson.model.Result.FAILURE;

/**
 * A build of a literate branch project.
 *
 * @author Stephen Connolly
 * @todo when there is only one environment, collapse the UI so that you browse straight to the environment
 * @todo when the SCM is supported by a {@link jenkins.scm.api.SCMFileSystem}, use that rather than checking out
 */
public class LiterateBranchBuild extends Build<LiterateBranchProject, LiterateBranchBuild> {

    /**
     * The environments that will be built for this branch.
     */
    @CheckForNull
    private Set<BuildEnvironment> environments;

    /**
     * Constructor.
     *
     * @param project our parent.
     * @throws IOException if things go wrong.
     */
    public LiterateBranchBuild(LiterateBranchProject project) throws IOException {
        super(project);
    }

    /**
     * Contructor.
     *
     * @param project  our parent.
     * @param buildDir the data directory.
     * @throws IOException if things go wrong.
     */
    public LiterateBranchBuild(LiterateBranchProject project, File buildDir) throws IOException {
        super(project, buildDir);
    }

    /**
     * Returns our build environments.
     *
     * @return our build environments.
     */
    @NonNull
    public Collection<BuildEnvironment> getBuildEnvironments() {
        return environments == null ? Collections.<BuildEnvironment>emptySet() : environments;
    }

    /**
     * Returns the child environment builds.
     *
     * @return the child environment builds.
     */
    @NonNull
    public List<LiterateEnvironmentBuild> getItems() {
        List<LiterateEnvironmentBuild> result = new ArrayList<LiterateEnvironmentBuild>();
        for (BuildEnvironment environment : getBuildEnvironments()) {
            LiterateEnvironmentProject config = project.getEnvironment(environment);
            if (config != null) {
                LiterateEnvironmentBuild run = config.getBuildByNumber(getNumber());
                if (run != null) {
                    result.add(run);
                }
            }
        }
        return result;
    }

    /**
     * Returns a named environment build.
     *
     * @param token the named environment.
     * @return the build.
     */
    @CheckForNull
    public LiterateEnvironmentBuild getItem(String token) {
        LiterateEnvironmentProject config = project.getItem(token);
        if (config != null) {
            return config.getBuildByNumber(getNumber());
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getDynamic(String token, StaplerRequest req, StaplerResponse rsp) {
        try {
            LiterateEnvironmentBuild item = getItem(token);
            if (item != null) {
                if (item.getNumber() == this.getNumber()) {
                    return item;
                } else {
                    // redirect the user to the correct URL
                    String url = Functions.joinPath(item.getUrl(), req.getRestOfPath());
                    String qs = req.getQueryString();
                    if (qs != null) {
                        url += '?' + qs;
                    }
                    throw HttpResponses.redirectViaContextPath(url);
                }
            }
        } catch (IllegalArgumentException _) {
            // failed to parse the token as Combination. Must be something else
        }
        return super.getDynamic(token, req, rsp);
    }

    /**
     * Deletes the build and all matrix configurations in this build when the button is pressed.
     */
    @RequirePOST
    @SuppressWarnings("unused") // called by stapler.
    public void doDoDeleteAll(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        checkPermission(DELETE);

        // We should not simply delete the build if it has been explicitly
        // marked to be preserved, or if the build should not be deleted
        // due to dependencies!
        String why = getWhyKeepLog();
        if (why != null) {
            sendError(hudson.model.Messages.Run_UnableToDelete(toString(), why), req, rsp);
            return;
        }

        List<LiterateEnvironmentBuild> runs = getItems();
        for (LiterateEnvironmentBuild run : runs) {
            why = run.getWhyKeepLog();
            if (why != null) {
                sendError(hudson.model.Messages.Run_UnableToDelete(toString(), why), req, rsp);
                return;
            }
            run.delete();
        }
        delete();
        rsp.sendRedirect2(req.getContextPath() + '/' + getParent().getUrl());
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
         * {@inheritDoc}
         */
        @Override
        protected Result doRun(final BuildListener listener) throws Exception, RunnerAbortedException {
            FilePath ws = getWorkspace();
            assert ws != null : "we are in a build so must have a workspace";
            final FilePathRepository repo = new FilePathRepository(ws);
            ProjectModelRequest.Builder requestBuilder = ProjectModelRequest.builder(repo);
            requestBuilder.withBaseName(getParent().getParent().getMarkerFile());
            for (BranchProperty p : getParent().getBranch().getProperties()) {
                if (p instanceof LiterateBranchProperty) {
                    LiterateBranchProperty.class.cast(p).projectModelRequest(requestBuilder);
                }
            }
            listener.getLogger().println("Parsing literate build description...");
            ProjectModelSource source = new ProjectModelSource(LiterateBranchProject.class.getClassLoader());
            ProjectModel model = source.submit(requestBuilder.build());
            if (model == null) {
                listener.fatalError("Could not parse literate build description");
                return FAILURE;
            }
            listener.getLogger().println("Literate build description:");
            listener.getLogger().println("Checking " + model.getEnvironments().size() + " execution environments");
            boolean issues = false;
            for (ExecutionEnvironment e : model.getEnvironments()) {
                if (model.getBuildFor(e) == null) {
                    issues = true;
                }
                listener.getLogger()
                        .println(" * " + e.getLabels() + (model.getBuildFor(e) == null ? " missing build" : " ok"));
            }
            if (issues) {
                return FAILURE;
            }

            addAction(new ProjectModelAction(model));

            // TODO refactor so that we get the README that was used for reading the project model
            if (repo.isFile("README.md")) {
                try {
                    addAction(new ReadMeAction(IOUtils.toString(repo.get("README.md"))));
                } catch (Throwable t) {
                    // ignore
                }
            }

            List<ExecutionEnvironment> environmentList = model.getEnvironments();
            for (BranchProperty p : getParent().getBranch().getProperties()) {
                if (p instanceof LiterateBranchProperty) {
                    environmentList = LiterateBranchProperty.class.cast(p).environments(environmentList);
                }
            }
            environments = BuildEnvironment.fromSets(environmentList);
            project.rebuildEnvironments(LiterateBranchBuild.this);

            if (!preBuild(listener, project.getBuilders())) {
                return FAILURE;
            }
            if (!preBuild(listener, project.getPublishersList())) {
                return FAILURE;
            }

            // TODO sequential support builder fast fail as project option distinct from fan-out
            List<LiterateEnvironmentProject> configurations = new ArrayList<LiterateEnvironmentProject>();
            final Run<LiterateBranchProject, LiterateBranchBuild> upstream = LiterateBranchBuild.this;
            ParametersAction originalParametersAction = getAction(ParametersAction.class);
            List<ParameterValue> parameters = new ArrayList<ParameterValue>(originalParametersAction == null
                    ? Collections.<ParameterValue>emptyList()
                    : originalParametersAction.getParameters()
            );
            boolean modifiedParameters = false;
            for (Parameter p : model.getBuild().getParameters().values()) {
                if (p.getDefaultValue() == null) {
                    continue;
                }
                boolean match = false;
                for (ParameterValue v : parameters) {
                    if (StringUtils.equals(p.getName(), v.getName())) {
                        match = true;
                        break;
                    }
                }
                if (!match) {
                    parameters.add(new StringParameterValue(p.getName(), p.getDefaultValue(), p.getDescription()));
                    modifiedParameters = true;
                }
            }
            ParametersAction parametersAction = parameters.isEmpty() ? null : new ParametersAction(parameters);
            if (modifiedParameters && parametersAction != null) {
                if (originalParametersAction != null) {
                    getActions().remove(originalParametersAction);
                }
                addAction(parametersAction);
            }
            for (BuildEnvironment environment : getBuildEnvironments()) {
                LiterateEnvironmentProject c = project.getEnvironment(environment);
                configurations.add(c);
                c.scheduleBuild(parametersAction, new Cause.UpstreamCause(upstream));
            }
            try {
                Result r = Result.SUCCESS;
                for (LiterateEnvironmentProject c : configurations) {
                    listener.getLogger()
                            .println("Waiting for the completion of "
                                    + ModelHyperlinkNote.encodeTo(c, c.getEnvironment().getName())
                                    + "...");
                    LiterateEnvironmentBuild run = waitForCompletion(c);
                    Result runResult = getResult(run);
                    listener.getLogger().println(
                            "Completed " + ModelHyperlinkNote.encodeTo(c, c.getEnvironment().getName()) + " "
                                    + ModelHyperlinkNote.encodeTo(run) + " Result "
                                    + runResult);
                    r = r.combine(runResult);
                }
                return r;
            } finally {
                // if the build was aborted in the middle, cancel all the child builds
                Queue q = Jenkins.getInstance().getQueue();
                synchronized (q) {// avoid micro-locking on Queue#cancel
                    final int n = getNumber();
                    for (LiterateEnvironmentProject c : configurations) {
                        for (Queue.Item i : q.getItems(c)) {
                            ParentLiterateBranchBuildAction
                                    a = i.getAction(ParentLiterateBranchBuildAction.class);
                            if (a != null && a.getParent() == getBuild()) {
                                q.cancel(i);
                                listener.getLogger().println("Cancelled " + ModelHyperlinkNote
                                        .encodeTo(c, c.getEnvironment().getName()));
                            }
                        }
                        LiterateEnvironmentBuild b = c.getBuildByNumber(n);
                        if (b != null && b
                                .isBuilding()) {// executor can spend some time in post production state,
                            // so only cancel in-progress builds.
                            Executor exe = b.getExecutor();
                            if (exe != null) {
                                listener.getLogger().println("Interrupting " + ModelHyperlinkNote
                                        .encodeTo(c, c.getEnvironment().getName()) + " " + ModelHyperlinkNote
                                        .encodeTo(b));
                                exe.interrupt();
                            }
                        }
                    }
                }
            }
        }

        /**
         * Returns the result of a specific run.
         *
         * @param run the run.
         * @return the result
         */
        private Result getResult(@Nullable LiterateEnvironmentBuild run) {
            return run == null ? Result.ABORTED : run.getResult();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected void post2(BuildListener listener) throws Exception {
            if (!performAllBuildSteps(listener, project.getPublishersList(), true)) {
                setResult(FAILURE);
            }
            if (!performAllBuildSteps(listener, project.getProperties(), true)) {
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
            LiterateBranchProject project = (LiterateBranchProject) getProject();
            return wsl.allocate(n.getWorkspaceFor(project.getParent())
                    .child(project.getBranch().getName()));
        }

        /**
         * Waits for a specific project to complete.
         *
         * @param c the project.
         * @return the corresponding build.
         * @throws InterruptedException if interrupted.
         * @throws IOException          if communtication failed.
         */
        private LiterateEnvironmentBuild waitForCompletion(LiterateEnvironmentProject c)
                throws InterruptedException, IOException {
            String whyInQueue = "";
            long startTime = System.currentTimeMillis();

            // wait for the completion
            int appearsCancelledCount = 0;
            while (true) {
                LiterateEnvironmentBuild b = c.getBuildByNumber(getNumber());

                // two ways to get beyond this. one is that the build starts and gets done,
                // or the build gets cancelled before it even started.
                if (b != null && !b.isBuilding()) {
                    Result buildResult = b.getResult();
                    if (buildResult != null) {
                        return b;
                    }
                }
                Queue.Item qi = c.getQueueItem();
                if (b == null && qi == null) {
                    appearsCancelledCount++;
                } else {
                    appearsCancelledCount = 0;
                }

                if (appearsCancelledCount >= 5) {
                    // there's conceivably a race condition in computing b and qi, as their computation
                    // are not synchronized. There are indeed several reports of Hudson incorrectly assuming
                    // builds being cancelled. See
                    // http://www.nabble.com/Master-slave-problem-tt14710987.html and also
                    // http://www.nabble.com/Anyone-using-AccuRev-plugin--tt21634577.html#a21671389
                    // because of this, we really make sure that the build is cancelled by doing this 5
                    // times over 5 seconds
                    listener.getLogger().println("Appears cancelled");
                    return null;
                }

                if (qi != null) {
                    // if the build seems to be stuck in the queue, display why
                    String why = qi.getWhy();
                    if (!why.equals(whyInQueue) && System.currentTimeMillis() - startTime > 5000) {
                        listener.getLogger().println(
                                "Configuration " + ModelHyperlinkNote.encodeTo(c, c.getEnvironment().getName())
                                        + " is still in the queue: " + qi.getCauseOfBlockage().getShortDescription());
                        whyInQueue = why;
                    }
                }

                Thread.sleep(1000);
            }
        }

    }

}
