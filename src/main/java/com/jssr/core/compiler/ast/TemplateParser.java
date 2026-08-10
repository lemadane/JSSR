package com.jssr.core.compiler.ast;

import java.util.*;

/**
 * Parser for JSSR template strings into an Abstract Syntax Tree (AST) representation.
 */
public final class TemplateParser {

    private TemplateParser() {}

    /**
     * Parse a raw template HTML string into a list of AST TemplateNodes.
     *
     * @param template Template HTML string
     * @return List of TemplateNode instances representing the parsed AST
     */
    public static List<TemplateNode> parse(String template) {
        if (template == null || template.isEmpty()) {
            return Collections.emptyList();
        }
        return parseBlock(template, 0, template.length());
    }

    private static List<TemplateNode> parseBlock(String s, int start, int end) {
        List<TemplateNode> nodes = new ArrayList<>();
        int i = start;

        while (i < end) {
            int nextInterpolation = s.indexOf("${", i);
            int nextDirective = s.indexOf("@", i);

            int next = -1;
            if (nextInterpolation != -1 && (nextDirective == -1 || nextInterpolation < nextDirective)) {
                next = nextInterpolation;
            } else if (nextDirective != -1) {
                next = nextDirective;
            }

            if (next == -1 || next >= end) {
                // Static text to the end
                String text = s.substring(i, end);
                if (!text.isEmpty()) {
                    nodes.add(new TemplateNode.StaticTextNode(text));
                }
                break;
            }

            if (next > i) {
                String text = s.substring(i, next);
                nodes.add(new TemplateNode.StaticTextNode(text));
                i = next;
            }

            if (s.startsWith("${", i)) {
                int closing = s.indexOf('}', i + 2);
                if (closing != -1 && closing < end) {
                    String expr = s.substring(i + 2, closing).trim();
                    nodes.add(new TemplateNode.InterpolationNode(expr));
                    i = closing + 1;
                } else {
                    nodes.add(new TemplateNode.StaticTextNode("${"));
                    i += 2;
                }
            } else if (s.startsWith("@if", i) && isWordBoundary(s, i + 3)) {
                i = parseIfDirective(s, i, end, nodes);
            } else if (s.startsWith("@for", i) && isWordBoundary(s, i + 4)) {
                i = parseForDirective(s, i, end, nodes);
            } else if (s.startsWith("@switch", i) && isWordBoundary(s, i + 7)) {
                i = parseSwitchDirective(s, i, end, nodes);
            } else {
                nodes.add(new TemplateNode.StaticTextNode("@"));
                i++;
            }
        }

        return nodes;
    }

    private static int parseIfDirective(String s, int pos, int limit, List<TemplateNode> nodes) {
        int openParen = s.indexOf('(', pos);
        int closeParen = findMatchingParen(s, openParen);
        if (openParen == -1 || closeParen == -1 || closeParen >= limit) {
            nodes.add(new TemplateNode.StaticTextNode("@if"));
            return pos + 3;
        }

        String mainCond = s.substring(openParen + 1, closeParen).trim();
        int bodyStart = closeParen + 1;

        // Find matching @end for this @if block while tracking nested control blocks
        int bodyEnd = findMatchingEnd(s, bodyStart, limit);

        String fullBody = s.substring(bodyStart, bodyEnd);
        List<Branch> branches = parseIfBranches(fullBody);

        TemplateNode.IfNode ifNode = buildIfTree(mainCond, branches);
        nodes.add(ifNode);

        return bodyEnd + 4; // skip "@end"
    }

    private record Branch(String cond, String body) {}

    private static List<Branch> parseIfBranches(String body) {
        List<Branch> branches = new ArrayList<>();
        int len = body.length();
        int cur = 0;

        String currentCond = null;
        int currentBodyStart = 0;

        int depth = 0;
        int i = 0;

        while (i < len) {
            if (body.startsWith("@if", i) || body.startsWith("@for", i) || body.startsWith("@switch", i)) {
                depth++;
                i++;
            } else if (body.startsWith("@end", i)) {
                if (depth > 0) depth--;
                i += 4;
            } else if (depth == 0) {
                if (body.startsWith("@elseif", i) && isWordBoundary(body, i + 7)) {
                    String subBody = body.substring(currentBodyStart, i);
                    branches.add(new Branch(currentCond, subBody));

                    int openParen = body.indexOf('(', i);
                    int closeParen = findMatchingParen(body, openParen);
                    currentCond = body.substring(openParen + 1, closeParen).trim();
                    currentBodyStart = closeParen + 1;
                    i = currentBodyStart;
                } else if (body.startsWith("@else", i) && isWordBoundary(body, i + 5)) {
                    String subBody = body.substring(currentBodyStart, i);
                    branches.add(new Branch(currentCond, subBody));

                    currentCond = null; // @else has no condition
                    currentBodyStart = i + 5;
                    i = currentBodyStart;
                } else {
                    i++;
                }
            } else {
                i++;
            }
        }

        String finalBody = body.substring(currentBodyStart);
        branches.add(new Branch(currentCond, finalBody));

        return branches;
    }

    private static TemplateNode.IfNode buildIfTree(String mainCond, List<Branch> branches) {
        if (branches.isEmpty()) {
            return new TemplateNode.IfNode(mainCond, Collections.emptyList(), Collections.emptyList());
        }

        Branch mainBranch = branches.get(0);
        List<TemplateNode> thenNodes = parse(mainBranch.body());

        if (branches.size() == 1) {
            return new TemplateNode.IfNode(mainCond, thenNodes, Collections.emptyList());
        }

        List<Branch> remaining = branches.subList(1, branches.size());
        Branch next = remaining.get(0);

        List<TemplateNode> elseNodes;
        if (next.cond() != null) {
            // @elseif branch
            elseNodes = List.of(buildIfTree(next.cond(), remaining));
        } else {
            // @else branch
            elseNodes = parse(next.body());
        }

        return new TemplateNode.IfNode(mainCond, thenNodes, elseNodes);
    }

    private static int parseForDirective(String s, int pos, int limit, List<TemplateNode> nodes) {
        int openParen = s.indexOf('(', pos);
        int closeParen = findMatchingParen(s, openParen);
        if (openParen == -1 || closeParen == -1 || closeParen >= limit) {
            nodes.add(new TemplateNode.StaticTextNode("@for"));
            return pos + 4;
        }

        String expr = s.substring(openParen + 1, closeParen).trim();
        String item = "";
        String collection = "";
        if (expr.contains(":")) {
            String[] parts = expr.split(":", 2);
            item = parts[0].trim();
            collection = parts[1].trim();
        } else if (expr.contains(" in ")) {
            String[] parts = expr.split(" in ", 2);
            item = parts[0].trim();
            collection = parts[1].trim();
        }

        int bodyStart = closeParen + 1;
        int bodyEnd = findMatchingEnd(s, bodyStart, limit);

        String body = s.substring(bodyStart, bodyEnd);
        List<TemplateNode> bodyNodes = parse(body);

        nodes.add(new TemplateNode.ForNode(item, collection, bodyNodes));
        return bodyEnd + 4;
    }

    private static int parseSwitchDirective(String s, int pos, int limit, List<TemplateNode> nodes) {
        int openParen = s.indexOf('(', pos);
        int closeParen = findMatchingParen(s, openParen);
        if (openParen == -1 || closeParen == -1 || closeParen >= limit) {
            nodes.add(new TemplateNode.StaticTextNode("@switch"));
            return pos + 7;
        }

        String expr = s.substring(openParen + 1, closeParen).trim();
        int bodyStart = closeParen + 1;
        int bodyEnd = findMatchingEnd(s, bodyStart, limit);

        String body = s.substring(bodyStart, bodyEnd);
        Map<String, List<TemplateNode>> cases = new LinkedHashMap<>();
        List<TemplateNode> defaultBranch = Collections.emptyList();

        // Parse cases inside switch body
        int len = body.length();
        int i = 0;
        String currentCaseVal = null;
        boolean isDefault = false;
        int caseBodyStart = 0;

        while (i < len) {
            if (body.startsWith("@case", i) && isWordBoundary(body, i + 5)) {
                if (currentCaseVal != null) {
                    cases.put(currentCaseVal, parse(body.substring(caseBodyStart, i)));
                } else if (isDefault) {
                    defaultBranch = parse(body.substring(caseBodyStart, i));
                    isDefault = false;
                }

                int cOpen = body.indexOf('(', i);
                int cClose = findMatchingParen(body, cOpen);
                currentCaseVal = body.substring(cOpen + 1, cClose).trim();
                caseBodyStart = cClose + 1;
                i = caseBodyStart;
            } else if (body.startsWith("@default", i) && isWordBoundary(body, i + 8)) {
                if (currentCaseVal != null) {
                    cases.put(currentCaseVal, parse(body.substring(caseBodyStart, i)));
                    currentCaseVal = null;
                }
                isDefault = true;
                caseBodyStart = i + 8;
                i = caseBodyStart;
            } else {
                i++;
            }
        }

        if (currentCaseVal != null) {
            cases.put(currentCaseVal, parse(body.substring(caseBodyStart, len)));
        } else if (isDefault) {
            defaultBranch = parse(body.substring(caseBodyStart, len));
        }

        nodes.add(new TemplateNode.SwitchNode(expr, cases, defaultBranch));
        return bodyEnd + 4;
    }

    private static int findMatchingParen(String s, int openParen) {
        if (openParen == -1) return -1;
        int depth = 0;
        for (int i = openParen; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private static int findMatchingEnd(String s, int start, int limit) {
        int depth = 1;
        int i = start;
        while (i < limit) {
            if (s.startsWith("@if", i) && isWordBoundary(s, i + 3)) {
                depth++;
                i += 3;
            } else if (s.startsWith("@for", i) && isWordBoundary(s, i + 4)) {
                depth++;
                i += 4;
            } else if (s.startsWith("@switch", i) && isWordBoundary(s, i + 7)) {
                depth++;
                i += 7;
            } else if (s.startsWith("@end", i) && isWordBoundary(s, i + 4)) {
                depth--;
                if (depth == 0) {
                    return i;
                }
                i += 4;
            } else {
                i++;
            }
        }
        return limit;
    }

    private static boolean isWordBoundary(String s, int pos) {
        if (pos >= s.length()) return true;
        char c = s.charAt(pos);
        return !Character.isLetterOrDigit(c) && c != '_';
    }
}
