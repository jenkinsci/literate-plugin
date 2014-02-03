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
import net.jcip.annotations.Immutable;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;
import java.util.logging.Logger;

/**
 * @author Stephen Connolly
 */
@Immutable
public class PromotionConfiguration extends AbstractDescribableImpl<PromotionConfiguration>
        implements Serializable, ModelObject {
    private static final Logger LOGGER = Logger.getLogger(PromotionConfiguration.class.getName());
    @NonNull
    private final String name;
    @CheckForNull
    private final String displayName;
    @CheckForNull
    private final String environment;

    @DataBoundConstructor
    public PromotionConfiguration(String name, String displayName, String environment) {
        this.name = name;
        this.displayName = displayName;
        this.environment = environment;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        PromotionConfiguration promotion = (PromotionConfiguration) o;

        if (environment != null ? !environment.equals(promotion.environment) : promotion.environment != null) {
            return false;
        }
        if (!name.equals(promotion.name)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + (environment != null ? environment.hashCode() : 0);
        return result;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<PromotionConfiguration> {

        @Override
        public String getDisplayName() {
            return "Promotion process";
        }
    }
}
