package com.jssr.core.compiler.ast;

import java.util.*;

/**
 * Parser for JSSR template strings into an Abstract Syntax Tree (AST) representation.
 * Uses an explicit HTML state machine for precise attribute, tag, and script/style/comment tracking.
 */
public final class TemplateParser {

    private TemplateParser() {}

    private enum HtmlState {
        TEXT,
        TAG_NAME,
        BETWEEN_ATTRIBUTES,
        ATTRIBUTE_NAME,
        BEFORE_ATTRIBUTE_VALUE,
        QUOTED_ATTRIBUTE_VALUE,
        UNQUOTED_ATTRIBUTE_VALUE
    }

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
        return parseBlock(template, 0, template.length(), 0);
    }

    private static List<TemplateNode> parseBlock(String s, int start, int end, int loopDepth) {
        List<TemplateNode> nodes = new ArrayList<>();
        int i = start;

        HtmlState state = HtmlState.TEXT;
        char quoteChar = 0;
        String currentAttr = "";
        String pendingAttr = "";
        StringBuilder buf = new StringBuilder();

        boolean inScript = false;
        boolean inStyle = false;
        boolean inComment = false;

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
                String text = s.substring(i, end);
                if (!text.isEmpty()) {
                    nodes.add(new TemplateNode.StaticTextNode(text));
                }
                break;
            }

            // Update HTML state machine up to next token position
            for (int k = i; k < next; k++) {
                if (k + 4 <= end && s.startsWith("<!--", k)) {
                    inComment = true;
                } else if (inComment && k + 3 <= end && s.startsWith("-->", k)) {
                    inComment = false;
                }

                if (!inComment) {
                    char c = s.charAt(k);
                    if (c == '<') {
                        state = HtmlState.TAG_NAME;
                        quoteChar = 0;
                        currentAttr = "";
                        pendingAttr = "";
                        buf.setLength(0);

                        String lowerRemainder = s.substring(k).toLowerCase(Locale.ROOT);
                        if (lowerRemainder.startsWith("<script")) {
                            inScript = true;
                        } else if (lowerRemainder.startsWith("</script>")) {
                            inScript = false;
                        } else if (lowerRemainder.startsWith("<style")) {
                            inStyle = true;
                        } else if (lowerRemainder.startsWith("</style>")) {
                            inStyle = false;
                        }
                    } else if (state != HtmlState.TEXT) {
                        switch (state) {
                            case TAG_NAME -> {
                                if (Character.isWhitespace(c) || c == '>' || c == '/') {
                                    state = (c == '>') ? HtmlState.TEXT : HtmlState.BETWEEN_ATTRIBUTES;
                                    buf.setLength(0);
                                    currentAttr = "";
                                    pendingAttr = "";
                                } else {
                                    buf.append(c);
                                }
                            }
                            case BETWEEN_ATTRIBUTES -> {
                                if (c == '>') {
                                    state = HtmlState.TEXT;
                                    buf.setLength(0);
                                    currentAttr = "";
                                    pendingAttr = "";
                                } else if (!Character.isWhitespace(c) && c != '/') {
                                    state = HtmlState.ATTRIBUTE_NAME;
                                    buf.setLength(0);
                                    buf.append(c);
                                }
                            }
                            case ATTRIBUTE_NAME -> {
                                if (c == '=') {
                                    pendingAttr = buf.toString().trim();
                                    buf.setLength(0);
                                    state = HtmlState.BEFORE_ATTRIBUTE_VALUE;
                                } else if (Character.isWhitespace(c) || c == '>' || c == '/') {
                                    pendingAttr = buf.toString().trim();
                                    buf.setLength(0);
                                    state = (c == '>') ? HtmlState.TEXT : HtmlState.BETWEEN_ATTRIBUTES;
                                } else {
                                    buf.append(c);
                                }
                            }
                            case BEFORE_ATTRIBUTE_VALUE -> {
                                if (c == '"' || c == '\'') {
                                    quoteChar = c;
                                    currentAttr = pendingAttr;
                                    state = HtmlState.QUOTED_ATTRIBUTE_VALUE;
                                    buf.setLength(0);
                                } else if (!Character.isWhitespace(c)) {
                                    quoteChar = 0;
                                    currentAttr = pendingAttr;
                                    state = HtmlState.UNQUOTED_ATTRIBUTE_VALUE;
                                    buf.setLength(0);
                                }
                            }
                            case QUOTED_ATTRIBUTE_VALUE -> {
                                if (c == quoteChar) {
                                    quoteChar = 0;
                                    currentAttr = "";
                                    pendingAttr = "";
                                    state = HtmlState.BETWEEN_ATTRIBUTES;
                                    buf.setLength(0);
                                }
                            }
                            case UNQUOTED_ATTRIBUTE_VALUE -> {
                                if (Character.isWhitespace(c) || c == '>') {
                                    quoteChar = 0;
                                    currentAttr = "";
                                    pendingAttr = "";
                                    state = (c == '>') ? HtmlState.TEXT : HtmlState.BETWEEN_ATTRIBUTES;
                                    buf.setLength(0);
                                }
                            }
                            default -> {}
                        }
                    }
                }
            }

            if (next > i) {
                String text = s.substring(i, next);
                nodes.add(new TemplateNode.StaticTextNode(text));
                i = next;
            }

            if (s.startsWith("${", i)) {
                if (state == HtmlState.TAG_NAME) {
                    state = HtmlState.TEXT;
                }
                int closing = s.indexOf('}', i + 2);
                if (closing != -1 && closing < end) {
                    String expr = s.substring(i + 2, closing).trim();
                    boolean inTag = (state != HtmlState.TEXT);
                    String activeAttr = "";
                    if (state == HtmlState.QUOTED_ATTRIBUTE_VALUE || state == HtmlState.UNQUOTED_ATTRIBUTE_VALUE) {
                        activeAttr = currentAttr;
                    } else if (state == HtmlState.BEFORE_ATTRIBUTE_VALUE) {
                        activeAttr = pendingAttr;
                    }
                    char activeQuote = (state == HtmlState.QUOTED_ATTRIBUTE_VALUE) ? quoteChar : 0;

                    nodes.add(new TemplateNode.InterpolationNode(
                            expr, activeAttr, inTag, activeQuote, inScript, inStyle, inComment
                    ));
                    i = closing + 1;
                } else {
                    nodes.add(new TemplateNode.StaticTextNode("${"));
                    i += 2;
                }
            } else if (s.startsWith("@if", i) && isWordBoundary(s, i + 3)) {
                i = parseIfDirective(s, i, end, nodes, loopDepth);
            } else if (s.startsWith("@for", i) && isWordBoundary(s, i + 4)) {
                i = parseForDirective(s, i, end, nodes, loopDepth);
            } else if (s.startsWith("@switch", i) && isWordBoundary(s, i + 7)) {
                i = parseSwitchDirective(s, i, end, nodes, loopDepth);
            } else if (s.startsWith("@try", i) && isWordBoundary(s, i + 4)) {
                i = parseTryDirective(s, i, end, nodes, loopDepth);
            } else if (s.startsWith("@throw", i) && isWordBoundary(s, i + 6)) {
                int openParen = s.indexOf('(', i + 6);
                int closeParen = findMatchingParen(s, openParen);
                if (openParen != -1 && closeParen != -1 && closeParen < end) {
                    String expr = s.substring(openParen + 1, closeParen).trim();
                    nodes.add(new TemplateNode.ThrowNode(expr));
                    i = closeParen + 1;
                    if (i < end && s.charAt(i) == ':') i++;
                } else {
                    nodes.add(new TemplateNode.StaticTextNode("@throw"));
                    i += 6;
                }
            } else if (s.startsWith("@continue", i) && isWordBoundary(s, i + 9)) {
                if (loopDepth == 0) {
                    throw new IllegalArgumentException("JSSR template parse error: @continue directive may only be used inside a @for loop.");
                }
                nodes.add(new TemplateNode.ContinueNode());
                i += 9;
            } else if (s.startsWith("@break", i) && isWordBoundary(s, i + 6)) {
                if (loopDepth == 0) {
                    throw new IllegalArgumentException("JSSR template parse error: @break directive may only be used inside a @for loop.");
                }
                nodes.add(new TemplateNode.BreakNode());
                i += 6;
            } else {
                nodes.add(new TemplateNode.StaticTextNode("@"));
                i++;
            }
        }

        return nodes;
    }

    private static int parseIfDirective(String s, int pos, int limit, List<TemplateNode> nodes, int loopDepth) {
        int openParen = s.indexOf('(', pos);
        int closeParen = findMatchingParen(s, openParen);
        if (openParen == -1 || closeParen == -1 || closeParen >= limit) {
            nodes.add(new TemplateNode.StaticTextNode("@if"));
            return pos + 3;
        }

        String mainCond = s.substring(openParen + 1, closeParen).trim();
        int bodyStart = closeParen + 1;
        if (bodyStart < limit && s.charAt(bodyStart) == ':') bodyStart++;

        int bodyEnd = findMatchingEnd(s, bodyStart, limit);

        String fullBody = s.substring(bodyStart, bodyEnd);
        List<Branch> branches = parseIfBranches(fullBody);

        TemplateNode.IfNode ifNode = buildIfTree(mainCond, branches, loopDepth);
        nodes.add(ifNode);

        return bodyEnd + 4; // skip "@end"
    }

    private record Branch(String cond, String body) {}

    private static List<Branch> parseIfBranches(String body) {
        List<Branch> branches = new ArrayList<>();
        int len = body.length();
        int currentBodyStart = 0;
        String currentCond = null;
        int depth = 0;
        int i = 0;

        while (i < len) {
            if (body.startsWith("@if", i) || body.startsWith("@for", i) || body.startsWith("@switch", i) || body.startsWith("@try", i)) {
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
                    if (currentBodyStart < len && body.charAt(currentBodyStart) == ':') currentBodyStart++;
                    i = currentBodyStart;
                } else if (body.startsWith("@else", i) && isWordBoundary(body, i + 5)) {
                    String subBody = body.substring(currentBodyStart, i);
                    branches.add(new Branch(currentCond, subBody));

                    currentCond = null;
                    currentBodyStart = i + 5;
                    if (currentBodyStart < len && body.charAt(currentBodyStart) == ':') currentBodyStart++;
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

    private static TemplateNode.IfNode buildIfTree(String mainCond, List<Branch> branches, int loopDepth) {
        if (branches.isEmpty()) {
            return new TemplateNode.IfNode(mainCond, Collections.emptyList(), Collections.emptyList());
        }

        Branch mainBranch = branches.get(0);
        List<TemplateNode> thenNodes = parseBlock(mainBranch.body(), 0, mainBranch.body().length(), loopDepth);

        if (branches.size() == 1) {
            return new TemplateNode.IfNode(mainCond, thenNodes, Collections.emptyList());
        }

        List<Branch> remaining = branches.subList(1, branches.size());
        Branch next = remaining.get(0);

        List<TemplateNode> elseNodes;
        if (next.cond() != null) {
            elseNodes = List.of(buildIfTree(next.cond(), remaining, loopDepth));
        } else {
            elseNodes = parseBlock(next.body(), 0, next.body().length(), loopDepth);
        }

        return new TemplateNode.IfNode(mainCond, thenNodes, elseNodes);
    }

    private static int parseForDirective(String s, int pos, int limit, List<TemplateNode> nodes, int loopDepth) {
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
        if (bodyStart < limit && s.charAt(bodyStart) == ':') bodyStart++;

        int bodyEnd = findMatchingEnd(s, bodyStart, limit);

        String fullBody = s.substring(bodyStart, bodyEnd);
        int elseIdx = findTopLevelElse(fullBody);

        List<TemplateNode> bodyNodes;
        List<TemplateNode> elseNodes;

        if (elseIdx != -1) {
            int elseBodyStart = elseIdx + 5;
            if (elseBodyStart < fullBody.length() && fullBody.charAt(elseBodyStart) == ':') elseBodyStart++;
            bodyNodes = parseBlock(fullBody.substring(0, elseIdx), 0, elseIdx, loopDepth + 1);
            elseNodes = parseBlock(fullBody.substring(elseBodyStart), 0, fullBody.length() - elseBodyStart, loopDepth);
        } else {
            bodyNodes = parseBlock(fullBody, 0, fullBody.length(), loopDepth + 1);
            elseNodes = Collections.emptyList();
        }

        nodes.add(new TemplateNode.ForNode(item, collection, bodyNodes, elseNodes));
        return bodyEnd + 4;
    }

    private static int findTopLevelElse(String body) {
        int len = body.length();
        int depth = 0;
        int i = 0;
        while (i < len) {
            if (body.startsWith("@if", i) || body.startsWith("@for", i) || body.startsWith("@switch", i) || body.startsWith("@try", i)) {
                depth++;
                i++;
            } else if (body.startsWith("@end", i)) {
                if (depth > 0) depth--;
                i += 4;
            } else if (depth == 0 && body.startsWith("@else", i) && isWordBoundary(body, i + 5)) {
                return i;
            } else {
                i++;
            }
        }
        return -1;
    }

    private static int parseTryDirective(String s, int pos, int limit, List<TemplateNode> nodes, int loopDepth) {
        int bodyStart = pos + 4;
        if (bodyStart < limit && s.charAt(bodyStart) == ':') bodyStart++;

        int bodyEnd = findMatchingEnd(s, bodyStart, limit);
        String fullBody = s.substring(bodyStart, bodyEnd);

        int catchIdx = -1;
        int finallyIdx = -1;
        int depth = 0;
        int i = 0;
        int len = fullBody.length();

        while (i < len) {
            if (fullBody.startsWith("@if", i) || fullBody.startsWith("@for", i) || fullBody.startsWith("@switch", i) || fullBody.startsWith("@try", i)) {
                depth++;
                i++;
            } else if (fullBody.startsWith("@end", i)) {
                if (depth > 0) depth--;
                i += 4;
            } else if (depth == 0) {
                if (catchIdx == -1 && fullBody.startsWith("@catch", i) && isWordBoundary(fullBody, i + 6)) {
                    catchIdx = i;
                    i += 6;
                } else if (finallyIdx == -1 && fullBody.startsWith("@finally", i) && isWordBoundary(fullBody, i + 8)) {
                    finallyIdx = i;
                    i += 8;
                } else {
                    i++;
                }
            } else {
                i++;
            }
        }

        String tryStr = "";
        String catchVar = "err";
        String catchStr = "";
        String finallyStr = "";

        if (catchIdx != -1 && finallyIdx != -1) {
            tryStr = fullBody.substring(0, catchIdx);
            catchStr = fullBody.substring(catchIdx, finallyIdx);
            finallyStr = fullBody.substring(finallyIdx + 8);
        } else if (catchIdx != -1) {
            tryStr = fullBody.substring(0, catchIdx);
            catchStr = fullBody.substring(catchIdx);
        } else if (finallyIdx != -1) {
            tryStr = fullBody.substring(0, finallyIdx);
            finallyStr = fullBody.substring(finallyIdx + 8);
        } else {
            tryStr = fullBody;
        }

        if (catchIdx != -1) {
            int openParen = catchStr.indexOf('(');
            int closeParen = findMatchingParen(catchStr, openParen);
            if (openParen != -1 && closeParen != -1) {
                catchVar = catchStr.substring(openParen + 1, closeParen).trim();
                catchStr = catchStr.substring(closeParen + 1);
            } else {
                catchStr = catchStr.substring(6);
            }
            if (catchStr.startsWith(":")) catchStr = catchStr.substring(1);
        }
        if (finallyStr.startsWith(":")) finallyStr = finallyStr.substring(1);

        List<TemplateNode> tryNodes = parseBlock(tryStr, 0, tryStr.length(), loopDepth);
        List<TemplateNode> catchNodes = catchIdx != -1 ? parseBlock(catchStr, 0, catchStr.length(), loopDepth) : Collections.emptyList();
        List<TemplateNode> finallyNodes = finallyIdx != -1 ? parseBlock(finallyStr, 0, finallyStr.length(), loopDepth) : Collections.emptyList();

        nodes.add(new TemplateNode.TryNode(tryNodes, catchVar, catchNodes, finallyNodes));
        return bodyEnd + 4;
    }

    private static int parseSwitchDirective(String s, int pos, int limit, List<TemplateNode> nodes, int loopDepth) {
        int openParen = s.indexOf('(', pos);
        int closeParen = findMatchingParen(s, openParen);
        if (openParen == -1 || closeParen == -1 || closeParen >= limit) {
            nodes.add(new TemplateNode.StaticTextNode("@switch"));
            return pos + 7;
        }

        String expr = s.substring(openParen + 1, closeParen).trim();
        int bodyStart = closeParen + 1;
        if (bodyStart < limit && s.charAt(bodyStart) == ':') bodyStart++;

        int bodyEnd = findMatchingEnd(s, bodyStart, limit);

        String body = s.substring(bodyStart, bodyEnd);
        Map<String, List<TemplateNode>> cases = new LinkedHashMap<>();
        List<TemplateNode> defaultBranch = Collections.emptyList();

        int len = body.length();
        int i = 0;
        String currentCaseVal = null;
        boolean isDefault = false;
        int caseBodyStart = 0;

        while (i < len) {
            if (body.startsWith("@case", i) && isWordBoundary(body, i + 5)) {
                if (currentCaseVal != null) {
                    cases.put(currentCaseVal, parseBlock(body.substring(caseBodyStart, i), 0, i - caseBodyStart, loopDepth));
                } else if (isDefault) {
                    defaultBranch = parseBlock(body.substring(caseBodyStart, i), 0, i - caseBodyStart, loopDepth);
                    isDefault = false;
                }

                int cOpen = body.indexOf('(', i);
                int cClose = findMatchingParen(body, cOpen);
                currentCaseVal = body.substring(cOpen + 1, cClose).trim();
                caseBodyStart = cClose + 1;
                if (caseBodyStart < len && body.charAt(caseBodyStart) == ':') caseBodyStart++;
                i = caseBodyStart;
            } else if (body.startsWith("@default", i) && isWordBoundary(body, i + 8)) {
                if (currentCaseVal != null) {
                    cases.put(currentCaseVal, parseBlock(body.substring(caseBodyStart, i), 0, i - caseBodyStart, loopDepth));
                    currentCaseVal = null;
                }
                isDefault = true;
                caseBodyStart = i + 8;
                if (caseBodyStart < len && body.charAt(caseBodyStart) == ':') caseBodyStart++;
                i = caseBodyStart;
            } else {
                i++;
            }
        }

        if (currentCaseVal != null) {
            cases.put(currentCaseVal, parseBlock(body.substring(caseBodyStart, len), 0, len - caseBodyStart, loopDepth));
        } else if (isDefault) {
            defaultBranch = parseBlock(body.substring(caseBodyStart, len), 0, len - caseBodyStart, loopDepth);
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
            } else if (s.startsWith("@try", i) && isWordBoundary(s, i + 4)) {
                depth++;
                i += 4;
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
