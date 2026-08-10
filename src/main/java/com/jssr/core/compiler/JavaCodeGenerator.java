package com.jssr.core.compiler;

import com.jssr.core.JssrComponent;
import com.jssr.core.compiler.ast.TemplateNode;
import com.jssr.core.compiler.ast.TemplateParser;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AST-driven Java source code generator for JSSR precompiled JVM bytecode templates.
 * Emits direct Java rendering statements from TemplateNode AST trees while enforcing
 * 100% security context parity with the interpreted rendering engine.
 */
public final class JavaCodeGenerator {
    private static final AtomicInteger COUNTER = new AtomicInteger();

    /**
     * Generate a unique class name for a compiled template.
     *
     * @return Generated class name
     */
    public static String generateUniqueClassName() {
        return "JssrTemplate_Gen_" + System.currentTimeMillis() + "_" + COUNTER.incrementAndGet();
    }

    /**
     * Generate Java source code for a JSSR component class using AST compilation.
     *
     * @param componentClass Component class implementing JssrComponent
     * @param className Simple name for the generated class
     * @return Full Java source code string
     */
    public String generateClassSource(Class<? extends JssrComponent> componentClass, String className) {
        String extractedTemplate = tryExtractTemplate(componentClass);

        StringBuilder sb = new StringBuilder();
        sb.append("package com.jssr.core.compiler.generated;\n\n");
        sb.append("import com.jssr.core.JssrComponent;\n");
        sb.append("import com.jssr.core.compiler.CompiledTemplateExecutable;\n");
        sb.append("import java.util.Map;\n");
        sb.append("import java.util.Collections;\n\n");
        sb.append("public final class ").append(className).append(" implements CompiledTemplateExecutable {\n");
        sb.append("    @Override\n");
        sb.append("    public void render(JssrComponent component, Map<String, Object> localScope, StringBuilder sb) {\n");
        sb.append("        if (component == null) return;\n");

        if (extractedTemplate != null) {
            boolean canDirectCast = isAccessibleFromGenerated(componentClass);
            if (canDirectCast) {
                String canonicalName = componentClass.getCanonicalName();
                sb.append("        ").append(canonicalName).append(" c = (").append(canonicalName).append(") component;\n");
            }
            sb.append("        Map<String, Object> scope = (localScope == null) ? Collections.emptyMap() : localScope;\n");

            Map<String, Class<?>> recordFields = canDirectCast ? getRecordFieldTypes(componentClass) : Collections.emptyMap();
            List<TemplateNode> ast = TemplateParser.parse(extractedTemplate);

            AtomicInteger varSeq = new AtomicInteger();
            generateNodeStatements(ast, componentClass, recordFields, canDirectCast, sb, "scope", "        ", varSeq);
        } else {
            // Dynamic fallback for non-extractable template definitions
            sb.append("        String rawHtml = component.template();\n");
            sb.append("        if (rawHtml == null || rawHtml.isBlank()) {\n");
            sb.append("            if (rawHtml != null) sb.append(rawHtml);\n");
            sb.append("            return;\n");
            sb.append("        }\n");
            sb.append("        Map<String, Object> scope = (localScope == null) ? Collections.emptyMap() : localScope;\n");
            sb.append("        String controlFlowProcessed = JssrComponent.processControlFlow(component, scope, rawHtml);\n");
            sb.append("        String interpolatedHtml = JssrComponent.interpolateVariables(component, scope, controlFlowProcessed);\n");
            sb.append("        String finalHtml = JssrComponent.processCustomTags(interpolatedHtml);\n");
            sb.append("        if (finalHtml != null) sb.append(finalHtml);\n");
        }

        sb.append("    }\n");
        sb.append("}\n");

        return sb.toString();
    }

    private void generateNodeStatements(
            List<TemplateNode> nodes,
            Class<? extends JssrComponent> componentClass,
            Map<String, Class<?>> recordFields,
            boolean canDirectCast,
            StringBuilder sb,
            String currentScopeVar,
            String indent,
            AtomicInteger varSeq
    ) {
        for (int idx = 0; idx < nodes.size(); idx++) {
            TemplateNode node = nodes.get(idx);
            if (node instanceof TemplateNode.StaticTextNode staticText) {
                if (!staticText.text().isEmpty()) {
                    sb.append(indent).append("sb.append(\"")
                            .append(escapeJavaString(staticText.text()))
                            .append("\");\n");
                }
            } else if (node instanceof TemplateNode.InterpolationNode interp) {
                String expr = interp.expression();
                String activeAttr = interp.activeAttribute() == null ? "" : interp.activeAttribute();

                if (interp.inScript()) {
                    sb.append(indent).append("com.jssr.core.compiler.JssrSecurity.rejectScriptInterpolation(\"")
                            .append(escapeJavaString(expr)).append("\");\n");
                } else if (interp.inStyle()) {
                    sb.append(indent).append("com.jssr.core.compiler.JssrSecurity.rejectStyleInterpolation(\"")
                            .append(escapeJavaString(expr)).append("\");\n");
                } else if (interp.inComment()) {
                    sb.append(indent).append("com.jssr.core.compiler.JssrSecurity.rejectCommentInterpolation(\"")
                            .append(escapeJavaString(expr)).append("\");\n");
                } else if (interp.inTag()) {
                    if (interp.quoteChar() == 0 && activeAttr.isEmpty()) {
                        if (canDirectCast && recordFields.containsKey(expr)) {
                            Class<?> type = recordFields.get(expr);
                            if (type != boolean.class && type != Boolean.class &&
                                !com.jssr.core.BooleanAttribute.class.isAssignableFrom(type) &&
                                !com.jssr.core.HtmlAttribute.class.isAssignableFrom(type)) {
                                sb.append(indent).append("com.jssr.core.compiler.JssrSecurity.rejectFreestandingAttribute(\"")
                                        .append(escapeJavaString(expr)).append("\", \"").append(type.getSimpleName()).append("\");\n");
                            }
                        } else {
                            int id = varSeq.incrementAndGet();
                            sb.append(indent).append("Object val_fs_").append(id)
                                    .append(" = JssrComponent.resolveProperty(component, ").append(currentScopeVar)
                                    .append(", \"").append(escapeJavaString(expr)).append("\").value();\n");
                            sb.append(indent).append("if (val_fs_").append(id).append(" != null && !(")
                                    .append("val_fs_").append(id).append(" instanceof Boolean || ")
                                    .append("val_fs_").append(id).append(" instanceof com.jssr.core.BooleanAttribute || ")
                                    .append("val_fs_").append(id).append(" instanceof com.jssr.core.HtmlAttribute)) {\n");
                            sb.append(indent).append("    com.jssr.core.compiler.JssrSecurity.rejectFreestandingAttribute(\"")
                                    .append(escapeJavaString(expr)).append("\", val_fs_").append(id).append(".getClass().getSimpleName());\n");
                            sb.append(indent).append("}\n");
                        }
                    } else if (interp.quoteChar() == 0 && !activeAttr.isEmpty()) {
                        sb.append(indent).append("com.jssr.core.compiler.JssrSecurity.rejectUnquotedAttribute(\"")
                                .append(escapeJavaString(expr)).append("\", \"").append(escapeJavaString(activeAttr)).append("\");\n");
                    } else {
                        JssrSecurity.AttributeContext ctx = JssrSecurity.classifyAttribute(activeAttr);
                        switch (ctx) {
                            case SRCDOC -> sb.append(indent).append("com.jssr.core.compiler.JssrSecurity.rejectSrcdocAttribute(\"")
                                    .append(escapeJavaString(expr)).append("\");\n");
                            case FRAMEWORK_EXPRESSION -> sb.append(indent).append("com.jssr.core.compiler.JssrSecurity.rejectFrameworkAttribute(\"")
                                    .append(escapeJavaString(expr)).append("\", \"").append(escapeJavaString(activeAttr)).append("\");\n");
                            case EVENT_HANDLER -> sb.append(indent).append("com.jssr.core.compiler.JssrSecurity.rejectEventHandlerAttribute(\"")
                                    .append(escapeJavaString(expr)).append("\", \"").append(escapeJavaString(activeAttr)).append("\");\n");
                            case STYLE -> sb.append(indent).append("com.jssr.core.compiler.JssrSecurity.rejectStyleAttribute(\"")
                                    .append(escapeJavaString(expr)).append("\");\n");
                            case SRCSET -> {
                                if (canDirectCast && recordFields.containsKey(expr)) {
                                    Class<?> type = recordFields.get(expr);
                                    if (!com.jssr.core.SafeSrcSet.class.isAssignableFrom(type)) {
                                        sb.append(indent).append("com.jssr.core.compiler.JssrSecurity.rejectInvalidSrcSet(\"")
                                                .append(escapeJavaString(expr)).append("\", \"").append(escapeJavaString(activeAttr)).append("\");\n");
                                    }
                                } else {
                                    int id = varSeq.incrementAndGet();
                                    sb.append(indent).append("Object val_ss_").append(id)
                                            .append(" = JssrComponent.resolveProperty(component, ").append(currentScopeVar)
                                            .append(", \"").append(escapeJavaString(expr)).append("\").value();\n");
                                    sb.append(indent).append("if (val_ss_").append(id)
                                            .append(" != null && !(val_ss_").append(id).append(" instanceof com.jssr.core.SafeSrcSet)) {\n");
                                    sb.append(indent).append("    com.jssr.core.compiler.JssrSecurity.rejectInvalidSrcSet(\"")
                                            .append(escapeJavaString(expr)).append("\", \"").append(escapeJavaString(activeAttr)).append("\");\n");
                                    sb.append(indent).append("}\n");
                                }
                            }
                            case URL_LIST -> {
                                if (canDirectCast && recordFields.containsKey(expr)) {
                                    Class<?> type = recordFields.get(expr);
                                    if (!com.jssr.core.SafeUrlList.class.isAssignableFrom(type)) {
                                        sb.append(indent).append("com.jssr.core.compiler.JssrSecurity.rejectInvalidUrlList(\"")
                                                .append(escapeJavaString(expr)).append("\", \"").append(escapeJavaString(activeAttr)).append("\");\n");
                                    }
                                } else {
                                    int id = varSeq.incrementAndGet();
                                    sb.append(indent).append("Object val_ul_").append(id)
                                            .append(" = JssrComponent.resolveProperty(component, ").append(currentScopeVar)
                                            .append(", \"").append(escapeJavaString(expr)).append("\").value();\n");
                                    sb.append(indent).append("if (val_ul_").append(id)
                                            .append(" != null && !(val_ul_").append(id).append(" instanceof com.jssr.core.SafeUrlList)) {\n");
                                    sb.append(indent).append("    com.jssr.core.compiler.JssrSecurity.rejectInvalidUrlList(\"")
                                            .append(escapeJavaString(expr)).append("\", \"").append(escapeJavaString(activeAttr)).append("\");\n");
                                    sb.append(indent).append("}\n");
                                }
                            }
                            case URL -> {
                                if (canDirectCast && recordFields.containsKey(expr)) {
                                    Class<?> type = recordFields.get(expr);
                                    if (!com.jssr.core.SafeUrl.class.isAssignableFrom(type)) {
                                        sb.append(indent).append("com.jssr.core.compiler.JssrSecurity.rejectInvalidUrl(\"")
                                                .append(escapeJavaString(expr)).append("\", \"").append(escapeJavaString(activeAttr)).append("\");\n");
                                    }
                                } else {
                                    int id = varSeq.incrementAndGet();
                                    sb.append(indent).append("Object val_url_").append(id)
                                            .append(" = JssrComponent.resolveProperty(component, ").append(currentScopeVar)
                                            .append(", \"").append(escapeJavaString(expr)).append("\").value();\n");
                                    sb.append(indent).append("if (val_url_").append(id)
                                            .append(" != null && !(val_url_").append(id).append(" instanceof com.jssr.core.SafeUrl)) {\n");
                                    sb.append(indent).append("    com.jssr.core.compiler.JssrSecurity.rejectInvalidUrl(\"")
                                            .append(escapeJavaString(expr)).append("\", \"").append(escapeJavaString(activeAttr)).append("\");\n");
                                    sb.append(indent).append("}\n");
                                }
                            }
                            default -> {}
                        }

                        // For ALL attribute interpolations, reject RawHtml
                        if (!activeAttr.isEmpty()) {
                            if (canDirectCast && recordFields.containsKey(expr)) {
                                Class<?> type = recordFields.get(expr);
                                if (com.jssr.core.RawHtml.class.isAssignableFrom(type)) {
                                    sb.append(indent).append("com.jssr.core.compiler.JssrSecurity.rejectRawHtmlInAttribute(\"")
                                            .append(escapeJavaString(expr)).append("\", \"").append(escapeJavaString(activeAttr)).append("\");\n");
                                }
                            } else {
                                int id = varSeq.incrementAndGet();
                                sb.append(indent).append("Object val_raw_").append(id)
                                        .append(" = JssrComponent.resolveProperty(component, ").append(currentScopeVar)
                                        .append(", \"").append(escapeJavaString(expr)).append("\").value();\n");
                                sb.append(indent).append("if (val_raw_").append(id)
                                        .append(" instanceof com.jssr.core.RawHtml) {\n");
                                sb.append(indent).append("    com.jssr.core.compiler.JssrSecurity.rejectRawHtmlInAttribute(\"")
                                        .append(escapeJavaString(expr)).append("\", \"").append(escapeJavaString(activeAttr)).append("\");\n");
                                sb.append(indent).append("}\n");
                            }
                        }
                    }
                }

                if (canDirectCast && recordFields.containsKey(expr)) {
                    Class<?> type = recordFields.get(expr);
                    boolean isQuotedAttr = interp.inTag() && interp.quoteChar() != 0;
                    if (interp.inTag() && interp.quoteChar() == 0 && activeAttr.isEmpty() && (type == boolean.class || type == Boolean.class)) {
                        String attrName = expr.contains(".") ? expr.substring(expr.lastIndexOf('.') + 1) : expr;
                        if (type == boolean.class) {
                            sb.append(indent).append("if (c.").append(expr).append("()) sb.append(\"").append(escapeJavaString(attrName)).append("\");\n");
                        } else {
                            sb.append(indent).append("if (Boolean.TRUE.equals(c.").append(expr).append("())) sb.append(\"").append(escapeJavaString(attrName)).append("\");\n");
                        }
                    } else if (type == boolean.class || type == int.class || type == long.class ||
                        type == double.class || type == float.class || type == short.class || type == byte.class) {
                        sb.append(indent).append("sb.append(c.").append(expr).append("());\n");
                    } else if (type == Boolean.class || type == Integer.class || type == Long.class ||
                        type == Double.class || type == Float.class || type == Short.class || type == Byte.class) {
                        sb.append(indent).append("sb.append(c.").append(expr).append("() != null ? c.").append(expr).append("() : \"\");\n");
                    } else if (type == char.class) {
                        sb.append(indent).append("sb.append(JssrComponent.escapeHtml(String.valueOf(c.").append(expr).append("())));\n");
                    } else if (type == Character.class) {
                        sb.append(indent).append("sb.append(c.").append(expr).append("() != null ? JssrComponent.escapeHtml(String.valueOf(c.").append(expr).append("())) : \"\");\n");
                    } else if (com.jssr.core.RawHtml.class.isAssignableFrom(type)) {
                        sb.append(indent).append("sb.append(c.").append(expr).append("() != null ? c.").append(expr).append("().template() : \"\");\n");
                    } else if (com.jssr.core.SafeUrl.class.isAssignableFrom(type)) {
                        sb.append(indent).append("sb.append(c.").append(expr).append("() != null ? JssrComponent.escapeHtml(c.").append(expr).append("().template()) : \"\");\n");
                    } else if (com.jssr.core.SafeSrcSet.class.isAssignableFrom(type)) {
                        sb.append(indent).append("sb.append(c.").append(expr).append("() != null ? JssrComponent.escapeHtml(c.").append(expr).append("().template()) : \"\");\n");
                    } else if (com.jssr.core.SafeUrlList.class.isAssignableFrom(type)) {
                        sb.append(indent).append("sb.append(c.").append(expr).append("() != null ? JssrComponent.escapeHtml(c.").append(expr).append("().template()) : \"\");\n");
                    } else if (java.util.Optional.class.isAssignableFrom(type)) {
                        sb.append(indent).append("sb.append(c.").append(expr).append("() != null && c.").append(expr).append("().isPresent() ? JssrComponent.escapeHtml(String.valueOf(c.").append(expr).append("().get())) : \"\");\n");
                    } else if (com.jssr.core.BooleanAttribute.class.isAssignableFrom(type)) {
                        sb.append(indent).append("sb.append(c.").append(expr).append("() != null ? c.").append(expr).append("().template() : \"\");\n");
                    } else if (com.jssr.core.HtmlAttribute.class.isAssignableFrom(type)) {
                        if (isQuotedAttr) {
                            sb.append(indent).append("sb.append(c.").append(expr).append("() != null ? JssrComponent.escapeHtml(c.").append(expr).append("().template()) : \"\");\n");
                        } else {
                            sb.append(indent).append("sb.append(c.").append(expr).append("() != null ? c.").append(expr).append("().template() : \"\");\n");
                        }
                    } else if (JssrComponent.class.isAssignableFrom(type)) {
                        if (isQuotedAttr) {
                            sb.append(indent).append("sb.append(c.").append(expr).append("() != null ? JssrComponent.escapeHtml(c.").append(expr).append("().render()) : \"\");\n");
                        } else {
                            sb.append(indent).append("sb.append(c.").append(expr).append("() != null ? c.").append(expr).append("().render() : \"\");\n");
                        }
                    } else {
                        sb.append(indent).append("sb.append(JssrComponent.escapeHtml(c.").append(expr).append("() != null ? String.valueOf(c.").append(expr).append("()) : \"\"));\n");
                    }
                } else {
                    sb.append(indent).append("sb.append(JssrComponent.renderInterpolatedExpression(component, ").append(currentScopeVar).append(", \"")
                            .append(escapeJavaString(expr)).append("\", ")
                            .append(interp.inTag()).append(", \"")
                            .append(escapeJavaString(activeAttr)).append("\", (char) ")
                            .append((int) interp.quoteChar()).append("));\n");
                }
            } else if (node instanceof TemplateNode.IfNode ifNode) {
                int id = varSeq.incrementAndGet();
                String cond = ifNode.condition();
                String condResVar = "condRes_" + id;
                String thenScopeVar = "thenScope_" + id;

                sb.append(indent).append("com.jssr.core.JssrComponent.ConditionResult ").append(condResVar)
                        .append(" = JssrComponent.evaluateConditionWithBinding(component, ").append(currentScopeVar)
                        .append(", \"").append(escapeJavaString(cond)).append("\");\n");

                sb.append(indent).append("if (").append(condResVar).append(".matches()) {\n");
                sb.append(indent).append("    Map<String, Object> ").append(thenScopeVar).append(" = ").append(currentScopeVar).append(";\n");
                sb.append(indent).append("    if (!").append(condResVar).append(".bindings().isEmpty()) {\n");
                sb.append(indent).append("        ").append(thenScopeVar).append(" = new java.util.HashMap<>(").append(currentScopeVar).append(");\n");
                sb.append(indent).append("        ").append(thenScopeVar).append(".putAll(").append(condResVar).append(".bindings());\n");
                sb.append(indent).append("    }\n");

                generateNodeStatements(ifNode.thenBranch(), componentClass, recordFields, canDirectCast, sb, thenScopeVar, indent + "    ", varSeq);

                if (!ifNode.elseBranch().isEmpty()) {
                    sb.append(indent).append("} else {\n");
                    generateNodeStatements(ifNode.elseBranch(), componentClass, recordFields, canDirectCast, sb, currentScopeVar, indent + "    ", varSeq);
                }
                sb.append(indent).append("}\n");
            } else if (node instanceof TemplateNode.ForNode forNode) {
                int id = varSeq.incrementAndGet();
                String item = forNode.item();
                String collection = forNode.collection();
                String collCode;
                if (canDirectCast && recordFields.containsKey(collection)) {
                    collCode = "c." + collection + "()";
                } else {
                    collCode = "JssrComponent.resolveProperty(component, " + currentScopeVar + ", \"" + escapeJavaString(collection) + "\").value()";
                }

                String varList = "list_" + id;
                String nextScopeVar = "loopScope_" + id;
                sb.append(indent).append("java.util.List<?> ").append(varList).append(" = JssrComponent.toList(").append(collCode).append(");\n");
                sb.append(indent).append("if (!").append(varList).append(".isEmpty()) {\n");
                sb.append(indent).append("    for (Object ").append(item).append(" : ").append(varList).append(") {\n");
                sb.append(indent).append("        Map<String, Object> ").append(nextScopeVar).append(" = new java.util.HashMap<>(").append(currentScopeVar).append(");\n");
                sb.append(indent).append("        ").append(nextScopeVar).append(".put(\"").append(item).append("\", ").append(item).append(");\n");

                generateNodeStatements(forNode.body(), componentClass, recordFields, canDirectCast, sb, nextScopeVar, indent + "        ", varSeq);

                sb.append(indent).append("    }\n");

                if (!forNode.elseBranch().isEmpty()) {
                    sb.append(indent).append("} else {\n");
                    generateNodeStatements(forNode.elseBranch(), componentClass, recordFields, canDirectCast, sb, currentScopeVar, indent + "    ", varSeq);
                }
                sb.append(indent).append("}\n");
            } else if (node instanceof TemplateNode.TryNode tryNode) {
                int id = varSeq.incrementAndGet();
                String catchScopeVar = "catchScope_" + id;
                String catchVar = (tryNode.catchVar() == null || tryNode.catchVar().isBlank()) ? "err" : tryNode.catchVar();

                sb.append(indent).append("try {\n");
                generateNodeStatements(tryNode.tryBody(), componentClass, recordFields, canDirectCast, sb, currentScopeVar, indent + "    ", varSeq);

                if (!tryNode.catchBody().isEmpty()) {
                    sb.append(indent).append("} catch (Throwable err_ex_").append(id).append(") {\n");
                    sb.append(indent).append("    Map<String, Object> ").append(catchScopeVar).append(" = new java.util.HashMap<>(").append(currentScopeVar).append(");\n");
                    sb.append(indent).append("    String safeMsg_").append(id).append(" = err_ex_").append(id).append(".getMessage() == null ? err_ex_").append(id).append(".getClass().getSimpleName() : err_ex_").append(id).append(".getMessage();\n");
                    sb.append(indent).append("    ").append(catchScopeVar).append(".put(\"").append(catchVar).append("\", err_ex_").append(id).append(");\n");
                    sb.append(indent).append("    ").append(catchScopeVar).append(".put(\"").append(catchVar).append(".message\", safeMsg_").append(id).append(");\n");
                    sb.append(indent).append("    ").append(catchScopeVar).append(".put(\"err\", err_ex_").append(id).append(");\n");
                    sb.append(indent).append("    ").append(catchScopeVar).append(".put(\"err.message\", safeMsg_").append(id).append(");\n");

                    generateNodeStatements(tryNode.catchBody(), componentClass, recordFields, canDirectCast, sb, catchScopeVar, indent + "    ", varSeq);
                }

                if (!tryNode.finallyBody().isEmpty()) {
                    sb.append(indent).append("} finally {\n");
                    generateNodeStatements(tryNode.finallyBody(), componentClass, recordFields, canDirectCast, sb, currentScopeVar, indent + "    ", varSeq);
                }

                sb.append(indent).append("}\n");
            } else if (node instanceof TemplateNode.ThrowNode throwNode) {
                String expr = throwNode.expression().replace("'", "").replace("\"", "");
                sb.append(indent).append("if (true) throw new RuntimeException(\"")
                        .append(escapeJavaString(expr)).append("\");\n");
                break;
            } else if (node instanceof TemplateNode.ContinueNode) {
                sb.append(indent).append("continue;\n");
                break;
            } else if (node instanceof TemplateNode.BreakNode) {
                sb.append(indent).append("break;\n");
                break;
            } else if (node instanceof TemplateNode.SwitchNode switchNode) {
                int id = varSeq.incrementAndGet();
                String expr = switchNode.expression();
                sb.append(indent).append("String switchVal_").append(id).append(" = String.valueOf(JssrComponent.resolveProperty(component, ").append(currentScopeVar).append(", \"")
                        .append(escapeJavaString(expr)).append("\").value());\n");

                boolean firstCase = true;
                for (Map.Entry<String, List<TemplateNode>> caseEntry : switchNode.cases().entrySet()) {
                    String caseExpr = caseEntry.getKey();
                    String cleanCase = caseExpr.replace("'", "").replace("\"", "");
                    if (firstCase) {
                        sb.append(indent).append("if (\"").append(escapeJavaString(cleanCase))
                                .append("\".equalsIgnoreCase(switchVal_").append(id).append(")) {\n");
                        firstCase = false;
                    } else {
                        sb.append(indent).append("} else if (\"").append(escapeJavaString(cleanCase))
                                .append("\".equalsIgnoreCase(switchVal_").append(id).append(")) {\n");
                    }
                    generateNodeStatements(caseEntry.getValue(), componentClass, recordFields, canDirectCast, sb, currentScopeVar, indent + "    ", varSeq);
                }

                if (!switchNode.defaultBranch().isEmpty()) {
                    if (!firstCase) {
                        sb.append(indent).append("} else {\n");
                    }
                    generateNodeStatements(switchNode.defaultBranch(), componentClass, recordFields, canDirectCast, sb, currentScopeVar, indent + "    ", varSeq);
                }
                if (!firstCase || !switchNode.defaultBranch().isEmpty()) {
                    sb.append(indent).append("}\n");
                }
            } else if (node instanceof TemplateNode.ComponentNode child) {
                sb.append(indent).append("sb.append(JssrComponent.renderCustomTag(component, ").append(currentScopeVar).append(", \"")
                        .append(escapeJavaString(child.tagName())).append("\", Collections.emptyMap()));\n");
            }
        }
    }

    private boolean isAccessibleFromGenerated(Class<?> clazz) {
        if (clazz == null) return false;
        if (clazz.getCanonicalName() == null) return false;
        if (!Modifier.isPublic(clazz.getModifiers())) return false;
        Class<?> curr = clazz.getDeclaringClass();
        while (curr != null) {
            if (!Modifier.isPublic(curr.getModifiers())) {
                return false;
            }
            curr = curr.getDeclaringClass();
        }
        return true;
    }

    private String escapeJavaString(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 32 || c > 126) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    private Map<String, Class<?>> getRecordFieldTypes(Class<?> componentClass) {
        Map<String, Class<?>> map = new HashMap<>();
        if (componentClass.isRecord()) {
            for (RecordComponent rc : componentClass.getRecordComponents()) {
                map.put(rc.getName(), rc.getType());
            }
        }
        return map;
    }

    /**
     * Helper to attempt dummy instantiation of a record component to retrieve sample template.
     *
     * @param componentClass Component record class
     * @return Raw template string or null if instantiation fails
     */
    public static String tryExtractTemplate(Class<? extends JssrComponent> componentClass) {
        try {
            if (componentClass.isRecord()) {
                RecordComponent[] rcs = componentClass.getRecordComponents();
                Class<?>[] paramTypes = new Class<?>[rcs.length];
                Object[] dummyArgs = new Object[rcs.length];
                for (int i = 0; i < rcs.length; i++) {
                    paramTypes[i] = rcs[i].getType();
                    dummyArgs[i] = getDummyValue(rcs[i].getName(), rcs[i].getType());
                }
                Constructor<? extends JssrComponent> ctor = componentClass.getDeclaredConstructor(paramTypes);
                ctor.setAccessible(true);
                JssrComponent dummy = ctor.newInstance(dummyArgs);
                return dummy.template();
            } else {
                Constructor<? extends JssrComponent> ctor = componentClass.getDeclaredConstructor();
                ctor.setAccessible(true);
                JssrComponent dummy = ctor.newInstance();
                return dummy.template();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static Object getDummyValue(String name, Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0.0d;
        if (type == float.class) return 0.0f;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        if (type == char.class) return '\0';
        return null;
    }
}
