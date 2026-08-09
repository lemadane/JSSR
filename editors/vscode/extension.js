const vscode = require('vscode');

/**
 * Activate the JSSR VS Code extension.
 * @param {vscode.ExtensionContext} context
 */
function activate(context) {
    const formatterProvider = {
        provideDocumentFormattingEdits(document, options, token) {
            return formatJssrDocument(document, options);
        }
    };

    context.subscriptions.push(
        vscode.languages.registerDocumentFormattingEditProvider('java', formatterProvider)
    );
}

/**
 * Format JSSR multiline text blocks inside a Java document.
 * @param {vscode.TextDocument} document
 * @param {vscode.FormattingOptions} options
 * @returns {vscode.TextEdit[]}
 */
function formatJssrDocument(document, options) {
    const edits = [];
    const text = document.getText();
    const config = vscode.workspace.getConfiguration('jssr');
    const indentSize = config.get('format.indentSize') || options.tabSize || 4;
    const indentStep = ' '.repeat(indentSize);

    // Regex to match Java 17 multiline text blocks: """ ... """
    const textBlockRegex = /"""([\s\S]*?)"""/g;
    let match;

    while ((match = textBlockRegex.exec(text)) !== null) {
        const fullMatch = match[0];
        const rawContent = match[1];
        const matchStartOffset = match.index;
        const matchEndOffset = matchStartOffset + fullMatch.length;

        // Ensure we are inside a JSSR component template or HTML block
        if (!isJssrTemplateCandidate(text, matchStartOffset, rawContent)) {
            continue;
        }

        const startPos = document.positionAt(matchStartOffset);
        const endPos = document.positionAt(matchEndOffset);

        // Determine base indentation of the opening/closing text block line
        const blockLine = document.lineAt(startPos.line).text;
        const baseIndentMatch = blockLine.match(/^(\s*)/);
        const baseIndent = baseIndentMatch ? baseIndentMatch[1] : '';

        const formattedBlock = formatTemplateContent(rawContent, baseIndent, indentStep);

        edits.push(vscode.TextEdit.replace(
            new vscode.Range(startPos, endPos),
            formattedBlock
        ));
    }

    return edits;
}

/**
 * Check if a text block match is likely a JSSR component template.
 */
function isJssrTemplateCandidate(fullText, matchOffset, content) {
    // Check if content contains HTML tags or JSSR directives or interpolation
    const hasHtmlOrJssr = /<[a-zA-Z0-9_\-\:]+|@if|@for|@switch|@try|\$\{/.test(content);
    if (!hasHtmlOrJssr) {
        return false;
    }

    // Check surrounding Java code for JssrComponent or template() method
    const textBefore = fullText.substring(Math.max(0, matchOffset - 500), matchOffset);
    return textBefore.includes('JssrComponent') || textBefore.includes('template()') || textBefore.includes('return');
}

/**
 * Format raw JSSR template text block lines with proper indentation.
 */
function formatTemplateContent(rawContent, baseIndent, indentStep) {
    const lines = rawContent.split('\n');
    if (lines.length === 0) return '"""' + rawContent + '"""';

    const formattedLines = [];
    let currentDepth = 1; // 1 level deeper than baseIndent by default inside text block

    for (let i = 0; i < lines.length; i++) {
        const line = lines[i].trim();

        if (line.length === 0) {
            formattedLines.push('');
            continue;
        }

        // Check if line starts with a closing/dedent directive or closing HTML tag
        const isDedentDirective = /^@(else|elseif|case|default|catch|finally|end)\b/.test(line);
        const isClosingHtmlTag = /^<\/[a-zA-Z0-9_\-\:]+>/.test(line);

        if ((isDedentDirective || isClosingHtmlTag) && currentDepth > 1) {
            currentDepth--;
        }

        const indent = baseIndent + indentStep.repeat(Math.max(0, currentDepth));
        formattedLines.push(indent + line);

        // Check if line starts an indenting directive or opening HTML tag
        const isIndentDirective = /^@(if|elseif|else|for|switch|case|default|try|catch|finally)\b/.test(line);
        const isOpeningHtmlTag = /^<([a-zA-Z0-9_\-\:]+)[^>]*>(?!\s*<\/\1>)/.test(line);
        const isSelfClosingOrVoid = /<[^>]+\/>|^<(meta|link|img|br|hr|input|area|base|col|embed|param|source|track|wbr)\b/i.test(line);

        if ((isIndentDirective || (isOpeningHtmlTag && !isSelfClosingOrVoid))) {
            currentDepth++;
        }
    }

    return '"""\n' + formattedLines.join('\n') + '\n' + baseIndent + '"""';
}

function deactivate() {}

module.exports = {
    activate,
    deactivate,
    formatTemplateContent
};
