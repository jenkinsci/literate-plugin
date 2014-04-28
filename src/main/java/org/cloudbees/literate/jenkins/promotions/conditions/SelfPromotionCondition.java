/*
 * The MIT License
 *
 * Copyright (c) 2011-2014, CloudBees, Inc., Vincent Latombe
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
package org.cloudbees.literate.jenkins.promotions.conditions;

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import org.cloudbees.literate.jenkins.LiterateBranchBuild;
import org.cloudbees.literate.jenkins.LiterateBranchProject;
import org.cloudbees.literate.jenkins.promotions.*;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;

/**
 * {@link PromotionCondition} that promotes a build as soon as it's done.
 *
 * @author Kohsuke Kawaguchi
 */
public class SelfPromotionCondition extends PromotionCondition {
  private final boolean evenIfUnstable;

  @DataBoundConstructor
  public SelfPromotionCondition(boolean evenIfUnstable) {
    this.evenIfUnstable = evenIfUnstable;
  }

  public boolean isEvenIfUnstable() {
    return evenIfUnstable;
  }

  @Override
  public PromotionBadge isMet(PromotionProject promotionProject, LiterateBranchBuild build) {
    if (!build.isBuilding()) {
      Result r = build.getResult();
      if ((r == Result.SUCCESS) || (evenIfUnstable && r == Result.UNSTABLE)) {
        return new SelfPromotionBadge();
      }
    }
    return null;
  }

  /**
   * {@link RunListener} to pick up completions of a build.
   * <p/>
   * <p/>
   * This is a single instance that receives all the events everywhere in the system.
   *
   * @author Kohsuke Kawaguchi
   */
  @Extension
  public static final class RunListenerImpl extends RunListener<AbstractBuild<?, ?>> {
    public RunListenerImpl() {
      super((Class) AbstractBuild.class);
    }

    @Override
    public void onCompleted(AbstractBuild<?, ?> build, TaskListener listener) {
      if (build instanceof LiterateBranchBuild) {
        LiterateBranchBuild lbb = (LiterateBranchBuild) build;
        LiterateBranchProject lbp = lbb.getProject();
        PromotionBranchProperty property = lbp.getBranch().getProperty(PromotionBranchProperty.class);
        if (property != null) {
          for (PromotionConfiguration c : property.getConfigurations()) {
            for (PromotionCondition p : c.getConditions()) {
              if (p instanceof SelfPromotionCondition) {
                PromotionJobProperty pjp = lbp.getProperty(PromotionJobProperty.class);
                if (pjp == null) {
                  throw new IllegalStateException("This project doesn't have any promotion criteria set");
                }
                PromotionProject item = pjp.getItem(c.getName());
                if (item == null) {
                  throw new IllegalStateException("This project doesn't have the promotion criterion called " + c.getName());
                }
                try {
                  item.considerPromotion(lbb);
                } catch (IOException e) {
                  e.printStackTrace(listener.error("Failed to promote a build"));
                }
                break;
              }
            }
          }
        }
      }
    }
  }

  @Extension
  public static final class DescriptorImpl extends PromotionConditionDescriptor {
    public String getDisplayName() {
      return Messages.SelfPromotionCondition_DisplayName();
    }
  }
}
