/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

/* Generated By:JJTree: Do not edit this line. ASTOr.java Version 7.0 */
/* JavaCCOptions:MULTI=true,NODE_USES_PARSER=false,VISITOR=true,TRACK_TOKENS=false,NODE_PREFIX=AST,NODE_EXTENDS=,NODE_FACTORY=,SUPPORT_CLASS_VISIBILITY_PUBLIC=true */
package com.aws.greengrass.clientdevices.auth.configuration.parser;

public
class ASTOr extends SimpleNode {
  public ASTOr(int id) {
    super(id);
  }

  public ASTOr(RuleExpression p, int id) {
    super(p, id);
  }


  /** Accept the visitor. **/
  public Object jjtAccept(RuleExpressionVisitor visitor, Object data) {

    return
    visitor.visit(this, data);
  }
}
/* JavaCC - OriginalChecksum=215db50219581de78a1fe6df19e18341 (do not edit this line) */
