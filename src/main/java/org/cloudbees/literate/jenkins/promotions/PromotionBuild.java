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

import hudson.EnvVars;
import hudson.console.HyperlinkNote;
import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.Node;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import hudson.security.PermissionScope;
import hudson.slaves.WorkspaceList;
import jenkins.model.Jenkins;
import org.cloudbees.literate.api.v1.ProjectModel;
import org.cloudbees.literate.api.v1.TaskCommands;
import org.cloudbees.literate.jenkins.LiterateBranchBuild;
import org.cloudbees.literate.jenkins.LiterateBuilder;
import org.cloudbees.literate.jenkins.ProjectModelAction;
import org.cloudbees.literate.jenkins.promotions.conditions.ManualCondition;
import org.kohsuke.stapler.export.Exported;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Stephen Connolly
 */
public class PromotionBuild extends AbstractBuild<PromotionProject, PromotionBuild>
        implements Comparable<PromotionBuild> {

    public static final PermissionGroup PERMISSIONS =
            new PermissionGroup(PromotionBuild.class,
                    Messages._LiteratePromotionsBranchProperty_Permissions_Title());
    public static final Permission
            PROMOTE = new Permission(PERMISSIONS, "Promote",
            Messages._LiteratePromotionsBranchProperty_PromotePermission_Description(),
            Jenkins.ADMINISTER, PermissionScope.RUN);

    public PromotionBuild(PromotionProject job) throws IOException {
        super(job);
    }

    public PromotionBuild(PromotionProject job, Calendar timestamp) {
        super(job, timestamp);
    }

    public PromotionBuild(PromotionProject project, File buildDir) throws IOException {
        super(project, buildDir);
    }

    /**
     * Gets the build that this promotion promoted.
     *
     * @return null if there's no such object. For example, if the build has already garbage collected.
     */
    @Exported
    public LiterateBranchBuild getTarget() {
        PromotionTargetAction
                pta = getAction(PromotionTargetAction.class);
        return pta.resolve(this);
    }

    @Override
    public AbstractBuild<?, ?> getRootBuild() {
        return getTarget().getRootBuild();
    }

    @Override
    public String getUrl() {
        return getTarget().getUrl() + "promotion/" + getParent().getName() + "/promotionBuild/" + getNumber() + "/";
    }

    /**
     * Gets the {@link PromotionStatus} object that keeps track of what {@link org.cloudbees.literate.jenkins
     * .PromotionBranchProperty.PromotionBuild}s are
     * performed for a build, including this {@link org.cloudbees.literate.jenkins
     * .PromotionBranchProperty.PromotionBuild}.
     */
    public PromotionStatus getStatus() {
        return getTarget().getAction(PromotionBranchBuildAction.class).getPromotion(getParent().getName());
    }

    @Override
    public EnvVars getEnvironment(TaskListener listener) throws IOException, InterruptedException {
        EnvVars e = super.getEnvironment(listener);

        // Augment environment with target build's information
        String rootUrl = Jenkins.getInstance().getRootUrl();
        AbstractBuild<?, ?> target = getTarget();
        if (rootUrl != null) {
            e.put("PROMOTED_URL", rootUrl + target.getUrl());
        }
        e.put("PROMOTED_JOB_NAME", target.getParent().getName());
        e.put("PROMOTED_JOB_FULL_NAME", target.getParent().getFullName());
        e.put("PROMOTED_NUMBER", Integer.toString(target.getNumber()));
        e.put("PROMOTED_ID", target.getId());
        EnvVars envScm = new EnvVars();
        target.getProject().getScm().buildEnvVars(target, envScm);
        for (Map.Entry<String, String> entry : envScm.entrySet()) {
            e.put("PROMOTED_" + entry.getKey(), entry.getValue());
        }

        // Allow the promotion status to contribute to build environment
        getStatus().buildEnvVars(this, e);

        return e;
    }

    /**
     * @return user's name who triggered the promotion, or 'anonymous'
     */
    public String getUserName() {
        Cause.UserCause userClause = getCause(Cause.UserCause.class);
        if (userClause != null && userClause.getUserName() != null) {
            return userClause.getUserName();
        }
        return "anonymous";
    }

    public List<ParameterValue> getParameterValues() {
        List<ParameterValue> values = new ArrayList<ParameterValue>();
        ParametersAction parametersAction = getParametersActions(this);
        if (parametersAction != null) {
            ManualCondition manualCondition =
                    (ManualCondition) getProject().getPromotionCondition(ManualCondition.class.getName());
            if (manualCondition != null) {
                List<ParameterDefinition> parameterDefinitions =
                        manualCondition.getParameterDefinitions(getProject(), getTarget());
                for (ParameterValue pvalue : parametersAction.getParameters()) {
                    if (ManualCondition.getParameterDefinition(parameterDefinitions, pvalue.getName()) != null) {
                        values.add(pvalue);
                    }
                }
            }
            return values;
        }

        //fallback to badge lookup for compatibility
        for (PromotionBadge badget : getStatus().getBadges()) {
            if (badget instanceof ManualCondition.Badge) {
                return ((ManualCondition.Badge) badget).getParameterValues();
            }
        }
        return Collections.emptyList();
    }

    public List<ParameterDefinition> getParameterDefinitionsWithValue() {
        List<ParameterDefinition> definitions = new ArrayList<ParameterDefinition>();
        ManualCondition manualCondition =
                (ManualCondition) getProject().getPromotionCondition(ManualCondition.class.getName());
        List<ParameterDefinition> parameterDefinitions =
                manualCondition.getParameterDefinitions(getProject(), getTarget());
        for (ParameterValue pvalue : getParameterValues()) {
            ParameterDefinition pdef = ManualCondition.getParameterDefinition(parameterDefinitions, pvalue.getName());
            definitions.add(pdef.copyWithDefaultValue(pvalue));
        }
        return definitions;
    }

    public void run() {
        getStatus().addPromotionAttempt(this);
        run(new RunnerImpl(this));
    }

    @Override
    @edu.umd.cs.findbugs.annotations.SuppressWarnings(value = "EQ_COMPARETO_USE_OBJECT_EQUALS",
            justification = "Matching the behaviour of the promoted builds plugin")
    public int compareTo(PromotionBuild that) {
        return that.getId().compareTo(this.getId());
    }

    protected class RunnerImpl extends AbstractRunner {
        final PromotionBuild promotionRun;

        RunnerImpl(final PromotionBuild promotionRun) {
            this.promotionRun = promotionRun;
        }

        @Override
        protected WorkspaceList.Lease decideWorkspace(Node n, WorkspaceList wsl)
                throws InterruptedException, IOException {
            String customWorkspace = PromotionBuild.this.getProject().getCustomWorkspace();
            if (customWorkspace != null)
            // we allow custom workspaces to be concurrently used between jobs.
            {
                return WorkspaceList.Lease.createDummyLease(
                        n.getRootPath().child(getEnvironment(listener).expand(customWorkspace)));
            }
            return wsl.acquire(n.getWorkspaceFor((TopLevelItem) getTarget().getProject()), true);
        }

        protected Result doRun(BuildListener listener) throws Exception {
            LiterateBranchBuild target = getTarget();

            ProjectModelAction modelAction = target.getAction(ProjectModelAction.class);
            if (modelAction == null) {
                listener.getLogger().println(
                        HyperlinkNote.encodeTo('/' + target.getUrl(), target.getFullDisplayName())
                                + " is missing its project model record"
                );
                return Result.FAILURE;
            }
            ProjectModel model = modelAction.getModel();
            TaskCommands task = model.getTask(getParent().getConfiguration().getName());

            if (task == null) {
                listener.getLogger().println("Project model for " +
                        HyperlinkNote.encodeTo('/' + target.getUrl(), target.getFullDisplayName())
                        + " does not specify any tasks for the "
                        + getParent().getConfiguration().getDisplayName()
                        + " (" + getParent().getConfiguration().getName() + ") promotion"
                );
                return Result.NOT_BUILT;
            }

            listener.getLogger().println(
                    "Promoting " +
                            HyperlinkNote.encodeTo('/' + target.getUrl(), target.getFullDisplayName())
            );

            // start with SUCCESS, unless someone makes it a failure
            setResult(Result.SUCCESS);
            if (!setup(listener)) {
                return Result.FAILURE;
            }

            try {
                if (!LiterateBuilder.perform(PromotionBuild.this, launcher, listener, getEnvironment(listener),
                        task.getCommand())) {
                    return Result.FAILURE;
                }

                return null;
            } finally {
                boolean failed = false;

                for (int i = buildEnvironments.size() - 1; i >= 0; i--) {
                    if (!buildEnvironments.get(i).tearDown(PromotionBuild.this, listener)) {
                        failed = true;
                    }
                }

                if (failed) {
                    return Result.FAILURE;
                }
            }
        }

        protected void post2(BuildListener listener) throws Exception {
            if (getResult() == Result.SUCCESS) {
                getStatus().onSuccessfulPromotion(PromotionBuild.this);
            }
            // persist the updated build record
            getTarget().save();

            if (getResult() == Result.SUCCESS) {
                // we should evaluate any other pending promotions in case
                // they had a condition on this promotion
                PromotionBranchBuildAction pba = getTarget().getAction(PromotionBranchBuildAction.class);
                for (PromotionProject pp : pba.getPendingPromotions()) {
                    pp.considerPromotion(getTarget());
                }

//                // tickle PromotionTriggers
//                for (AbstractProject<?,?> p : Jenkins.getInstance().getAllItems(AbstractProject.class)) {
//                    PromotionTrigger pt = p.getTrigger(PromotionTrigger.class);
//                    if (pt!=null)
//                        pt.consider(Promotion.this);
//                }
            }
        }

        private boolean setup(BuildListener listener) throws InterruptedException {
            for (PromotionSetup setup : PromotionBuild.this.getProject().getConfiguration().getSetupSteps()) {
                if (!setup.setup(PromotionBuild.this, listener)) {
                    listener.getLogger().println("failed setup " + setup + " " + getResult());
                    return false;
                }
            }
            return true;
        }

    }

    /**
     * Factory method for creating {@link ParametersAction}
     *
     * @param parameters
     * @return
     */
    public static ParametersAction createParametersAction(List<ParameterValue> parameters) {
        return new ParametersAction(parameters);
    }

    public static ParametersAction getParametersActions(PromotionBuild build) {
        return build.getAction(ParametersAction.class);
    }

    /**
     * Combine the target build parameters with the promotion build parameters
     *
     * @param actions
     * @param build
     * @param promotionParams
     */
    public static void buildParametersAction(List<Action> actions, AbstractBuild<?, ?> build,
                                             List<ParameterValue> promotionParams) {
        List<ParameterValue> params = new ArrayList<ParameterValue>();

        //Add the target build parameters first, if the same parameter is not being provided bu the promotion build
        List<ParametersAction> parameters = build.getActions(ParametersAction.class);
        for (ParametersAction paramAction : parameters) {
            for (ParameterValue pvalue : paramAction.getParameters()) {
                if (!promotionParams.contains(pvalue)) {
                    params.add(pvalue);
                }
            }
        }

        //Add all the promotion build parameters
        params.addAll(promotionParams);

        // Create list of actions to pass to scheduled build
        actions.add(new ParametersAction(params));
    }
}
