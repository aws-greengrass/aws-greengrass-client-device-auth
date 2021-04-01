package com.aws.greengrass.device.configuration.parser;

import com.aws.greengrass.device.attribute.DeviceAttribute;
import com.aws.greengrass.device.Session;

public class RuleExpressionEvalVisitor implements RuleExpressionVisitor {
    @Override
    public Object visit(SimpleNode node, Object data) {
        // Not used
        return null;
    }

    @Override
    public Object visit(ASTStart node, Object data) {
        // Single child node
        return node.jjtGetChild(0).jjtAccept(this, data);
    }

    @Override
    public Object visit(ASTOr node, Object data) {
        boolean c1 = (boolean) node.jjtGetChild(0).jjtAccept(this, data);
        boolean c2 = false;
        if (!c1) {
            c2 = (boolean) node.jjtGetChild(1).jjtAccept(this, data);
        }
        return c1 || c2;
    }

    @Override
    public Object visit(ASTAnd node, Object data) {
        boolean c1 = (boolean) node.jjtGetChild(0).jjtAccept(this, data);
        boolean c2 = false;
        if (c1) {
            c2 = (boolean) node.jjtGetChild(1).jjtAccept(this, data);
        }
        return c1 && c2;
    }

    @Override
    public Object visit(ASTThing node, Object data) {
        Session session = (Session) data;
        DeviceAttribute attribute = session.getSessionAttribute("Thing", "ThingName");
        return attribute.matches((String) node.jjtGetValue());
    }
}
