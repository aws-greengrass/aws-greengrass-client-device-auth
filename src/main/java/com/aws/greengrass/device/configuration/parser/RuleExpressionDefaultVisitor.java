/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

/* Generated By:JavaCC: Do not edit this line. RuleExpressionDefaultVisitor.java Version 7.0.10 */
package com.aws.greengrass.device.configuration.parser;

public class RuleExpressionDefaultVisitor implements RuleExpressionVisitor{
  public Object defaultVisit(SimpleNode node, Object data){
    node.childrenAccept(this, data);
    return data;
  }
  public Object visit(SimpleNode node, Object data){
    return defaultVisit(node, data);
  }
  public Object visit(ASTStart node, Object data){
    return defaultVisit(node, data);
  }
  public Object visit(ASTOr node, Object data){
    return defaultVisit(node, data);
  }
  public Object visit(ASTAnd node, Object data){
    return defaultVisit(node, data);
  }
  public Object visit(ASTThing node, Object data){
    return defaultVisit(node, data);
  }
}
/* JavaCC - OriginalChecksum=a8850fa05464eb13e5aca678b0c2b355 (do not edit this line) */
