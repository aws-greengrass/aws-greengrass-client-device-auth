/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.benchmark;

import com.aws.greengrass.clientdevices.auth.configuration.ExpressionVisitor;
import com.aws.greengrass.clientdevices.auth.configuration.parser.ASTStart;
import com.aws.greengrass.clientdevices.auth.configuration.parser.RuleExpression;
import com.aws.greengrass.clientdevices.auth.session.Session;
import com.aws.greengrass.clientdevices.auth.session.attribute.AttributeProvider;
import com.aws.greengrass.clientdevices.auth.session.attribute.DeviceAttribute;
import com.aws.greengrass.clientdevices.auth.session.attribute.WildcardSuffixAttribute;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;

import java.io.StringReader;


public class RuleExpressionEvaluationBenchmark {

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void evaluateThingExpressionWithThingName() throws Exception {
        ASTStart tree = new RuleExpression(new StringReader("thingName: MyThingName")).Start();
        Session session = new FakeSession("MyThingName");
        new ExpressionVisitor().visit(tree, session);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void evaluateWildcardPrefixThingExpressionWithThingName() throws Exception {
        ASTStart tree = new RuleExpression(new StringReader("thingName: *ThingName")).Start();
        Session session = new FakeSession("MyThingName");
        new ExpressionVisitor().visit(tree, session);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void evaluateWildcardSuffixThingExpressionWithThingName() throws Exception {
        ASTStart tree = new RuleExpression(new StringReader("thingName: MyThing*")).Start();
        Session session = new FakeSession("MyThingName");
        new ExpressionVisitor().visit(tree, session);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void evaluateWildcardPrefixAndSuffixThingExpressionWithThingName() throws Exception {
        ASTStart tree = new RuleExpression(new StringReader("thingName: *ThingName*")).Start();
        Session session = new FakeSession("MyThingName");
        new ExpressionVisitor().visit(tree, session);
    }

    static class FakeSession implements Session {

        private final DeviceAttribute attribute;

        FakeSession(String thingName) {
            this.attribute = new WildcardSuffixAttribute(thingName);
        }

        @Override
        public AttributeProvider getAttributeProvider(String attributeProviderNameSpace) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DeviceAttribute getSessionAttribute(String attributeNamespace, String attributeName) {
            return attribute;
        }
    }
}
