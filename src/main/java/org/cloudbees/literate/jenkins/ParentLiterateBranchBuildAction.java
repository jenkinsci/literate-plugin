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

import hudson.model.Action;
import hudson.model.Executor;
import hudson.model.InvisibleAction;
import hudson.model.Queue;

import java.util.List;

/**
 * Helper action to allow us to track the parent build from the child.
 */
public class ParentLiterateBranchBuildAction extends InvisibleAction implements Queue.QueueAction {
    /**
     * Our parent build.
     */
    private transient LiterateBranchBuild parent;

    /**
     * Default constructor to be called from the parent build only
     */
    public ParentLiterateBranchBuildAction() {
        Executor exec = Executor.currentExecutor();
        if (exec == null) {
            throw new IllegalStateException("not on an executor thread");
        }
        parent = (LiterateBranchBuild) exec.getCurrentExecutable();
    }

    /**
     * {@inheritDoc}
     */
    public boolean shouldSchedule(List<Action> actions) {
        return true;
    }

    /**
     * Gets the parent build associated builder this action
     */
    public LiterateBranchBuild getParent() {
        return parent;
    }
}
