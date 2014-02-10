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
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.ModelObject;
import org.apache.commons.lang.StringUtils;
import org.cloudbees.literate.jenkins.promotions.conditions.ManualCondition;
import org.cloudbees.literate.jenkins.promotions.setup.RestoreArchivedFiles;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * @author Stephen Connolly
 */
public class PromotionConfiguration extends AbstractDescribableImpl<PromotionConfiguration>
        implements Serializable, ModelObject {
    private static final Logger LOGGER = Logger.getLogger(PromotionConfiguration.class.getName());
    @NonNull
    private final String name;
    @CheckForNull
    private final String displayName;
    @CheckForNull
    private final String environment;
    @CheckForNull
    private final PromotionSetup[] setupSteps;
    @CheckForNull
    private final PromotionCondition[] conditions;

    @DataBoundConstructor
    public PromotionConfiguration(String name, String displayName, String environment, PromotionSetup[] setupSteps,
                                  PromotionCondition[] conditions) {
        this.name = name;
        this.displayName = displayName;
        this.environment = normalizeEnvironment(environment);
        this.setupSteps = setupSteps == null ? null : setupSteps.clone();
        this.conditions = conditions == null ? null : conditions.clone();
    }

    public static String normalizeEnvironment(String environment) {
        return asEnvironmentsString(asEnvironments(environment));
    }

    @CheckForNull
    public static String asEnvironmentsString(Set<String> environments) {
        if (environments == null || environments.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (String environment : environments) {
            if (StringUtils.isBlank(environment)) {
                continue;
            }
            if (first) {
                first = false;
            } else {
                builder.append(' ');
            }
            if (environment.indexOf('"') != -1
                    || environment.indexOf('\'') != -1
                    || environment.indexOf('`') != -1
                    || environment.indexOf(' ') != -1
                    || environment.indexOf(',') != -1
                    || environment.indexOf('\\') != -1
                    || environment.indexOf('\n') != -1
                    || environment.indexOf('\r') != -1
                    || environment.indexOf('\t') != -1
                    || environment.indexOf('\f') != -1
                    || environment.indexOf('\b') != -1) {
                builder.append('"');
                builder.append(environment.replaceAll("[`\'\"\n\r\t\f\b\\\\]", "\\\\$0"));
                builder.append('"');
            } else {
                builder.append(environment);
            }
        }
        return first ? null : builder.toString();
    }

    @CheckForNull
    public static Set<String> asEnvironments(String environment) {
        if (StringUtils.isBlank(environment)) {
            return null;
        }
        Set<String> result = new LinkedHashSet<String>();
        StringBuilder builder = new StringBuilder();
        Character inQuote = null;
        boolean inEscape = false;
        for (int i = 0; i < environment.length(); i++) {
            char c = environment.charAt(i);
            switch (c) {
                case '\\':
                    if (inEscape) {
                        builder.append(c);
                        inEscape = false;
                    } else {
                        inEscape = true;
                    }
                    break;
                case '\'':
                case '`':
                case '"':
                    if (inEscape) {
                        builder.append(c);
                        inEscape = false;
                        break;
                    }
                    if (inQuote == null) {
                        inQuote = c;
                        break;
                    }
                    if (inQuote == c) {
                        if (builder.length() > 0) {
                            result.add(builder.toString());
                        }
                        builder.setLength(0);
                        inQuote = null;
                        break;
                    }
                    builder.append(c);
                    break;
                case ' ':
                case ',':
                case '\n':
                case '\r':
                case '\t':
                case '\f':
                case '\b':
                    if (inEscape) {
                        builder.append(c);
                        inEscape = false;
                        break;
                    }
                    if (inQuote == null) {
                        if (builder.length() > 0) {
                            result.add(builder.toString());
                        }
                        builder.setLength(0);
                        break;
                    }
                    builder.append(c);
                    break;

                default:
                    inEscape = false;
                    builder.append(c);
                    break;
            }
        }
        if (builder.length() > 0) {
            result.add(builder.toString());
        }
        if (result.isEmpty()) {
            return null;
        }
        return result;
    }

    public static boolean equals(PromotionConfiguration c1, PromotionConfiguration c2) {
        return nameEquals(c1.getName(), c2.getName());
    }

    public static int compare(PromotionConfiguration c1, PromotionConfiguration c2) {
        return nameCompare(c1.getName(), c2.getName());
    }

    public static int compare(PromotionConfiguration c1, PromotionConfiguration c2,
                              Iterable<PromotionConfiguration> sequence) {
        return nameCompare(c1.getName(), c2.getName(), sequence);
    }

    public static boolean nameEquals(String n1, String n2) {
        return StringUtils.equalsIgnoreCase(n1, n2);
    }

    public static int nameCompare(String n1, String n2) {
        return n1.compareToIgnoreCase(n2);
    }

    public static int nameCompare(String n1, String n2, Iterable<PromotionConfiguration> sequence) {
        int i1 = Integer.MAX_VALUE;
        int i2 = Integer.MAX_VALUE;
        int i = 0;
        for (PromotionConfiguration c : sequence) {
            String n = c.getName();
            if (PromotionConfiguration.nameEquals(n, n1)) {
                i1 = i;
            } else if (PromotionConfiguration.nameEquals(n, n2)) {
                i2 = i;
            } else if (i1 != Integer.MAX_VALUE && i2 != Integer.MAX_VALUE) {
                break;
            }
            i++;
        }
        int rv = Integer.compare(i1, i2);
        return rv == 0 ? nameCompare(n1, n2) : rv;
    }

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    public String getDisplayName() {
        return StringUtils.defaultIfBlank(displayName, name);
    }

    @CheckForNull
    public String getEnvironment() {
        return environment;
    }

    @CheckForNull
    public Set<String> asEnvironments() {
        return asEnvironments(environment);
    }

    @NonNull
    public List<PromotionSetup> getSetupSteps() {
        return setupSteps == null ? Collections.<PromotionSetup>emptyList() : Arrays.asList(setupSteps);
    }

    public List<PromotionCondition> getConditions() {
        return conditions == null ? Collections.<PromotionCondition>emptyList() : Arrays.asList(conditions);
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<PromotionConfiguration> {

        @Override
        public String getDisplayName() {
            return "Promotion process";
        }

        public PromotionConfiguration defaultIfNull(PromotionConfiguration c) {
            if (c == null) {
                return new PromotionConfiguration("", null, null, new PromotionSetup[]{
                        new RestoreArchivedFiles(null, null, null)
                }, new PromotionCondition[]{
                        new ManualCondition(null, null)
                });
            }
            return c;
        }
    }
}
