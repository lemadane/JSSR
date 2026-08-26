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
            } else if (s.startsWith("@return", i) && isWordBoundary(s, i + 7)) {
                nodes.add(new TemplateNode.ReturnNode());
                i += 7;
                if (i < end && (s.charAt(i) == ':' || s.charAt(i) == ';')) i++;
            } else {
                nodes.add(new TemplateNode.StaticTextNode("@"));
                i++;
            }
        }

        return nodes;
    }

    private static int findMatchingBrace(String s, int openBrace) {
        if (openBrace == -1 || openBrace >= s.length() || s.charAt(openBrace) != '{') return -1;
        int depth = 1;
        int len = s.length();
        int i = openBrace + 1;
        while (i < len) {
            if (i + 4 <= len && s.startsWith("<!--", i)) {
                int commentEnd = s.indexOf("-->", i + 4);
                if (commentEnd != -1) {
                    i = commentEnd + 3;
                    continue;
                }
            }
            if (i + 7 <= len && s.substring(i, Math.min(i + 8, len)).toLowerCase(Locale.ROOT).startsWith("<script")) {
                int scriptEnd = s.toLowerCase(Locale.ROOT).indexOf("</script>", i);
                if (scriptEnd != -1) {
                    i = scriptEnd + 9;
                    continue;
                }
            }
            if (i + 6 <= len && s.substring(i, Math.min(i + 7, len)).toLowerCase(Locale.ROOT).startsWith("<style")) {
                int styleEnd = s.toLowerCase(Locale.ROOT).indexOf("</style>", i);
                if (styleEnd != -1) {
                    i = styleEnd + 8;
                    continue;
                }
            }

            if (i + 1 < len && s.charAt(i) == '$' && s.charAt(i + 1) == '{') {
                int interpEnd = s.indexOf('}', i + 2);
                if (interpEnd != -1) {
                    i = interpEnd + 1;
                    continue;
                }
            }

            char c = s.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
            i++;
        }
        return -1;
    }

    private static int findNextChar(String s, int start, char target) {
        int len = s.length();
        for (int i = start; i < len; i++) {
            char c = s.charAt(i);
            if (c == target) return i;
            if (c == ':') continue;
            if (!Character.isWhitespace(c)) return -1;
        }
        return -1;
    }

    private static int findNextNonWhitespace(String s, int start) {
        int len = s.length();
        for (int i = start; i < len; i++) {
            if (!Character.isWhitespace(s.charAt(i))) return i;
        }
        return -1;
    }

    private static int parseIfDirective(String s, int pos, int limit, List<TemplateNode> nodes, int loopDepth) {
        int openParen = s.indexOf('(', pos);
        int closeParen = findMatchingParen(s, openParen);
        if (openParen == -1 || closeParen == -1 || closeParen >= limit) {
            nodes.add(new TemplateNode.StaticTextNode("@if"));
            return pos + 3;
        }

        String mainCond = s.substring(openParen + 1, closeParen).trim();
        int openBrace = findNextChar(s, closeParen + 1, '{');
        if (openBrace == -1 || openBrace >= limit) {
            nodes.add(new TemplateNode.StaticTextNode("@if"));
            return pos + 3;
        }

        int closeBrace = findMatchingBrace(s, openBrace);
        if (closeBrace == -1 || closeBrace >= limit) {
            nodes.add(new TemplateNode.StaticTextNode("@if"));
            return pos + 3;
        }

        List<TemplateNode> thenNodes = parseBlock(s, openBrace + 1, closeBrace, loopDepth);

        int curr = closeBrace + 1;
        List<TemplateNode> elseNodes = Collections.emptyList();

        int nextToken = findNextNonWhitespace(s, curr);
        if (nextToken != -1 && nextToken < limit && s.startsWith("@elseif", nextToken) && isWordBoundary(s, nextToken + 7)) {
            List<TemplateNode> elseifNodes = new ArrayList<>();
            curr = parseIfDirective(s, nextToken, limit, elseifNodes, loopDepth);
            elseNodes = elseifNodes;
        } else if (nextToken != -1 && nextToken < limit && s.startsWith("@else", nextToken) && isWordBoundary(s, nextToken + 5)) {
            int elseOpenBrace = findNextChar(s, nextToken + 5, '{');
            if (elseOpenBrace != -1 && elseOpenBrace < limit) {
                int elseCloseBrace = findMatchingBrace(s, elseOpenBrace);
                if (elseCloseBrace != -1 && elseCloseBrace <= limit) {
                    elseNodes = parseBlock(s, elseOpenBrace + 1, elseCloseBrace, loopDepth);
                    curr = elseCloseBrace + 1;
                }
            }
        }

        nodes.add(new TemplateNode.IfNode(mainCond, thenNodes, elseNodes));
        return curr;
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
        if (item.contains(" ")) {
            String[] itemTokens = item.split("\\s+");
            item = itemTokens[itemTokens.length - 1];
        }

        int openBrace = findNextChar(s, closeParen + 1, '{');
        if (openBrace == -1 || openBrace >= limit) {
            nodes.add(new TemplateNode.StaticTextNode("@for"));
            return pos + 4;
        }

        int closeBrace = findMatchingBrace(s, openBrace);
        if (closeBrace == -1 || closeBrace >= limit) {
            nodes.add(new TemplateNode.StaticTextNode("@for"));
            return pos + 4;
        }

        List<TemplateNode> bodyNodes = parseBlock(s, openBrace + 1, closeBrace, loopDepth + 1);
        List<TemplateNode> elseNodes = Collections.emptyList();
        int curr = closeBrace + 1;

        int nextToken = findNextNonWhitespace(s, curr);
        if (nextToken != -1 && nextToken < limit && s.startsWith("@else", nextToken) && isWordBoundary(s, nextToken + 5)) {
            int elseOpenBrace = findNextChar(s, nextToken + 5, '{');
            if (elseOpenBrace != -1 && elseOpenBrace < limit) {
                int elseCloseBrace = findMatchingBrace(s, elseOpenBrace);
                if (elseCloseBrace != -1 && elseCloseBrace <= limit) {
                    elseNodes = parseBlock(s, elseOpenBrace + 1, elseCloseBrace, loopDepth);
                    curr = elseCloseBrace + 1;
                }
            }
        }

        nodes.add(new TemplateNode.ForNode(item, collection, bodyNodes, elseNodes));
        return curr;
    }

    private static int parseTryDirective(String s, int pos, int limit, List<TemplateNode> nodes, int loopDepth) {
        int openBrace = findNextChar(s, pos + 4, '{');
        if (openBrace == -1 || openBrace >= limit) {
            nodes.add(new TemplateNode.StaticTextNode("@try"));
            return pos + 4;
        }

        int closeBrace = findMatchingBrace(s, openBrace);
        if (closeBrace == -1 || closeBrace >= limit) {
            nodes.add(new TemplateNode.StaticTextNode("@try"));
            return pos + 4;
        }

        List<TemplateNode> tryNodes = parseBlock(s, openBrace + 1, closeBrace, loopDepth);
        String catchVar = "err";
        List<TemplateNode> catchNodes = Collections.emptyList();
        List<TemplateNode> finallyNodes = Collections.emptyList();
        int curr = closeBrace + 1;

        while (curr < limit) {
            int nextToken = findNextNonWhitespace(s, curr);
            if (nextToken == -1 || nextToken >= limit) break;

            if (s.startsWith("@catch", nextToken) && isWordBoundary(s, nextToken + 6)) {
                int afterCatch = nextToken + 6;
                int parenOpen = findNextChar(s, afterCatch, '(');
                int afterParen = afterCatch;
                if (parenOpen != -1 && parenOpen < limit) {
                    int parenClose = findMatchingParen(s, parenOpen);
                    if (parenClose != -1 && parenClose < limit) {
                        catchVar = s.substring(parenOpen + 1, parenClose).trim();
                        afterParen = parenClose + 1;
                    }
                }
                int catchOpenBrace = findNextChar(s, afterParen, '{');
                if (catchOpenBrace != -1 && catchOpenBrace < limit) {
                    int catchCloseBrace = findMatchingBrace(s, catchOpenBrace);
                    if (catchCloseBrace != -1 && catchCloseBrace <= limit) {
                        catchNodes = parseBlock(s, catchOpenBrace + 1, catchCloseBrace, loopDepth);
                        curr = catchCloseBrace + 1;
                    }
                }
            } else if (s.startsWith("@finally", nextToken) && isWordBoundary(s, nextToken + 8)) {
                int finallyOpenBrace = findNextChar(s, nextToken + 8, '{');
                if (finallyOpenBrace != -1 && finallyOpenBrace < limit) {
                    int finallyCloseBrace = findMatchingBrace(s, finallyOpenBrace);
                    if (finallyCloseBrace != -1 && finallyCloseBrace <= limit) {
                        finallyNodes = parseBlock(s, finallyOpenBrace + 1, finallyCloseBrace, loopDepth);
                        curr = finallyCloseBrace + 1;
                    }
                }
            } else {
                break;
            }
        }

        nodes.add(new TemplateNode.TryNode(tryNodes, catchVar, catchNodes, finallyNodes));
        return curr;
    }

    private static int parseSwitchDirective(String s, int pos, int limit, List<TemplateNode> nodes, int loopDepth) {
        int openParen = s.indexOf('(', pos);
        int closeParen = findMatchingParen(s, openParen);
        if (openParen == -1 || closeParen == -1 || closeParen >= limit) {
            nodes.add(new TemplateNode.StaticTextNode("@switch"));
            return pos + 7;
        }

        String expr = s.substring(openParen + 1, closeParen).trim();
        int nextCharHeader = findNextNonWhitespace(s, closeParen + 1);
        int switchBodyStart;
        int switchBodyEnd;
        int returnPos;
        if (nextCharHeader != -1 && s.charAt(nextCharHeader) == '{') {
            int closeBrace = findMatchingBrace(s, nextCharHeader);
            if (closeBrace == -1 || closeBrace >= limit) {
                nodes.add(new TemplateNode.StaticTextNode("@switch"));
                return pos + 7;
            }
            switchBodyStart = nextCharHeader + 1;
            switchBodyEnd = closeBrace;
            returnPos = closeBrace + 1;
        } else if (nextCharHeader != -1 && s.charAt(nextCharHeader) == ':') {
            switchBodyStart = nextCharHeader + 1;
            int endDirective = findNextControlFlowDirectiveInBody(s, switchBodyStart);
            while (endDirective != -1 && (s.startsWith("@case", endDirective) || s.startsWith("@default", endDirective))) {
                endDirective = findNextControlFlowDirectiveInBody(s, endDirective + 5);
            }
            if (endDirective != -1 && s.startsWith("@end", endDirective)) {
                switchBodyEnd = endDirective;
                returnPos = endDirective + 4;
            } else {
                switchBodyEnd = limit;
                returnPos = limit;
            }
        } else {
            nodes.add(new TemplateNode.StaticTextNode("@switch"));
            return pos + 7;
        }

        String switchBody = s.substring(switchBodyStart, switchBodyEnd);
        Map<String, List<TemplateNode>> cases = new LinkedHashMap<>();
        List<TemplateNode> defaultBranch = Collections.emptyList();

        int len = switchBody.length();
        int i = 0;

        while (i < len) {
            int nextDirective = findNextControlFlowDirectiveInBody(switchBody, i);
            if (nextDirective == -1) break;

            if (switchBody.startsWith("@case", nextDirective) && isWordBoundary(switchBody, nextDirective + 5)) {
                int cOpen = switchBody.indexOf('(', nextDirective + 5);
                int cClose = findMatchingParen(switchBody, cOpen);
                if (cOpen != -1 && cClose != -1) {
                    String caseVal = switchBody.substring(cOpen + 1, cClose).trim();
                    int nextChar = findNextNonWhitespace(switchBody, cClose + 1);
                    if (nextChar != -1 && switchBody.charAt(nextChar) == '{') {
                        int cCloseBrace = findMatchingBrace(switchBody, nextChar);
                        if (cCloseBrace != -1) {
                            cases.put(caseVal, parseBlock(switchBody, nextChar + 1, cCloseBrace, loopDepth));
                            i = cCloseBrace + 1;
                            continue;
                        }
                    } else if (nextChar != -1 && switchBody.charAt(nextChar) == ':') {
                        int startBlock = nextChar + 1;
                        int nextCase = findNextCaseOrDefaultOrEnd(switchBody, startBlock);
                        int endBlock = (nextCase != -1) ? nextCase : len;
                        cases.put(caseVal, parseBlock(switchBody, startBlock, endBlock, loopDepth));
                        i = endBlock;
                        continue;
                    }
                }
                i = nextDirective + 5;
            } else if (switchBody.startsWith("@default", nextDirective) && isWordBoundary(switchBody, nextDirective + 8)) {
                int nextChar = findNextNonWhitespace(switchBody, nextDirective + 8);
                if (nextChar != -1 && switchBody.charAt(nextChar) == '{') {
                    int dCloseBrace = findMatchingBrace(switchBody, nextChar);
                    if (dCloseBrace != -1) {
                        defaultBranch = parseBlock(switchBody, nextChar + 1, dCloseBrace, loopDepth);
                        i = dCloseBrace + 1;
                        continue;
                    }
                } else if (nextChar != -1 && switchBody.charAt(nextChar) == ':') {
                    int startBlock = nextChar + 1;
                    int nextCase = findNextCaseOrDefaultOrEnd(switchBody, startBlock);
                    int endBlock = (nextCase != -1) ? nextCase : len;
                    defaultBranch = parseBlock(switchBody, startBlock, endBlock, loopDepth);
                    i = endBlock;
                    continue;
                }
                i = nextDirective + 8;
            } else {
                i = nextDirective + 1;
            }
        }

        nodes.add(new TemplateNode.SwitchNode(expr, cases, defaultBranch));
        return returnPos;
    }

    private static int findNextCaseOrDefaultOrEnd(String text, int fromIdx) {
        int curr = fromIdx;
        int len = text.length();
        while (curr < len) {
            int next = text.indexOf('@', curr);
            if (next == -1) return -1;
            if ((text.startsWith("@case", next) && isWordBoundary(text, next + 5))
             || (text.startsWith("@default", next) && isWordBoundary(text, next + 8))
             || (text.startsWith("@end", next) && isWordBoundary(text, next + 4))) {
                return next;
            }
            curr = next + 1;
        }
        return -1;
    }

    private static int findNextControlFlowDirectiveInBody(String text, int fromIdx) {
        int len = text.length();
        int curr = fromIdx;
        while (curr < len) {
            if ((text.startsWith("@case", curr) && isWordBoundary(text, curr + 5))
                    || (text.startsWith("@default", curr) && isWordBoundary(text, curr + 8))) {
                return curr;
            }
            curr++;
        }
        return -1;
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

    private static boolean isWordBoundary(String s, int pos) {
        if (pos >= s.length()) return true;
        char c = s.charAt(pos);
        return !Character.isLetterOrDigit(c) && c != '_';
    }
}
