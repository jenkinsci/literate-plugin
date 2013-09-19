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
package org.cloudbees.literate.jenkins.publishers;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionPoint;
import hudson.FilePath;
import hudson.model.BuildListener;
import hudson.tasks.Publisher;

import java.io.IOException;
import java.text.MessageFormat;

/**
 * An agent will find a build it's {@link Publisher}.
 *
 * @param <P> the publisher class represented by this {@link Agent}
 */
public abstract class Agent<P extends Publisher> implements ExtensionPoint {

    /**
     * The type of {@link Publisher} represented by this agent.
     */
    private final Class<P> clazz;

    /**
     * Constructor.
     *
     * @param clazz the type of {@link Publisher} represented by this agent.
     */
    protected Agent(Class<P> clazz) {
        this.clazz = clazz;
    }

    /**
     * Each {@link Publisher} has its own configuration requirements and format. The presence of a {@link Publisher}'s
     * configuration file in the configuration file directory indicates that the {@link Publisher} should be configured
     * for this build.
     *
     * @return the file name that indicates that this {@link Agent} can locate a {@link Publisher}.
     */
    @NonNull
    public abstract String getConfigurationFilename();

    /**
     * Construct the {@link Publisher}.
     *
     * @param listener          the listener.
     * @param configurationFile the configuration file for this {@link Agent}.
     * @return The configured {@link Publisher} or {@code null} if parsing the configuration file indicates that
     *         the {@link Publisher} should not be configured for this build. (To handle the use case where you want
     *         to disable a {@link Publisher} in a specific profile that has been enabled in the default profile.
     * @throws IOException if there was an issue reading the configuration file.
     */
    @CheckForNull
    public abstract P getPublisher(@NonNull BuildListener listener, @NonNull FilePath configurationFile)
            throws IOException;

    /**
     * In the event that there are multiple {@link Agent} extensions for the same {@link Publisher} and more than
     * one of them has a configuration file that parses, the {@link Agent} builder the highest
     * {@link hudson.Extension#ordinal()} that actually has a configuration file will be asked, in turn, to merge the
     * {@link Publisher} instances.
     *
     * @param listener the listener.
     * @param self     the configured {@link Publisher} that this {@link Agent} produced.
     * @param other    the {@link Publisher} instance that the other {@link Agent} produced.
     * @return the actual {@link Publisher} to use.
     */
    @CheckForNull
    public P merge(@NonNull BuildListener listener, P self, P other) {
        log(listener, "Resolving configuration merge request using first-past-the-post strategy");
        return self;
    }

    /**
     * Returns the type of {@link Publisher} that this {@link Agent} finds.
     *
     * @return the type of {@link Publisher} that this {@link Agent} finds.
     */
    public final Class<P> getPublisherClass() {
        return clazz;
    }

    /**
     * Returns the display name of this {@link Agent} for use within the console log.
     *
     * @return the display name of this {@link Agent} for use within the console log.
     */
    public final String getDisplayName() {
        return getPublisherClass().getSimpleName() + ":" + getConfigurationFilename();
    }

    /**
     * Helper method to log a message to a {@link BuildListener}.
     *
     * @param listener the listener.
     * @param message  the message.
     */
    public final void log(BuildListener listener, String message) {
        listener.getLogger().println("[" + getDisplayName() + "] " + message);
    }

    /**
     * Helper method to log a message to a {@link BuildListener}.
     *
     * @param listener the listener.
     * @param message  the message format.
     * @param args     the arguments to interpolate into the message.
     */
    public final void log(BuildListener listener, String message, Object... args) {
        listener.getLogger().println("[" + getDisplayName() + "] " + MessageFormat.format(message, args));
    }
}
