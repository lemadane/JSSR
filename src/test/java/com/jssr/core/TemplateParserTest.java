package com.jssr.core.compiler.ast;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TemplateParserTest {

    @Test
    @DisplayName("Verify parsing static text and interpolation nodes")
    void testParseStaticAndInterpolation() {
        String tpl = "<article><h2>${name}</h2><p>${role}</p></article>";
        List<TemplateNode> ast = TemplateParser.parse(tpl);

        assertNotNull(ast);
        assertEquals(5, ast.size());

        assertTrue(ast.get(0) instanceof TemplateNode.StaticTextNode);
        assertEquals("<article><h2>", ((TemplateNode.StaticTextNode) ast.get(0)).text());

        assertTrue(ast.get(1) instanceof TemplateNode.InterpolationNode);
        assertEquals("name", ((TemplateNode.InterpolationNode) ast.get(1)).expression());

        assertTrue(ast.get(2) instanceof TemplateNode.StaticTextNode);
        assertEquals("</h2><p>", ((TemplateNode.StaticTextNode) ast.get(2)).text());

        assertTrue(ast.get(3) instanceof TemplateNode.InterpolationNode);
        assertEquals("role", ((TemplateNode.InterpolationNode) ast.get(3)).expression());

        assertTrue(ast.get(4) instanceof TemplateNode.StaticTextNode);
        assertEquals("</p></article>", ((TemplateNode.StaticTextNode) ast.get(4)).text());
    }

    @Test
    @DisplayName("Verify parsing @if directive AST structure")
    void testParseIfDirective() {
        String tpl = "@if (active) { <span>Active</span> } @else { <span>Inactive</span> }";
        List<TemplateNode> ast = TemplateParser.parse(tpl);

        assertEquals(1, ast.size());
        assertTrue(ast.get(0) instanceof TemplateNode.IfNode);

        TemplateNode.IfNode ifNode = (TemplateNode.IfNode) ast.get(0);
        assertEquals("active", ifNode.condition());
        assertFalse(ifNode.thenBranch().isEmpty());
        assertFalse(ifNode.elseBranch().isEmpty());
    }

    @Test
    @DisplayName("Verify parsing @for directive AST structure")
    void testParseForDirective() {
        String tpl = "@for (user : users) { <li>${user.name}</li> }";
        List<TemplateNode> ast = TemplateParser.parse(tpl);

        assertEquals(1, ast.size());
        assertTrue(ast.get(0) instanceof TemplateNode.ForNode);

        TemplateNode.ForNode forNode = (TemplateNode.ForNode) ast.get(0);
        assertEquals("user", forNode.item());
        assertEquals("users", forNode.collection());
        assertFalse(forNode.body().isEmpty());
    }
}
