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
import edu.umd.cs.findbugs.annotations.Nullable;
import net.jcip.annotations.Immutable;
import org.cloudbees.literate.api.v1.ExecutionEnvironment;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.StringTokenizer;
import java.util.TreeSet;

/**
 * Represents an environment that a build is to take place on.
 *
 * @author Stephen Connolly
 */
@Immutable
public class BuildEnvironment implements Comparable<BuildEnvironment>, Serializable {

    /**
     * Ensure consistent serialization.
     */
    private static final long serialVersionUID = 1L;

    /**
     * The set of labels of the individual components that make up the environment. We always wrap this as an
     * unmodifiable when exposing outside of this class.
     */
    @NonNull
    private final SortedSet<String> environment;

    /**
     * The name of the environment. The accessor of this field performs a deterministic calculation from the final
     * field.
     */
    private transient String name;

    /**
     * Constructor.
     *
     * @param environment the environment.
     */
    public BuildEnvironment(@Nullable Set<String> environment) {
        this.environment = environment == null ? new TreeSet<String>() : new TreeSet<String>(environment);
    }

    /**
     * Returns the name of the environment.
     *
     * @return the name of the environment.
     */
    @NonNull
    public String getName() {
        if (name != null) {
            // if computed, use the computed form.
            return name;
        }
        if (environment.isEmpty()) {
            return "default";
        }
        StringBuilder result = new StringBuilder();
        boolean first = true;
        for (String env : new TreeSet<String>(environment)) {
            if (first) {
                first = false;
            } else {
                result.append(',');
            }
            result.append(env);
        }
        return name = result.toString();
    }

    /**
     * {@inheritDoc}
     */
    public int compareTo(BuildEnvironment o) {
        Iterator<String> i = environment.iterator();
        Iterator<String> oi = o.environment.iterator();
        while (i.hasNext() && oi.hasNext()) {
            String s = i.next();
            String os = oi.next();
            int r = s.compareTo(os);
            if (r != 0) {
                return r;
            }
        }
        if (i.hasNext()) {
            return 1;
        }
        if (oi.hasNext()) {
            return -1;
        }
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        BuildEnvironment that = (BuildEnvironment) o;

        if (!environment.equals(that.environment)) {
            return false;
        }

        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return environment.hashCode();
    }

    /**
     * Returns {@code true} if this is the default environment.
     *
     * @return {@code true} if this is the default environment.
     */
    public boolean isDefault() {
        return environment.isEmpty();
    }

    /**
     * Returns the components that make up the environment.
     *
     * @return the components that make up the environment.
     */
    public SortedSet<String> getComponents() {
        return Collections.unmodifiableSortedSet(environment);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return getName();
    }

    /**
     * Parses the string form ({@link #getName()} and constructs the corresponding instance.
     *
     * @param name the string form.
     * @return the {@link BuildEnvironment}.
     */
    public static BuildEnvironment fromString(String name) {
        if ("default".equals(name)) {
            return new BuildEnvironment(Collections.<String>emptySet());
        }
        SortedSet<String> components = new TreeSet<String>();
        StringTokenizer tokens = new StringTokenizer(name, ",");
        while (tokens.hasMoreTokens()) {
            String token = tokens.nextToken();
            if (components.isEmpty() || token.compareTo(components.last()) > 0) {
                components.add(token);
            } else {
                // if not sorted then the parse has failed
                throw new IllegalArgumentException("Can't parse " + name);
            }
        }
        return new BuildEnvironment(components);
    }

    /**
     * Transforms a collection of {@link ExecutionEnvironment} instances into a set of {@link BuildEnvironment}s.
     *
     * @param environments the collection of {@link ExecutionEnvironment}
     * @return the set of {@link BuildEnvironment}s.
     */
    public static Set<BuildEnvironment> fromSets(Collection<ExecutionEnvironment> environments) {
        Set<BuildEnvironment> result = new LinkedHashSet<BuildEnvironment>();
        for (ExecutionEnvironment environment : environments) {
            result.add(new BuildEnvironment(environment.getComponents()));
        }
        return result;
    }
}
