grammar Accord;

@header {
    package accord.regulationsimporter;
}

/*
 * Parser Rules
 */

// operators and booleans
bOp: LT | GT | EQ | LTEQ | NEQ | GTEQ;
lOp: AND | OR;
mathOp: PLUS | MINUS | DIVIDE | TIMES;

// language building blocks
variable: COLON IDENT;
stringConstant: STRING;
boolean: TRUE | FALSE;
value: boolean | stringConstant | NUMBER unit? | constant;
constant: PI;





accordRules: accordRule (lOp accordRules)?;
target: variable | expression | function;
targetOrValue: target | value;
targetNumberConstant: target|NUMBER|constant;
function: variable OB targetOrValue? (COMMA targetOrValue )* CB;
expression: OB targetNumberConstant mathOp targetNumberConstant CB;
accordRule: target ( ( bOp (target | value)) | (flOp ARROW OB accordRules CB ));
flOp: NOT? ((FORALL (OB accordRules CB)?) | EXISTS);







//unit notation
unit: (COLON DIVIDE? unitExpression) | PC;
unitExpression: unitTerm ( (TIMES | DIVIDE) unitTerm)*;
unitTerm: unitElement exponent?;
unitElement: simpleUnit | (OB unitExpression CB);
exponent: (PLUS | MINUS)? NUMBER;
simpleUnit: IDENT | NUMBER;


/*
 * Lexer Rules
 */
 
NUMBER: [0-9]+ ('.' [0-9]+)?;
WS: [ \t]+ -> skip; 
OB: '(';
CB: ')';
AND: '&&';
OR: '||';
NOT: ('n'|'N')('o'|'O')('t'|'T');
QUOTE: '"';
COMMA: ',';
LT: '<';
GT: '>';
ARROW: '=>';
LTEQ: '<=';
GTEQ: '>=';
EQ: '==';
NEQ: '!=';
DIVIDE: '/';
TIMES: '*';
PLUS: '+';
MINUS: '-';
COLON: ':';
PC: '%';
TRUE: ('t'|'T')('r'|'R')('u'|'U')('e'|'E');
FALSE: ('f'|'F')('a'|'A')('l'|'L')('s'|'S')('e'|'E');
PI: ('p'|'P')('i'|'I');
FORALL: ('f'|'F')('o'|'O')('r'|'R')('a'|'A')('l'|'L')('l'|'L');
EXISTS: ('e'|'E')('x'|'X')('i'|'I')('s'|'S')('t'|'T')('s'|'S');
IDENT: ([a-z] | [A-Z] | '_') ([a-z] | [A-Z] | [0-9] | '_')*;
STRING: '"' ~["]* '"';