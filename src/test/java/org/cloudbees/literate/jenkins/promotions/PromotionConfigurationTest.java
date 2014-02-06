/*
 * The MIT License
 *
 * Copyright (c) 2014, Stephen Connolly, CloudBees, Inc.
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

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

/**
 * @author Stephen Connolly
 */
public class PromotionConfigurationTest {

    @Test
    public void asEnvironments_smokes() throws Exception {
        assertThat(PromotionConfiguration.asEnvironments(null), nullValue());
        assertThat(PromotionConfiguration.asEnvironments(""), nullValue());
        assertThat(PromotionConfiguration.asEnvironments(",,,,"), nullValue());
        assertThat(PromotionConfiguration.asEnvironments("``, '', \"\""), nullValue());
        assertThat(PromotionConfiguration.asEnvironments("foo"), is(Collections.singleton("foo")));
        assertThat(PromotionConfiguration.asEnvironments("foo bar"), is(asSet("foo", "bar")));
        assertThat(PromotionConfiguration.asEnvironments("`foo` `bar`"), is(asSet("foo", "bar")));
        assertThat(PromotionConfiguration.asEnvironments("`foo bar`"), is(asSet("foo bar")));
        assertThat(PromotionConfiguration.asEnvironments("`foo \"bar\" manchu\\``, ha"), is(asSet("foo \"bar\" manchu`", "ha")));
    }

    @Test
    public void asEnvironmentsString_smokes() throws Exception {
        assertThat(PromotionConfiguration.asEnvironmentsString(null), nullValue());
        assertThat(PromotionConfiguration.asEnvironmentsString(Collections.<String>emptySet()), nullValue());
        assertThat(PromotionConfiguration.asEnvironmentsString(asSet("")), nullValue());
        assertThat(PromotionConfiguration.asEnvironmentsString(asSet("","","","")), nullValue());
        assertThat(PromotionConfiguration.asEnvironmentsString(Collections.singleton("foo")), is("foo"));
        assertThat(PromotionConfiguration.asEnvironmentsString(asSet("foo", "bar")), is("foo bar"));
        assertThat(PromotionConfiguration.asEnvironmentsString(asSet("foo bar")), is("\"foo bar\""));
        assertThat(PromotionConfiguration.asEnvironmentsString(asSet("foo \"bar\" manchu`", "ha")), is("\"foo \\\"bar\\\" manchu\\`\" ha"));
    }

    private static <T> Set<T> asSet(T... values) {
        return new LinkedHashSet<T>(Arrays.asList(values));
    }
}
