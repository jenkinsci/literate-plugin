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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.FreeStyleProject;
import hudson.model.Items;
import hudson.model.Job;
import hudson.model.TransientProjectActionFactory;
import hudson.security.Permission;
import hudson.tasks.Publisher;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.tools.zip.ZipEntry;
import org.apache.tools.zip.ZipOutputStream;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Project-level action to convert a freestyle project into a literate project.
 *
 * @author Stephen Connolly
 */
public class MigrateFreeStyleAction implements Action {

    /**
     * The permission required to export a literate job configuration bundle.
     */
    private static final Permission EXPORT_LITERATE = Job.EXTENDED_READ;

    /**
     * The project that this action allows exporting the configuration of.
     */
    @NonNull
    private final AbstractProject project;

    /**
     * Constructor.
     *
     * @param project the project that this action allows exporting the configuration of.
     */
    public MigrateFreeStyleAction(@NonNull AbstractProject project) {
        project.getClass();
        this.project = project;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    public String getIconFileName(String size) {
        return "/plugin/literate/images/" + size + "/literate-export.png";
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    public String getIconFileName() {
        return getIconFileName("24x24");
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    public String getDisplayName() {
        return Messages.MigrateFreeStyleAction_DisplayName();
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    public String getUrlName() {
        return "literate-export";
    }

    /**
     * Returns the {@link AbstractProject} that this action operates on.
     *
     * @return the {@link AbstractProject} that this action operates on.
     */
    @NonNull
    public AbstractProject getProject() {
        return project;
    }

    /**
     * Does an actual export.
     *
     * @param req the request.
     * @param rsp the response.
     * @throws IOException      when things go wrong.
     * @throws ServletException when things go wrong.
     */
    public void doExport(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        final Jenkins instance = Jenkins.getInstance();
        instance.getAuthorizationStrategy().getACL(instance).checkPermission(EXPORT_LITERATE);

        if (!"POST".equals(req.getMethod())) {
            rsp.sendRedirect2(".");
            return;
        }

        JSONObject json = req.getSubmittedForm();
        // TODO add in support for some of the build step types that we know how to handle, e.g. shell
        if (!json.has("publishers")) {
            rsp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        Set<String> remove = new HashSet<String>();
        for (Selection s : req.bindJSONToList(Selection.class, json.get("publishers"))) {
            if (!s.isSelected()) {
                remove.add(s.getName());
            }
        }
        final List<Publisher> publishers = new ArrayList<Publisher>(getPublishers());
        for (Iterator<Publisher> iterator = publishers.iterator(); iterator.hasNext(); ) {
            Publisher c = iterator.next();
            if (remove.contains(c.getClass().getName())) {
                iterator.remove();
            }
        }
        rsp.setContentType("application/zip");
        rsp.addHeader("Content-Disposition", "inline; filename=" + Util.rawEncode(project.getName()) + ".zip;");
        final ServletOutputStream outputStream = rsp.getOutputStream();
        try {
            ZipOutputStream zip = new ZipOutputStream(outputStream);
            try {
                for (Publisher publisher : publishers) {
                    StringWriter w = new StringWriter();
                    try {
                        try {
                            Items.XSTREAM.toXML(publisher, w);
                        } finally {
                            w.close();
                        }
                    } catch (Throwable t) {
                        // if we haven't send this to the zip stream, we are ok
                        continue;
                    }
                    try {
                        zip.putNextEntry(new ZipEntry(
                                LiterateEnvironmentBuild.PLUGIN_CONFIG_DIR + "/" + publisher.getClass().getName()
                                        + ".xml"));
                        zip.write(w.toString().getBytes("utf-8"));
                        // anything goes wrong here and we are dead in the water, it's a corrupted zip file
                        // no point trying to recover
                    } finally {
                        zip.flush();
                    }
                }
            } finally {
                zip.close();
            }
        } finally {
            outputStream.flush();
        }
    }

    /**
     * Returns the {@link Publisher}s configured for the project that this action acts on.
     *
     * @return the {@link Publisher}s configured for the project that this action acts on.
     */
    @NonNull
    public List<Publisher> getPublishers() {
        return project.getPublishersList().toList();
    }

    /**
     * Hook to add the {@link MigrateFreeStyleAction} to every {@link FreeStyleProject}.
     */
    @Extension
    @SuppressWarnings("unused") // instantiated by Jenkins
    public static class TransientProjectActionFactoryImpl extends TransientProjectActionFactory {
        /**
         * {@inheritDoc}
         */
        @Override
        public Collection<? extends Action> createFor(AbstractProject target) {
            if (target instanceof FreeStyleProject) {
                return Collections.singleton(new MigrateFreeStyleAction(target));
            } else {
                return Collections.emptySet();
            }
        }
    }

    /**
     * A named checkbox selection, used to handle parsing the submitted form data in
     * {@link #doExport(StaplerRequest, StaplerResponse)}.
     */
    public static class Selection {
        /**
         * The name of the object.
         */
        private final String name;

        /**
         * {@code true} iff the {@link #name} is selected,
         */
        private final boolean selected;

        /**
         * Constructor.
         *
         * @param name     the name.
         * @param selected {@code true} if selected.
         */
        @DataBoundConstructor
        public Selection(String name, boolean selected) {
            this.name = name;
            this.selected = selected;
        }

        /**
         * Returns the name.
         *
         * @return the name.
         */
        public String getName() {
            return name;
        }

        /**
         * Returns {@code true} if selected.
         *
         * @return {@code true} if selected.
         */
        public boolean isSelected() {
            return selected;
        }
    }
}
