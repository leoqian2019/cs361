/*
 * File: Scanner.java
 * Authors: Haoyu Song and Dale Skrien
 * Authors: Nick English, Nico Hillison, Leo Qian
 * Date: 4/16/22
 *
 * In the grammar below, the variables are enclosed in angle brackets and
 * "::=" is used instead of "-->" to separate a variable from its rules.
 * The special character "|" is used to separate the rules for each variable
 * (but note that "||" is an operator).
 * EMPTY indicates a rule with an empty right hand side.
 * All other symbols in the rules are terminals.
 */
package proj8EnglishHillisonQian.bantam.parser;

import proj8EnglishHillisonQian.bantam.ast.*;
import proj8EnglishHillisonQian.bantam.lexer.Scanner;
import proj8EnglishHillisonQian.bantam.lexer.Token;
import proj8EnglishHillisonQian.bantam.treedrawer.Drawer;
import proj8EnglishHillisonQian.bantam.util.CompilationException;
import proj8EnglishHillisonQian.bantam.util.Error;
import proj8EnglishHillisonQian.bantam.util.ErrorHandler;
import static proj8EnglishHillisonQian.bantam.lexer.Token.Kind.*;

/**
 * Reads in Tokens from a Scanner and constructs an AST.
 */
public class Parser
{
    // instance variables
    private Scanner scanner; // provides the tokens
    private Token currentToken; // the lookahead token
    private ErrorHandler errorHandler; // collects & organizes the error messages
    private String filename; // name of file being parsed

    // constructor
    public Parser(ErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }

    /**
     * parse the given file and return the root node of the AST
     * @param filename The name of the Bantam Java file to be parsed
     * @return The Program node forming the root of the AST generated by the parser
     */
    public Program parse(String filename) {
        this.scanner = new Scanner(filename, errorHandler);
        this.filename = filename;
        currentToken = scanner.scan();
        return parseProgram();
    }


    // <Program> ::= <Class> | <Class> <Program>
    private Program parseProgram() {
        int position = currentToken.position;
        ClassList clist = new ClassList(position);

        while (currentToken.kind != EOF) {
            Class_ aClass = parseClass();
            clist.addElement(aClass);
        }

        return new Program(position, clist);
    }


    // <Class> ::= CLASS <Identifier> <ExtendsClause> { <MemberList> }
    // <ExtendsClause> ::= EXTENDS <Identifier> | EMPTY
    // <MemberList> ::= EMPTY | <Member> <MemberList>
    private Class_ parseClass() throws  CompilationException{

        if(currentToken.kind != CLASS){
            errorHandler.register(Error.Kind.PARSE_ERROR, "Expecting class");
            throw new CompilationException(errorHandler);
        }

        int pos = currentToken.position;
        currentToken = scanner.scan();
        String name = parseIdentifier();

        String parent = null;
        if(currentToken.kind == EXTENDS){
            currentToken = scanner.scan();
            parent = parseIdentifier();
        }

        if (currentToken.kind != LCURLY) {
            errorHandler.register(Error.Kind.PARSE_ERROR, "Invalid class declaration");
            throw new CompilationException(errorHandler);
        }

        currentToken = scanner.scan();
        MemberList members = new MemberList(currentToken.position);
        while(currentToken.kind != RCURLY){
            members.addElement(parseMember());
        }

        currentToken = scanner.scan();
        return new Class_(pos, filename, name, parent, members);
    }


    //Fields and Methods
    // <Member> ::= <Field> | <Method>
    // <Method> ::= <Type> <Identifier> ( <Parameters> ) <BlockStmt>
    // <Field> ::= <Type> <Identifier> <InitialValue> ;
    // <InitialValue> ::= EMPTY | = <Expression>
    private Member parseMember() {
        int pos = currentToken.position;
        String type = parseType();
        String name = parseIdentifier();

        // if this member is not a method
        if(currentToken.kind != LPAREN){
            Expr initVal = null;
            if(currentToken.kind != SEMICOLON) {
                if(currentToken.kind == ASSIGN){
                    currentToken = scanner.scan();
                    initVal = parseExpression();
                    if(currentToken.kind != SEMICOLON){
                        errorHandler.register(Error.Kind.PARSE_ERROR, "Invalid field declaration - missing ';'");
                        throw new CompilationException(errorHandler);
                    }
                }
                else{
                    errorHandler.register(Error.Kind.PARSE_ERROR, "Invalid field declaration - missing '='");
                    throw new CompilationException(errorHandler);
                }

            }
            currentToken = scanner.scan();
            return new Field(pos, type, name, initVal);
        } else {
            currentToken = scanner.scan();
            FormalList params = parseParameters();
            if(currentToken.kind != RPAREN){
                errorHandler.register(Error.Kind.PARSE_ERROR, "Invalid method declaration");
                throw new CompilationException(errorHandler);
            }
            currentToken = scanner.scan();
            StmtList body = parseBlock().getStmtList();
            return new Method(pos, type, name, params, body);
        }

    }


    //-----------------------------------
    //Statements
    // <Stmt> ::= <WhileStmt> | <ReturnStmt> | <BreakStmt> | <VarDeclaration>
    //             | <ExpressionStmt> | <ForStmt> | <BlockStmt> | <IfStmt>
    private Stmt parseStatement() {
            Stmt stmt;

            switch (currentToken.kind) {
                case IF:
                    stmt = parseIf();
                    break;
                case LCURLY:
                    stmt = parseBlock();
                    break;
                case VAR:
                    stmt = parseVarDeclaration();
                    break;
                case RETURN:
                    stmt = parseReturn();
                    break;
                case FOR:
                    stmt = parseFor();
                    break;
                case WHILE:
                    stmt = parseWhile();
                    break;
                case BREAK:
                    stmt = parseBreak();
                    break;
                default:
                    stmt = parseExpressionStmt();
            }

            return stmt;
    }


    // <WhileStmt> ::= WHILE ( <Expression> ) <Stmt>
    private Stmt parseWhile() {
        int pos = currentToken.position;
        currentToken = scanner.scan();
        if(currentToken.kind != LPAREN){
            errorHandler.register(Error.Kind.PARSE_ERROR, "Invalid while - missing (");
            throw new CompilationException(errorHandler);
        }
        currentToken = scanner.scan();
        Expr expr = parseExpression();
        if(currentToken.kind != RPAREN){
            errorHandler.register(Error.Kind.PARSE_ERROR, "Invalid while - missing )");
            throw new CompilationException(errorHandler);
        }
        currentToken = scanner.scan();
        Stmt stmt = parseStatement();
        return new WhileStmt(pos, expr, stmt);
    }


    // <ReturnStmt> ::= RETURN <Expression> ; | RETURN ;
    private Stmt parseReturn() throws CompilationException {
        Expr expr = null;
        int pos = currentToken.position;
        currentToken = scanner.scan();

        if(currentToken.kind != SEMICOLON){
            expr = parseExpression();
            if(currentToken.kind != SEMICOLON){
                errorHandler.register(Error.Kind.PARSE_ERROR, "Invalid return statement");
                throw new CompilationException(errorHandler);
            }
        }
        currentToken = scanner.scan();
        return new ReturnStmt(pos, expr);
    }


    // <BreakStmt> ::= BREAK ;
    private Stmt parseBreak() {
        int pos = currentToken.position;
        currentToken = scanner.scan();

        if(currentToken.kind != SEMICOLON){
            errorHandler.register(Error.Kind.PARSE_ERROR, "Invalid break");
            throw new CompilationException(errorHandler);
        }
        currentToken = scanner.scan();
        return new BreakStmt(pos);
    }


    // <ExpressionStmt> ::= <Expression> ;
    private ExprStmt parseExpressionStmt() {
        int pos = currentToken.position;
        Expr expr = parseExpression();

        if(currentToken.kind != SEMICOLON){
            errorHandler.register(Error.Kind.PARSE_ERROR, "Invalid expr - missing ;");
            throw new CompilationException(errorHandler);
        }
        currentToken = scanner.scan();
        return new ExprStmt(pos, expr);
    }


    // <VarDeclaration> ::= VAR <Id> = <Expression> ;
    // Every local variable must be initialized
    private DeclStmt parseVarDeclaration() {
        int pos = currentToken.position;
        currentToken = scanner.scan();
        String name = parseIdentifier();

        if(currentToken.kind != ASSIGN){
            errorHandler.register(Error.Kind.PARSE_ERROR, "Invalid var declaration - need '='");
            throw new CompilationException(errorHandler);
        }
        currentToken = scanner.scan();
        Expr init = parseExpression();
        if(currentToken.kind != SEMICOLON){
            errorHandler.register(Error.Kind.PARSE_ERROR, "Invalid var declaration - need ';'");
            throw new CompilationException(errorHandler);
        }
        currentToken = scanner.scan();
        return new DeclStmt(pos, name, init);
    }


    // <ForStmt> ::= FOR ( <Start> ; <Terminate> ; <Increment> ) <STMT>
    // <Start> ::=     EMPTY | <Expression>
    // <Terminate> ::= EMPTY | <Expression>
    // <Increment> ::= EMPTY | <Expression>
    private ForStmt parseFor() {
        int pos = currentToken.position;
        Expr start = null;
        Expr terminate = null;
        Expr increament = null;
        Stmt body = null;
        currentToken = scanner.scan();
        if(currentToken.kind == LPAREN){
            currentToken = scanner.scan();
            if(currentToken.kind != RPAREN){
                if(currentToken.kind != SEMICOLON){
                    start = parseExpression();
                } else {currentToken = scanner.scan();}
                if(currentToken.kind == SEMICOLON){
                    currentToken = scanner.scan();
                    if(currentToken.kind != SEMICOLON) {
                        terminate = parseExpression();
                    } else {currentToken = scanner.scan();}
                    if(currentToken.kind == SEMICOLON){
                        currentToken = scanner.scan();
                        if(currentToken.kind != SEMICOLON) {
                            increament = parseExpression();
                        } else {currentToken = scanner.scan();}
                    }
                }
            }
            if(currentToken.kind == RPAREN){
                currentToken = scanner.scan();
                body = parseStatement();
                return new ForStmt(pos,start,terminate,increament,body);
            }
        }
        errorHandler.register(Error.Kind.PARSE_ERROR, "Invalid for statement, found " + currentToken.spelling);
        throw new CompilationException(errorHandler);
    }


    // <BlockStmt> ::= { <Body> }
    // <Body> ::= EMPTY | <Stmt> <Body>
    private BlockStmt parseBlock() {
        int pos = currentToken.position;
        StmtList statements = new StmtList(pos);

        if(currentToken.kind == LCURLY){
            currentToken = scanner.scan();
            while(currentToken.kind != RCURLY){
                statements.addElement(parseStatement());
            }
        }
        else{
            errorHandler.register(Error.Kind.PARSE_ERROR, "Invalid block statement - missing '{'");
            throw new CompilationException(errorHandler);
        }
        currentToken = scanner.scan();
        return new BlockStmt(pos,statements);

    }


    // <IfStmt> ::= IF ( <Expr> ) <Stmt> | IF ( <Expr> ) <Stmt> ELSE <Stmt>
    private Stmt parseIf() {
        int pos = currentToken.position;
        Expr pred = null;
        Stmt then = null;
        Stmt elseStmt = null;

        currentToken = scanner.scan();
        if(currentToken.kind == LPAREN){
            currentToken = scanner.scan();
            pred = parseExpression();
            if(currentToken.kind == RPAREN){
                currentToken = scanner.scan();
                then = parseStatement();
                if(currentToken.kind == ELSE){
                    currentToken = scanner.scan();
                    elseStmt = parseStatement();
                }
                return new IfStmt(pos,pred,then,elseStmt);
            }
        }
        errorHandler.register(Error.Kind.PARSE_ERROR, "Invalid if statement");
        throw new CompilationException(errorHandler);
    }


    //-----------------------------------------
    // Expressions
    // Here we use different rules than the grammar on page 49
    // of the manual to handle the precedence of operations

    // <Expression> ::= <LogicalORExpr> <OptionalAssignment>
    // <OptionalAssignment> ::= EMPTY | = <Expression>
    private Expr parseExpression() {
        int pos = currentToken.position;
        Expr expr = parseOrExpr();
        if(currentToken.kind == ASSIGN && (expr instanceof VarExpr)){
            currentToken = scanner.scan();
            Expr assign = parseExpression();
            return new AssignExpr(pos, null, ((VarExpr) expr).getName(), assign);
        }
        return expr;
    }


    // <LogicalOR> ::= <logicalAND> <LogicalORRest>
    // <LogicalORRest> ::= EMPTY |  || <LogicalAND> <LogicalORRest>
    private Expr parseOrExpr() {
        int position = currentToken.position;
        Expr left;

        left = parseAndExpr();
        while (currentToken.spelling.equals("||")) {
            currentToken = scanner.scan();
            Expr right = parseAndExpr();
            left = new BinaryLogicOrExpr(position, left, right);
        }

        return left;
    }


    // <LogicalAND> ::= <ComparisonExpr> <LogicalANDRest>
    // <LogicalANDRest> ::= EMPTY |  && <ComparisonExpr> <LogicalANDRest>
    private Expr parseAndExpr() {
        int pos = currentToken.position;
        Expr left = parseEqualityExpr();
        while(currentToken.spelling.equals("&&")){
            currentToken = scanner.scan();
            Expr right = parseEqualityExpr();
            left = new BinaryLogicAndExpr(pos, left, right);
        }
        return left;
    }


    // <ComparisonExpr> ::= <RelationalExpr> <equalOrNotEqual> <RelationalExpr> |
    //                      <RelationalExpr>
    // <equalOrNotEqual> ::=  = | !=
    private Expr parseEqualityExpr() {
        int pos = currentToken.position;
        Expr left = parseRelationalExpr();
        if(!currentToken.spelling.equals("==") && !currentToken.spelling.equals("!=")){
            return left;
        }
        String op = currentToken.spelling;
        currentToken = scanner.scan();
        Expr right = parseRelationalExpr();
        if(op.equals("==")){
            return new BinaryCompEqExpr(pos,left,right);
        }
        return new BinaryCompNeExpr(pos,left,right);
    }


    // <RelationalExpr> ::= <AddExpr> | <AddExpr> <ComparisonOp> <AddExpr>
    // <ComparisonOp> ::= < | > | <= | >=
    private Expr parseRelationalExpr() {
        int pos = currentToken.position;
        Expr left = parseAddExpr();
        if(currentToken.kind != COMPARE || currentToken.spelling.equals("==") || currentToken.spelling.equals("!=")){
            return left;
        }
        String op = currentToken.spelling;
        currentToken = scanner.scan();
        Expr right = parseAddExpr();
        if(op.equals("<")){
            return new BinaryCompLtExpr(pos,left,right);
        }
        if(op.equals(">")){
            return new BinaryCompGtExpr(pos,left,right);
        }
        if(op.equals("<=")){
            return new BinaryCompLeqExpr(pos,left,right);
        }
        return new BinaryCompGeqExpr(pos,left,right);
    }


    // <AddExpr>::＝ <MultExpr> <MoreMultExpr>
    // <MoreMultExpr> ::= EMPTY | + <MultExpr> <MoreMultExpr> | - <MultExpr> <MoreMultExpr>
    private Expr parseAddExpr() {
        int pos = currentToken.position;
        Expr left = parseMultExpr();
        String op = currentToken.spelling;
        if(currentToken.kind != PLUSMINUS){
            return left;
        }
        currentToken = scanner.scan();
        Expr right =  parseAddExpr();
        if(op.equals("+")){
            return new BinaryArithPlusExpr(pos,left,right);
        }
        return new BinaryArithMinusExpr(pos,left,right);
    }


    // <MultiExpr> ::= <NewCastOrUnary> <MoreNCU>
    // <MoreNCU> ::= * <NewCastOrUnary> <MoreNCU> |
    //               / <NewCastOrUnary> <MoreNCU> |
    //               % <NewCastOrUnary> <MoreNCU> |
    //               EMPTY
    private Expr parseMultExpr() {
        int pos = currentToken.position;
        Expr left = parseNewCastOrUnary();
        String op = currentToken.spelling;
        if(currentToken.kind != MULDIV){
            return left;
        }
        currentToken = scanner.scan();
        Expr right =  parseMultExpr();
        if(op.equals("*")){
            return new BinaryArithTimesExpr(pos,left,right);
        }
        if(op.equals("/")){
            return new BinaryArithDivideExpr(pos,left,right);
        }
        return new BinaryArithModulusExpr(pos,left,right);
    }

    // <NewCastOrUnary> ::= <NewExpression> | <CastExpression> | <UnaryPrefix>
    private Expr parseNewCastOrUnary() {
        if(currentToken.kind == NEW){
            return parseNew();
        }
        if(currentToken.kind == CAST){
            return parseCast();
        }
        return parseUnaryPrefix();
    }


    // <NewExpression> ::= NEW <Identifier> ( )
    private Expr parseNew() {
        int pos = currentToken.position;
        currentToken = scanner.scan();
        String name = parseIdentifier();
        if(currentToken.kind != LPAREN){
            errorHandler.register(Error.Kind.PARSE_ERROR, "Expecting (");
            throw new CompilationException(errorHandler);
        }
        currentToken = scanner.scan();
        if(currentToken.kind != RPAREN){
            errorHandler.register(Error.Kind.PARSE_ERROR, "Expecting )");
            throw new CompilationException(errorHandler);
        }
        currentToken = scanner.scan();
        return new NewExpr(pos, name);
    }


    // <CastExpression> ::= CAST ( <Type> , <Expression> )
    private Expr parseCast() {
        int pos = currentToken.position;
        currentToken = scanner.scan();
        if(currentToken.kind != LPAREN){
            errorHandler.register(Error.Kind.PARSE_ERROR, "Expecting (");
            throw new CompilationException(errorHandler);
        }
        currentToken = scanner.scan();
        String name = parseType();
        if(currentToken.kind != COMMA){
            errorHandler.register(Error.Kind.PARSE_ERROR, "Expecting ,");
            throw new CompilationException(errorHandler);
        }
        currentToken = scanner.scan();
        Expr expr = parseExpression();
        if(currentToken.kind != RPAREN){
            errorHandler.register(Error.Kind.PARSE_ERROR, "Expecting )");
            throw new CompilationException(errorHandler);
        }
        currentToken = scanner.scan();
        return new CastExpr(pos, name, expr);
    }


    // <UnaryPrefix> ::= <PrefixOp> <UnaryPreFix> | <UnaryPostfix>
    // <PrefixOp> ::= - | ! | ++ | --
    private Expr parseUnaryPrefix() {
        int pos = currentToken.position;
        String sp = currentToken.spelling;
        Expr expr = null;
        if(sp.equals("-")){
            currentToken = scanner.scan();
            expr = parseUnaryPrefix();
            return new UnaryNegExpr(pos, expr);
        }
        if(sp.equals("!")){
            currentToken = scanner.scan();
            expr = parseUnaryPrefix();
            return new UnaryNotExpr(pos, expr);
        }
        if(sp.equals("++")){
            currentToken = scanner.scan();
            expr = parseUnaryPrefix();
            return new UnaryIncrExpr(pos, expr, false);
        }
        if(sp.equals("--")){
            currentToken = scanner.scan();
            expr = parseUnaryPrefix();
            return new UnaryDecrExpr(pos, expr, false);
        }
        return parseUnaryPostfix();
    }


    // <UnaryPostfix> ::= <Primary> <PostfixOp>
    // <PostfixOp> ::= ++ | -- | EMPTY
    private Expr parseUnaryPostfix() {
        int pos = currentToken.position;
        Expr expr = parsePrimary();
        if(currentToken.spelling.equals("++")){
            currentToken = scanner.scan();
            return new UnaryIncrExpr(pos, expr, true);
        }
        if(currentToken.spelling.equals("--")){
            currentToken = scanner.scan();
            return new UnaryDecrExpr(pos, expr, true);
        }
        return expr;
    }


    // <Primary> ::= ( <Expression> ) | <IntegerConst> | <BooleanConst> |
    //                              <StringConst> | <VarExpr>
    // <VarExpr> ::= <VarExprPrefix> <Identifier> <VarExprSuffix>
    // <VarExprPrefix> ::= SUPER . | THIS . | EMPTY
    // <VarExprSuffix> ::= ( <Arguments> ) | EMPTY
    private Expr parsePrimary() {
        int pos = currentToken.position;
        if(currentToken.kind == LPAREN){
            currentToken = scanner.scan();
            Expr expr = parseExpression();
            if(currentToken.kind != RPAREN){
                errorHandler.register(Error.Kind.PARSE_ERROR, "Missing ) after expression");
                throw new CompilationException(errorHandler);
            }
            currentToken = scanner.scan();
            return expr;
        }
        if(currentToken.kind == INTCONST){
            String intCont = currentToken.getSpelling();
            currentToken = scanner.scan();
            return new ConstIntExpr(pos, intCont);
        }
        if(currentToken.kind == BOOLEAN){
            String boolVal = currentToken.getSpelling();
            currentToken = scanner.scan();
            return new ConstBooleanExpr(pos, boolVal);
        }
        if(currentToken.kind == STRCONST){
            String strConst = currentToken.getSpelling();
            currentToken = scanner.scan();
            return new ConstStringExpr(pos, strConst);
        }
        Expr ref = null;
        if(currentToken.spelling.equals("super") || currentToken.spelling.equals("this")){
            ref = new VarExpr(pos, null, currentToken.spelling);
            currentToken = scanner.scan();
            if(currentToken.kind != DOT){
                errorHandler.register(Error.Kind.PARSE_ERROR, "Expecting . after reference");
                throw new CompilationException(errorHandler);
            }
            currentToken = scanner.scan();
        }
        String name = parseIdentifier();
        if(currentToken.kind != LPAREN){
            return new VarExpr(pos, ref, name);
        }
        currentToken = scanner.scan();
        ExprList args = parseArguments();
        if(currentToken.kind != RPAREN){
            errorHandler.register(Error.Kind.PARSE_ERROR, "Expecting ) after arg list");
            throw new CompilationException(errorHandler);
        }
        currentToken = scanner.scan();
        return new DispatchExpr(pos, ref, name, args);
    }


    // <Arguments> ::= EMPTY | <Expression> <MoreArgs>
    // <MoreArgs>  ::= EMPTY | , <Expression> <MoreArgs>
    private ExprList parseArguments() {
        ExprList exprList = new ExprList(currentToken.position);
        if(currentToken.kind != RPAREN){
            exprList.addElement(parseExpression());
            while(currentToken.kind == COMMA){
                currentToken = scanner.scan();
                exprList.addElement(parseExpression());
            }
        }
        return exprList;
    }


    // <Parameters> ::=  EMPTY | <Formal> <MoreFormals>
    // <MoreFormals> ::= EMPTY | , <Formal> <MoreFormals
    private FormalList parseParameters() {
        FormalList formalList = new FormalList(currentToken.position);

        if(currentToken.kind != RPAREN){
            formalList.addElement(parseFormal());
            while(currentToken.kind == COMMA){
                currentToken = scanner.scan();
                formalList.addElement(parseFormal());
            }
        }
        return formalList;
    }


    // <Formal> ::= <Type> <Identifier>
    private Formal parseFormal() {
        int pos = currentToken.position;
        String type = parseType();
        String name = parseIdentifier();
        return new Formal(pos, type, name);
    }


    // <Type> ::= <Identifier>
    private String parseType() {
        return parseIdentifier();
    }


    //----------------------------------------
    //Terminals

    private String parseOperator() {
        return currentToken.spelling;
    }


    private String parseIdentifier() {
        if(currentToken.kind != IDENTIFIER){
            errorHandler.register(Error.Kind.PARSE_ERROR, "Expecting Identifier, found " + currentToken.kind);
            throw new CompilationException(errorHandler);
        }
        String name = currentToken.spelling;
        currentToken = scanner.scan();
        return name;
    }


    private ConstStringExpr parseStringConst() {
        //...save the currentToken's string to a local variable...
        //...advance to the next token...
        //...return a new ConstStringExpr containing the string...
        String currentString = currentToken.spelling;
        int currentPos = currentToken.position;
        currentToken = scanner.scan();
        return new ConstStringExpr(currentPos, currentString);
    }


    private ConstIntExpr parseIntConst() {
        return new ConstIntExpr(currentToken.position, currentToken.spelling);
    }

    private ConstBooleanExpr parseBoolean() {
        return new ConstBooleanExpr(currentToken.position, currentToken.spelling);
    }

    /**
     * The main method to run test files through the parser and print
     * status to the console.
     * @param args the files to test on.
     */
    public static void main(String[] args){
        String[] files;
        if(args.length < 1){
            files = new String[1];
            files[0] = ("src/proj8EnglishHillisonQian/bantam/parser/ParserTestEnglishHillisonQian.btm");
        } else {
            files = args;
        }

        ErrorHandler errorHandler = new ErrorHandler();

        for(String file: files){
            System.out.println("Running file: " + file);
            Program program;
            try{
                Parser parser = new Parser(errorHandler);
                 program = parser.parse(file);

                if(errorHandler.errorsFound()){
                    System.out.println("Error Tokens Found:\n" + errorHandler.getErrorList());
                    continue;
                }
            }
            catch (CompilationException e){
                System.out.println("Compilation error: " + e.getErrorHandler().
                        getErrorList());
                continue;
            }
            errorHandler.clear();
            System.out.println("Scan and Parse Successful");
            Drawer drawer = new Drawer();
            drawer.draw(file, program);
        }
    }

}

