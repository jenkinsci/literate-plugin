package org.cloudbees.literate.jenkins.promotions.conditions;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.ChoiceParameterDefinition;
import hudson.model.Hudson;
import hudson.model.InvisibleAction;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.model.User;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.acegisecurity.GrantedAuthority;
import org.cloudbees.literate.api.v1.Parameter;
import org.cloudbees.literate.api.v1.ProjectModel;
import org.cloudbees.literate.api.v1.TaskCommands;
import org.cloudbees.literate.jenkins.LiterateBranchBuild;
import org.cloudbees.literate.jenkins.ProjectModelAction;
import org.cloudbees.literate.jenkins.promotions.PromotionBadge;
import org.cloudbees.literate.jenkins.promotions.PromotionBuild;
import org.cloudbees.literate.jenkins.promotions.PromotionCondition;
import org.cloudbees.literate.jenkins.promotions.PromotionConditionDescriptor;
import org.cloudbees.literate.jenkins.promotions.PromotionProject;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link PromotionCondition} that requires manual promotion.
 *
 * @author Kohsuke Kawaguchi
 * @author Peter Hayes
 */
public class ManualCondition extends PromotionCondition {
    private final String users;
    @CheckForNull
    private final List<ParameterDefinition> parameterDefinitions;

    @DataBoundConstructor
    public ManualCondition(String users, ParameterDefinition[] parameterDefinitions) {
        this.users = users;
        this.parameterDefinitions = parameterDefinitions == null
                ? null : new ArrayList<ParameterDefinition>(Arrays.asList(parameterDefinitions));
    }

    public ManualCondition() {
        this(null, null);
    }

    /**
     * Gets the {@link hudson.model.ParameterDefinition} of the given name, if any.
     */
    public static ParameterDefinition getParameterDefinition(List<ParameterDefinition> parameterDefinitions,
                                                             String name) {
        if (parameterDefinitions == null) {
            return null;
        }

        for (ParameterDefinition pd : parameterDefinitions) {
            if (pd.getName().equals(name)) {
                return pd;
            }
        }

        return null;
    }

    @CheckForNull
    public String getUsers() {
        return users;
    }

    @NonNull
    public List<ParameterDefinition> getParameterDefinitions() {
        return parameterDefinitions == null
                ? Collections.<ParameterDefinition>emptyList()
                : Collections.unmodifiableList(parameterDefinitions);
    }

    @NonNull
    public List<ParameterDefinition> getParameterDefinitions(PromotionProject project, LiterateBranchBuild build) {
        ProjectModelAction modelAction = build.getAction(ProjectModelAction.class);
        ProjectModel model = modelAction == null ? null : modelAction.getModel();
        TaskCommands task = model == null ? null : model.getTask(project.getName());
        Map<String, Parameter> parameters = task == null ? null : task.getParameters();
        if (parameters == null || parameters.isEmpty()) {
            return getParameterDefinitions();
        }
        List<ParameterDefinition> result = new ArrayList<ParameterDefinition>();
        Set<String> missing = new HashSet<String>(parameters.keySet());
        for (ParameterDefinition d : getParameterDefinitions()) {
            Parameter p = parameters.get(d.getName());
            if (p != null) {
                missing.remove(d.getName());
                if (p.getDefaultValue() != null && d.getDefaultParameterValue() == null) {
                    result.add(d.copyWithDefaultValue(
                            new StringParameterValue(d.getName(), p.getDefaultValue(), d.getDescription())));
                } else {
                    result.add(d);
                }
            } else {
                result.add(d);
            }
        }
        for (String name : missing) {
            Parameter p = parameters.get(name);
            if (p.getValidValues() == null) {
                result.add(new StringParameterDefinition(p.getName(), p.getDefaultValue(), p.getDescription()));
            } else {
                result.add(new ChoiceParameterDefinition(p.getName(),
                        p.getValidValues().toArray(new String[p.getValidValues().size()]), p.getDescription()));
            }
        }
        return result;
    }

    /**
     * Gets the {@link hudson.model.ParameterDefinition} of the given name, if any.
     */
    public ParameterDefinition getParameterDefinition(String name) {
        return getParameterDefinition(parameterDefinitions, name);
    }

    public Set<String> getUsersAsSet() {
        if (users == null || users.equals("")) {
            return Collections.emptySet();
        }

        Set<String> set = new HashSet<String>();
        for (String user : users.split(",")) {
            user = user.trim();

            if (user.trim().length() > 0) {
                set.add(user);
            }
        }

        return set;
    }

    @Override
    public PromotionBadge isMet(PromotionProject project, LiterateBranchBuild build) {
        List<ManualApproval> approvals = build.getActions(ManualApproval.class);

        for (ManualApproval approval : approvals) {
            if (approval.name.equals(project.getName())) {
                return approval.badge;
            }
        }

        return null;
    }

    /**
     * Verifies that the currently logged in user (or anonymous) has permission
     * to approve the promotion and that the promotion has not already been
     * approved.
     */
    public boolean canApprove(PromotionProject project, LiterateBranchBuild build) {
        if (!getUsersAsSet().isEmpty() && !isInUsersList() && !isInGroupList()) {
            return false;
        }

        List<ManualApproval> approvals = build.getActions(ManualApproval.class);

        // For now, only allow approvals if this wasn't already approved
        for (ManualApproval approval : approvals) {
            if (approval.name.equals(project.getName())) {
                return false;
            }
        }

        return true;
    }

    /*
     * Check if user is listed in user list as a specific user
     */
    private boolean isInUsersList() {
        // Current user must be in users list or users list is empty
        Set<String> usersSet = getUsersAsSet();
        return usersSet.contains(Hudson.getAuthentication().getName());
    }

    /*
     * Check if user is a member of a groups as listed in the user / group field
     */
    private boolean isInGroupList() {
        Set<String> groups = getUsersAsSet();
        GrantedAuthority[] authorities = Hudson.getAuthentication().getAuthorities();
        for (GrantedAuthority authority : authorities) {
            if (groups.contains(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Web method to handle the approval action submitted by the user.
     */
    public void doApprove(StaplerRequest req, StaplerResponse rsp,
                          @AncestorInPath PromotionProject project,
                          @AncestorInPath LiterateBranchBuild build) throws IOException, ServletException {

        JSONObject formData = req.getSubmittedForm();

        if (canApprove(project, build)) {
            List<ParameterValue> paramValues = new ArrayList<ParameterValue>();
            List<ParameterDefinition> parameterDefinitions = getParameterDefinitions(project, build);
            if (!parameterDefinitions.isEmpty()) {
                JSONArray a = JSONArray.fromObject(formData.get("parameter"));

                for (Object o : a) {
                    JSONObject jo = (JSONObject) o;
                    String name = jo.getString("name");

                    ParameterDefinition d = getParameterDefinition(parameterDefinitions, name);
                    if (d == null) {
                        throw new IllegalArgumentException("No such parameter definition: " + name);
                    }

                    paramValues.add(d.createValue(req, jo));
                }
            }

            // add approval to build
            build.addAction(new ManualApproval(project.getName(), paramValues));
            build.save();

            // check for promotion
            project.considerPromotion(build);
        }

        rsp.sendRedirect2("../../../..");
    }

    /*
     * Used to annotate the build to indicate that it was manually approved.  This
     * is then looked for in the isMet method.
     */
    public static final class ManualApproval extends InvisibleAction {
        public String name;
        public Badge badge;

        public ManualApproval(String name, List<ParameterValue> values) {
            this.name = name;
            badge = new Badge(values);
        }
    }

    public static final class Badge extends PromotionBadge {
        private final List<ParameterValue> values;
        public String authenticationName;

        public Badge(List<ParameterValue> values) {
            this.authenticationName = Hudson.getAuthentication().getName();
            this.values = values;
        }

        @Exported
        public String getUserName() {
            if (authenticationName == null) {
                return "N/A";
            }

            User u = User.get(authenticationName, false);
            return u != null ? u.getDisplayName() : authenticationName;
        }

        @Exported
        public List<ParameterValue> getParameterValues() {
            return values != null ? values : Collections.EMPTY_LIST;
        }

        @Override
        public void buildEnvVars(AbstractBuild<?, ?> build, EnvVars env) {
            List<ParameterValue> params = ((PromotionBuild) build).getParameterValues();
            if (params != null) {
                for (ParameterValue value : params) {
                    value.buildEnvVars(build, env);
                }
            }
        }
    }

    @Extension
    public static final class DescriptorImpl extends PromotionConditionDescriptor {
        public String getDisplayName() {
            return Messages.ManualCondition_displayName();
        }
    }
}

