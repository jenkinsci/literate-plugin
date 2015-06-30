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

import hudson.FilePath;
import hudson.remoting.VirtualChannel;
import org.cloudbees.literate.api.v1.vfs.PathNotFoundException;
import org.cloudbees.literate.api.v1.vfs.ProjectRepository;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.TreeSet;
import jenkins.MasterToSlaveFileCallable;

/**
 * A {@link ProjectRepository} that is accessed using Jenkins' remoting {@link FilePath} interface.
 *
 * @author Stephen Connolly
 */
public class FilePathRepository implements ProjectRepository {

    /**
     * The root.
     */
    private final FilePath root;

    /**
     * Constructor.
     *
     * @param root the root.
     * @throws IOException          if the root could not be transformed into an absolute path.
     * @throws InterruptedException if interrupted while performing remote operations.
     */
    public FilePathRepository(FilePath root) throws IOException, InterruptedException {
        this.root = root.absolutize();
    }

    /**
     * Resolves a path string to a {@link FilePath}.
     *
     * @param path the path.
     * @return the {@link FilePath}
     * @throws PathNotFoundException if we know the path does not exist.
     */
    private FilePath resolve(String path) throws PathNotFoundException {
        if (path == null || path.trim().length() == 0 || path.equals("/")) {
            return root;
        } else {
            FilePath dir = root.child(path);
            String p1 = root.getRemote().replace('\\', '/');
            if (!p1.endsWith("/")) {
                p1 = p1 + "/";
            }
            String p2 = dir.getRemote().replace('\\', '/');
            if (!p2.endsWith("/")) {
                p2 = p2 + "/";
            }
            if (p2.startsWith(p1)) {
                return dir;
            } else {
                throw new PathNotFoundException("Path is outside of repository");
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public InputStream get(String filePath) throws PathNotFoundException, IOException {
        try {
            return resolve(filePath).read();
        } catch (InterruptedException x) {
            throw new IOException(x);
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean isDirectory(String path) throws IOException {
        try {
            return resolve(path).isDirectory();
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean isFile(String path) throws IOException {
        try {
            return resolve(path).act(new IsFile());
        } catch (InterruptedException e) {
            throw new IOException(e);
        }

    }

    /**
     * {@inheritDoc}
     */
    public Set<String> getPaths(String path) throws PathNotFoundException, IOException {
        FilePath dir;
        String prefix;
        if (path == null || path.trim().length() == 0 || path.equals("/")) {
            dir = root;
            prefix = "/";
        } else {
            dir = root.child(path);
            String p1 = root.getRemote().replace('\\', '/');
            if (!p1.endsWith("/")) {
                p1 = p1 + "/";
            }
            String p2 = dir.getRemote().replace('\\', '/');
            if (!p2.endsWith("/")) {
                p2 = p2 + "/";
            }
            if (p2.startsWith(p1)) {
                prefix = "/" + p2.substring(p1.length());
            } else {
                throw new PathNotFoundException("Path is outside of repository");
            }
        }
        try {
            if (!dir.isDirectory()) {
                throw new PathNotFoundException("Path does not exist or is not a directory");
            }
            Set<String> result = new TreeSet<String>();
            for (FilePath f : dir.list()) {
                if (f.isDirectory()) {
                    result.add(prefix + f.getName() + "/");
                } else {
                    result.add(prefix + f.getName());
                }
            }
            return result;
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    /**
     * Remote closure to test if something is a file.
     */
    private static class IsFile extends MasterToSlaveFileCallable<Boolean> {
        /**
         * {@inheritDoc}
         */
        public Boolean invoke(File f, VirtualChannel channel) throws IOException {
            return f.isFile();
        }
    }
}
