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
import hudson.Util;
import hudson.model.Action;
import net.jcip.annotations.ThreadSafe;
import org.kohsuke.stapler.Ancestor;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.pegdown.Extensions;
import org.pegdown.PegDownProcessor;

import java.io.Serializable;
import java.util.List;

/**
 * Show the contents of readme.md in the project page.
 *
 * @author Stephen Connolly
 */
@ThreadSafe
public class ReadMeAction implements Action, Serializable {

    /**
     * Ensure consistent serialization.
     */
    private static final long serialVersionUID = 1L;

    /**
     * The markdown content to render.
     */
    @NonNull
    private final String markdown;

    /**
     * The corresponding HTML.
     */
    @CheckForNull
    private transient String html;

    /**
     * Constructor.
     *
     * @param markdown the Markdown content.
     */
    public ReadMeAction(@CheckForNull String markdown) {
        this.markdown = Util.fixNull(markdown);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    public String getIconFileName(String size) {
        return "/plugin/literate/images/" + size + "/literate.png";
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
        return Messages.ReadMeAction_DisplayName();
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    public String getUrlName() {
        return "readme";
    }

    /**
     * Returns the parent object from the current request.
     *
     * @return the parent object from the current request.
     */
    @CheckForNull
    public Object getParent() {
        StaplerRequest current = Stapler.getCurrentRequest();
        if (current != null) {
            List<Ancestor> ancestors = current.getAncestors();
            if (ancestors.size() > 2) {
                return ancestors.get(ancestors.size() - 2).getObject();
            }
        }
        return null;
    }

    /**
     * Returns the Markdown content.
     *
     * @return the Markdown content.
     */
    @NonNull
    public String getMarkdown() {
        return markdown;
    }

    /**
     * Returns the HTML content.
     *
     * @return the HTML content.
     */
    @NonNull
    public String getHtml() {
        if (html == null) {
            // since the generation is deterministic we don't care about races, the end result is equivalent.
            PegDownProcessor pegDownProcessor = new PegDownProcessor(
                    Extensions.AUTOLINKS + Extensions.FENCED_CODE_BLOCKS + Extensions.HARDWRAPS
                            + Extensions.SUPPRESS_ALL_HTML
            );
            html = pegDownProcessor.markdownToHtml(markdown);
        }
        return html;
    }
}
