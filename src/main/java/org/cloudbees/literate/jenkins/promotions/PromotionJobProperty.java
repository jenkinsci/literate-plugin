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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.ItemGroup;
import hudson.model.ItemGroupMixIn;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import net.jcip.annotations.Immutable;
import org.apache.commons.lang.StringUtils;
import org.cloudbees.literate.api.v1.ProjectModel;
import org.cloudbees.literate.api.v1.TaskCommands;
import org.cloudbees.literate.jenkins.LiterateBranchBuild;
import org.cloudbees.literate.jenkins.LiterateBranchProject;
import org.cloudbees.literate.jenkins.ProjectModelAction;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Stephen Connolly
 */
@Immutable
public class PromotionJobProperty extends JobProperty<LiterateBranchProject> implements
        ItemGroup<PromotionProject> {
    private static final Logger LOGGER = Logger.getLogger(PromotionJobProperty.class.getName());

    @NonNull
    private final List<PromotionConfiguration> processes;
    @CheckForNull
    private transient Collection<PromotionBranchProjectAction> jobActions;
    @CheckForNull
    private transient List<PromotionProject> allProcesses = null;
    @CheckForNull
    private transient List<PromotionProject> activeProcesses = null;

    public PromotionJobProperty(@CheckForNull List<PromotionConfiguration> processes) {
        this.processes =
                processes == null
                        ? new ArrayList<PromotionConfiguration>()
                        : new ArrayList<PromotionConfiguration>(processes);
    }

    @Override
    @edu.umd.cs.findbugs.annotations.SuppressWarnings(value = "RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE",
            justification = "migration of legacy data")
    public Collection<? extends Action> getJobActions(LiterateBranchProject job) {
        if (jobActions == null) {
            if (processes == null || processes.isEmpty()) {
                jobActions = Collections.emptyList();
            } else {
                jobActions = Collections.singleton(new PromotionBranchProjectAction(this));
            }
        }
        return jobActions;
    }

    /**
     * Adds a new promotion process of the given name.
     */
    public synchronized PromotionProject addProcess(PromotionConfiguration process) throws IOException {
        PromotionProject
                p = new PromotionProject(this, process);
        processes.add(process);
        safeAddToProcessesList(p);
        buildActiveProcess();
        p.onCreatedFromScratch();
        return p;
    }

    private synchronized void safeAddToProcessesList(PromotionProject p) {
        int index = 0;
        boolean found = false;
        for (ListIterator<PromotionProject> i = allProcesses.listIterator(); i.hasNext(); ) {
            PromotionProject process = i.next();
            if (p.getName().equalsIgnoreCase(process.getName())) {
                found = true;
                try {
                    i.set(p);
                    break;
                } catch (UnsupportedOperationException e) {
                    // shouldn't end up here but Java Runtime Spec allows for this case
                    // we don't care about ConcurrentModificationException because we are done
                    // with the iterator once we find the first element.
                    allProcesses.set(index, p);
                    break;
                }
            }
            index++;
        }
        if (!found) {
            allProcesses.add(p);
        }
    }

    @Override
    protected void setOwner(LiterateBranchProject owner) {
        super.setOwner(owner);
        if (owner == null) {
            allProcesses = null;
            activeProcesses = null;
        } else {
            // readResolve is too early because we don't have our parent set yet,
            // so use this as the initialization opportunity.
            // CopyListener is also using setOwner to re-init after copying config from another job.
            allProcesses = new ArrayList<PromotionProject>(
                    ItemGroupMixIn.<String, PromotionProject>loadChildren(
                            this, getRootDir(), ItemGroupMixIn.KEYED_BY_NAME).values());
            for (PromotionProject p: allProcesses) {
                try {
                    p.onLoad(this, p.getName());
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Failed to load promotion process " + p.getName(), e);
                }
            }
            try {
                buildActiveProcess();
            } catch (IOException e) {
                throw new Error(e);
            }
        }
    }

    /**
     * Builds {@link #activeProcesses}.
     */
    private void buildActiveProcess() throws IOException {
        activeProcesses = new ArrayList<PromotionProject>();
        Set<String> existingProcesses = new HashSet<String>();
        for (PromotionProject p : allProcesses) {
            boolean active = isActiveProcessNameIgnoreCase(p.getName());
            p.makeDisabled(!active);
            if (active) {
                activeProcesses.add(p);
            }

            // ensure that the name casing matches what's given in the activeProcessName
            // this is because in case insensitive file system, we may end up resolving
            // to a directory name that differs only in their case.
            p.renameTo(getActiveProcessName(p.getName()));
            existingProcesses.add(p.getName());
            if (!p.getConfigFile().exists()) {
                try {
                    p.save();
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Failed to save promotion process " + p.getName(), e);
                }
            }
        }
        for (PromotionConfiguration c: processes) {
            if (existingProcesses.contains(c.getName()))  continue;
            PromotionProject p = new PromotionProject(this, c);
            safeAddToProcessesList(p);
            activeProcesses.add(p);
            p.onCreatedFromScratch();
            try {
                p.save();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to save promotion process " + p.getName(), e);
            }
        }
    }

    /**
     * Return the string in the case as specified in {@link #processes}.
     */
    private synchronized String getActiveProcessName(String s) {
        for (PromotionConfiguration n : processes) {
            if (n.getName().equalsIgnoreCase(s)) {
                return n.getName();
            }
        }
        return s;   // not active so we don't care
    }

    private synchronized boolean isActiveProcessNameIgnoreCase(String s) {
        for (PromotionConfiguration n : processes) {
            if (n.getName().equalsIgnoreCase(s)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets the list of promotion processes defined for this project,
     * including ones that are no longer actively used and only
     * for archival purpose.
     *
     * @return non-null and non-empty. Read-only.
     */
    public synchronized List<PromotionProject> getItems() {
        return allProcesses;
    }

    /**
     * Gets the list of active promotion processes.
     */
    public List<PromotionProject> getActiveItems() {
        return activeProcesses;
    }

    public LiterateBranchProject getOwner() {
        return owner;
    }

    public PromotionProject getItem(String name) {
        if (owner == null) {
            return null;
        }
        if (allProcesses == null) {
            return null;
        }
        for (PromotionProject process : allProcesses) {
            if (StringUtils.equals(process.getName(), name)) {
                return process;
            }
        }
        return null;
    }

    public File getRootDir() {
        return new File(getOwner().getRootDir(), "promotions");
    }

    public void save() throws IOException {
        // there's nothing to save, actually
    }

    public void onDeleted(PromotionProject item) throws IOException {
        // TODO delete the persisted directory?
    }

    public void onRenamed(PromotionProject item, String oldName, String newName)
            throws IOException {
        // TODO should delete the persisted directory?
    }

    public String getUrl() {
        return getOwner().getUrl() + "promotion/";
    }

    public String getFullName() {
        return getOwner().getFullName() + "/promotion";
    }

    public String getFullDisplayName() {
        return getOwner().getFullDisplayName() + " \u00BB promotion";
    }

    public String getUrlChildPrefix() {
        return "";
    }

    public File getRootDirFor(PromotionProject child) {
        return new File(getRootDir(), child.getName());
    }

    public String getDisplayName() {
        return "promotion";
    }

    @Override
    public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener) {
        if (build instanceof LiterateBranchBuild) {
            build.addAction(new PromotionBranchBuildAction((LiterateBranchBuild) build));
        }
        return true;
    }

    @edu.umd.cs.findbugs.annotations.SuppressWarnings(value = "RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE",
            justification = "migration of legacy data")
    public List<PromotionConfiguration> getProcesses() {
        return processes == null
                ? Collections.<PromotionConfiguration>emptyList()
                : Collections.unmodifiableList(processes);
    }

    public ProjectModel getModel(LiterateBranchBuild build) {
        ProjectModelAction action = build.getAction(ProjectModelAction.class);
        return action == null ? null : action.getModel();
    }

    public TaskCommands getTask(LiterateBranchBuild build, PromotionProject promotionProject) {
        ProjectModel model = getModel(build);
        if (model == null) {
            return null;
        }
        return model.getTask(promotionProject.getName());
    }

    @Extension
    public static class DescriptorImpl extends JobPropertyDescriptor {

        @Override
        public boolean isApplicable(Class<? extends Job> jobType) {
            return LiterateBranchProject.class.isAssignableFrom(jobType);
        }

        @Override
        public String getDisplayName() {
            return "";  // don't need this descriptor for the UI
        }
    }

}
