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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.FilePath;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Items;
import hudson.tasks.Publisher;
import hudson.util.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * The default {@link Agent} that every {@link Publisher} gets.
 */
public class DefaultXmlAgent<P extends Publisher> extends Agent<P> {

    /**
     * Gets the {@link DefaultXmlAgent} for a specified {@link Publisher}.
     *
     * @param descriptor the {@link Publisher}'s {@link Descriptor}.
     * @return the corresponding {@link DefaultXmlAgent}.
     */
    @SuppressWarnings("unchecked")
    public static DefaultXmlAgent<? extends Publisher> fromDescriptor(Descriptor<Publisher> descriptor) {
        return new DefaultXmlAgent<Publisher>(Class.class.cast(descriptor.clazz));
    }

    /**
     * Constructor.
     *
     * @param clazz the {@link Publisher}'s class.
     */
    public DefaultXmlAgent(Class<P> clazz) {
        super(clazz);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public String getConfigurationFilename() {
        return getPublisherClass().getName() + ".xml";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public P getPublisher(@NonNull BuildListener listener, @NonNull FilePath configurationFile) throws IOException, InterruptedException {
        byte[] bytes = readToBytes(configurationFile);
        if (bytes.length == 0) {
            log(listener, "Disabled: configuration file is empty");
            return null;
        }
        InputStream in = new ByteArrayInputStream(bytes);
        try {
            return getPublisherClass().cast(Items.XSTREAM.fromXML(in));
        } catch (Throwable e) {
            throw new IOException("Unable to read " + configurationFile, e);
        } finally {
            IOUtils.closeQuietly(in);
        }
    }

    /**
     * Reads the contents of a {@link FilePath} into a byte array.
     *
     * @param file the file.
     * @return the contents of the file.
     * @throws IOException if something goes wrong.
     */
    private byte[] readToBytes(FilePath file) throws IOException, InterruptedException {
        byte[] bytes;
        InputStream is = file.read();
        try {
            bytes = IOUtils.toByteArray(is);
        } finally {
            IOUtils.closeQuietly(is);
        }
        return bytes;
    }
}
