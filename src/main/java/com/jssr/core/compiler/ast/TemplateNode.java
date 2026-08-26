package com.jssr.core.compiler.ast;

import java.util.List;
import java.util.Map;

/**
 * Base sealed interface for JSSR Abstract Syntax Tree (AST) template nodes.
 */
public sealed interface TemplateNode permits
        TemplateNode.StaticTextNode,
        TemplateNode.InterpolationNode,
        TemplateNode.IfNode,
        TemplateNode.ForNode,
        TemplateNode.SwitchNode,
        TemplateNode.TryNode,
        TemplateNode.ThrowNode,
        TemplateNode.ContinueNode,
        TemplateNode.BreakNode,
        TemplateNode.ReturnNode,
        TemplateNode.ComponentNode {

    record StaticTextNode(String text) implements TemplateNode {}

    record InterpolationNode(
            String expression,
            String activeAttribute,
            boolean inTag,
            char quoteChar,
            boolean inScript,
            boolean inStyle,
            boolean inComment
    ) implements TemplateNode {}

    record IfNode(String condition, List<TemplateNode> thenBranch, List<TemplateNode> elseBranch) implements TemplateNode {}

    record ForNode(String item, String collection, List<TemplateNode> body, List<TemplateNode> elseBranch) implements TemplateNode {}

    record SwitchNode(String expression, Map<String, List<TemplateNode>> cases, List<TemplateNode> defaultBranch) implements TemplateNode {}

    record TryNode(List<TemplateNode> tryBody, String catchVar, List<TemplateNode> catchBody, List<TemplateNode> finallyBody) implements TemplateNode {}

    record ThrowNode(String expression) implements TemplateNode {}

    record ContinueNode() implements TemplateNode {}

    record BreakNode() implements TemplateNode {}

    record ReturnNode() implements TemplateNode {}

    record ComponentNode(String tagName, Map<String, String> attributes) implements TemplateNode {}
}
