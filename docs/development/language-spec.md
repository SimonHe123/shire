---
layout: default
title: Language Specification
parent: Development
nav_order: 9
---

See in [ShireParser.bnf] for latest version.

```bnf
ShireFile ::= frontMatterHeader? (used | code | velocityExpr | TEXT_SEGMENT | NEWLINE | COMMENTS)*

frontMatterHeader ::= FRONTMATTER_START NEWLINE frontMatterEntries FRONTMATTER_END

frontMatterEntries ::= ((frontMatterEntry) WHITE_SPACE?)*
frontMatterEntry ::=
    "when" WHITE_SPACE? COLON WHITE_SPACE? conditionExpr NEWLINE?
    | frontMatterKey WHITE_SPACE? COLON WHITE_SPACE? (frontMatterValue | patternAction) NEWLINE?

frontMatterKey ::= FRONTMATTER_KEY | QUOTE_STRING
frontMatterValue ::= IDENTIFIER | NUMBER | QUOTE_STRING | DATE | BOOLEAN | frontMatterArray | (NEWLINE objectKeyValue)
frontMatterArray ::= LBRACKET (frontMatterValue (COMMA frontMatterValue)*) RBRACKET

objectKeyValue ::= (INDENT keyValue NEWLINE?)*
keyValue ::= frontMatterEntry

patternAction ::= pattern WHITE_SPACE? actionBlock
actionBlock ::=  blockStart (actionBody) blockEnd
actionBody ::= (actionExpr PIPE)* actionExpr
actionExpr ::= WHITE_SPACE? (caseBody | funcCall) WHITE_SPACE?

funcCall ::= funcName (LPAREN pipelineArgs? RPAREN)?
funcName ::= IDENTIFIER
pipelineArgs ::= (pipelineArg (COMMA pipelineArg)*)?
pipelineArg ::= NUMBER | IDENTIFIER | QUOTE_STRING

caseBody ::= CASE (WHITE_SPACE | NEWLINE)* QUOTE_STRING blockStart casePatternAction* blockEnd
casePatternAction ::= caseCondition blockStart (actionExpr PIPE)* actionExpr blockEnd
caseCondition ::= DEFAULT | pattern | QUOTE_STRING

pattern ::= PATTERN_EXPR

private blockStart ::= (WHITE_SPACE | NEWLINE)* OPEN_BRACE (WHITE_SPACE | NEWLINE)*
private blockEnd ::= (WHITE_SPACE | NEWLINE)* CLOSE_BRACE (WHITE_SPACE | NEWLINE)*

// todo
conditionExpr ::= expr
expr ::=
    logicalOrExpr
    | logicalAndExpr
    | eqComparisonExpr
    | ineqComparisonExpr
    | callExpr
    | qualRefExpr
    | simpleRefExpr
    | literalExpr
    | parenExpr

fake refExpr ::= expr? '.' IDENTIFIER
simpleRefExpr ::= IDENTIFIER {extends=refExpr elementType=refExpr}
qualRefExpr ::= expr '.' IDENTIFIER {extends=refExpr elementType=refExpr}

logicalOrExpr ::= expr '||' expr
logicalAndExpr ::= expr '&&' expr
eqComparisonExpr ::= expr eqComparisonOp expr
ineqComparisonExpr ::= expr ineqComparisonOp expr
callExpr ::= refExpr '(' expressionList? ')'
expressionList ::= expr (',' expr)*

literalExpr ::= literal
parenExpr ::= '(' expr ')'
// when
private eqComparisonOp ::= WHITE_SPACE? ('==' | '!=') WHITE_SPACE?
private ineqComparisonOp ::= WHITE_SPACE? ('<=' | '>=' | '<' | '>') WHITE_SPACE?

private literal ::= NUMBER
  | TRUE | FALSE
  | QUOTE_STRING
  | IDENTIFIER
  | "$" IDENTIFIER

used ::= (
    AGENT_START AGENT_ID
    | COMMAND_START COMMAND_ID (COLON COMMAND_PROP (SHARP LINE_INFO)?)?
    | VARIABLE_START (varId | varAccess)
)

// just make template pass success not fail
varAccess ::= OPEN_BRACE WHITE_SPACE? varId WHITE_SPACE? (DOT WHITE_SPACE? varId)* WHITE_SPACE? CLOSE_BRACE

varId ::= VARIABLE_ID

code ::=  CODE_BLOCK_START LANGUAGE_ID? NEWLINE? code_contents? CODE_BLOCK_END?

code_contents ::= (NEWLINE | CODE_CONTENT)*

velocityExpr ::=
     SHARP 'if' '(' WHITE_SPACE? VARIABLE_START? expr WHITE_SPACE?  ')'
    | SHARP 'else'
    | SHARP 'elseif' '(' WHITE_SPACE? VARIABLE_START? expr WHITE_SPACE? ')'
    | SHARP 'end'
    | SHARP 'endif'
```