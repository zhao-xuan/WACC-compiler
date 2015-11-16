import static antlr.WaccParser.*;

import antlr.WaccParser;
import antlr.WaccParserBaseVisitor;
import org.antlr.v4.runtime.misc.NotNull;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

public class WaccVisitor extends WaccParserBaseVisitor<WaccType> {

    private SymbolTable st = new SymbolTable();
    private WaccVisitorErrorHandler errorHandler = new WaccVisitorErrorHandler();

    public boolean verbose = true;

    //////////////// UTILITY METHODS //////////////
    /*
     * Converts generic object into a WaccType if possible
     */
    private WaccType objToType(Object o) {
        if(o instanceof Integer) return new WaccType((Integer) o);
        if(o instanceof WaccType) return (WaccType) o;
        return WaccType.INVALID;
    }

    /*
     * Checks that two types are equal
     * Parameters can be ParseTrees, Integers or WaccTypes
     */
    private boolean typesMatch(Object lhs, Object rhs) {
        WaccType typel = objToType(lhs);
        WaccType typer = objToType(rhs);
        return typel.isValid() && typer.isValid() && (typel.isAll() || typer.isAll() ||  typel.equals(typer))   ;
    }

    //////////////// OVERRIDE METHODS //////////////

    @Override
    public WaccType visitProg(ProgContext ctx) {
        outputln("====");
        outputln("Visited main program entry");
        visitChildren(ctx);
        outputln("====");
        return null;
    }

    @Override
    public WaccType visitFunc(FuncContext ctx) {

        WaccType expectedType = visit(ctx.type());

        visit(ctx.paramList());

        WaccType returnType = visitStat(ctx.stat());

        if(!typesMatch(expectedType, returnType)) {
            errorHandler.typeMismatch(ctx, expectedType, returnType);
        }

        st.addFunction(ctx.ident().getText(), expectedType);
        return expectedType;
    }

    @Override
    public WaccType visitParamList(ParamListContext ctx) {
        String funcName = ((FuncContext) ctx.getParent()).ident().getText();

        for(int i = 0; i < ctx.getChildCount(); i += 2) {
            ParamContext param = (ParamContext) ctx.getChild(i);
            String paramName = param.ident().getText();
            WaccType paramType = visit(param);
            st.addParamToFunction(funcName, paramName, paramType);
        }
        return null;
    }

    @Override
    public WaccType visitParam(ParamContext ctx) {
        return new WaccType(ctx.type());
    }

    @Override
    public WaccType visitStat(StatContext ctx) {
        return visitChildren(ctx);
    }

    @Override
    public WaccType visitVarDeclaration(VarDeclarationContext ctx) {
        WaccType expected = visit(ctx.type());
        WaccType actual = visit(ctx.assignRhs());

        if(!typesMatch(expected, actual)) {
            errorHandler.typeMismatch(ctx, expected, actual);
        }

        boolean success = st.addVariable(ctx.ident().getText(), actual);
        if(!success) {
            errorHandler.variableRedeclaration(ctx, ctx.ident().getText());
        }
        return null;
    }

    @Override
    public WaccType visitVarAssignment(VarAssignmentContext ctx) {
        WaccType expected = visit(ctx.assignLhs());
        WaccType actual = visit(ctx.assignRhs());

        if(!typesMatch(expected, actual)) {
            errorHandler.typeMismatch(ctx, expected, actual);
        }
        return null;
    }

    @Override
    public WaccType visitReadStat(ReadStatContext ctx) {
        WaccType assignment = visit(ctx.assignLhs());

        if(!typesMatch(CHAR, assignment) && !typesMatch(INT, assignment)) {
            errorHandler.typeMismatch(ctx, new WaccType(CHAR), new WaccType(INT), assignment);
        }
        return null;
    }

    @Override
    public WaccType visitFreeStat(FreeStatContext ctx) {
        WaccType exprType = visit(ctx.expr());
        if(!typesMatch(exprType, PAIR)) {
            errorHandler.freeTypeMismatch(ctx.expr(), exprType);
        }
        return null;
    }

    @Override
    public WaccType visitReturnStat(ReturnStatContext ctx) {
        return visit(ctx.expr());
    }

    @Override
    public WaccType visitExitStat(ExitStatContext ctx) {
        WaccType exprType = visit(ctx.expr());
        if (!typesMatch(INT, exprType)) {
            errorHandler.typeMismatch(ctx.expr(), new WaccType(INT), exprType);
        }
        return null;
    }

    @Override
    public WaccType visitPrintStat(PrintStatContext ctx) {
        if(!visit(ctx.expr()).isValid()) {
            errorHandler.unprintableType(ctx, ctx.expr().getText());
        }
        return null;
    }

    @Override
    public WaccType visitPrintlnStat(PrintlnStatContext ctx) {
        if(!visit(ctx.expr()).isValid()) {
            errorHandler.unprintableType(ctx, ctx.expr().getText());
        }
        return null;
    }

    @Override
    public WaccType visitIfStat(IfStatContext ctx) {
        WaccType conditional = visit(ctx.expr());
        if(!typesMatch(BOOL, conditional)) {
            errorHandler.typeMismatch(ctx, new WaccType(BOOL), conditional);
        }
        st.newScope();
        visit(ctx.stat(0));
        st.endScope();
        st.newScope();
        visit(ctx.stat(1));
        st.endScope();
        return null;
    }

    @Override
    public WaccType visitWhileStat(WhileStatContext ctx) {
        WaccType conditional = visit(ctx.expr());
        if(!typesMatch(BOOL, conditional)){
            errorHandler.typeMismatch(ctx, new WaccType(BOOL), conditional);
        }
        st.newScope();
        visit(ctx.stat());
        st.endScope();
        return null;
    }

    @Override
    public WaccType visitScopeStat(ScopeStatContext ctx) {
        st.newScope();
        visit(ctx.stat());
        st.endScope();
        return null;
    }

    @Override
    public WaccType visitAssignLhs(AssignLhsContext ctx) {
        if(ctx.ident() != null) {
            return visit(ctx.ident());
        } else if(ctx.arrayElem() != null) {
            return visit(ctx.arrayElem());
        } else if(ctx.pairElem() != null) {
            return visit(ctx.pairElem());
        }
        return null;
    }

    @Override
    public WaccType visitAssignRhs(AssignRhsContext ctx) {
        return visitChildren(ctx);
    }

    @Override
    public WaccType visitNewPair(NewPairContext ctx) {
        WaccType fst = visit(ctx.expr(0));
        WaccType snd = visit(ctx.expr(1));
        WaccType pair = new WaccType(fst.getId(), snd.getId());
        pair.setFstArray(fst.isArray());
        pair.setSndArray(snd.isArray());
        return pair;
    }

    @Override
    public WaccType visitFuncCall(FuncCallContext ctx) {
        visit(ctx.argList());
        return st.lookupFunctionType(ctx.ident().getText());
    }

    @Override
    public WaccType visitArgList(ArgListContext ctx) {
        String ident = ((FuncCallContext) ctx.getParent()).ident().getText();

        // check that number of args passed is correct
        if(ctx.expr().size() != st.getNumParams(ident)) {
            errorHandler.invalidNumberOfArgs(ctx, ident);
        }

        // check each param matches type
        for (int i = 0; i < ctx.expr().size(); i++) {
            WaccType argType = visit(ctx.expr(i));
            WaccType paramType = st.getFunctionParamType(ident, i);
            if(!typesMatch(argType, paramType)) {
                errorHandler.typeMismatch(ctx.expr(i), paramType, argType);
            }
        }
        return null;
    }

    @Override
    public WaccType visitPairElem(PairElemContext ctx) {
        WaccType exprType = visit(ctx.expr());
        WaccType newType;
        if(ctx.FST() != null) {
            newType = new WaccType(exprType.getFstId());
            newType.setFstArray(exprType.isFstArray());
        } else {
            newType = new WaccType(exprType.getSndId());
            newType.setFstArray(exprType.isSndArray());
        }
        return newType;
    }

    @Override
    public WaccType visitType(TypeContext ctx) {
        WaccType base = visit(ctx.getChild(0));

        // case of arrays
        if(ctx.type() != null) {
            base.setArray(true);
        }

        return base;
    }

    @Override
    public WaccType visitBaseType(BaseTypeContext ctx) {
        return new WaccType(((TerminalNode) ctx.getChild(0)).getSymbol().getType());
    }

    @Override
    public WaccType visitPairType(PairTypeContext ctx) {
        WaccType fst = visit(ctx.pairElemType(0));
        WaccType snd = visit(ctx.pairElemType(1));
        WaccType pair = new WaccType(fst.getId(), snd.getId());
        pair.setFstArray(fst.isArray());
        pair.setSndArray(snd.isArray());
        return pair;
    }

    @Override
    public WaccType visitPairElemType(PairElemTypeContext ctx) {
        if(ctx.PAIR() != null) {
            return WaccType.PAIR;
        }

        WaccType base = visit(ctx.getChild(0));

        // case of arrays
        if(ctx.type() != null) {
            base.setArray(true);
        }

        return base;
    }

    @Override
    public WaccType visitExpr(ExprContext ctx) {
        if(ctx.INT_LIT() != null) return new WaccType(INT);
        if(ctx.BOOL_LIT() != null) return new WaccType(BOOL);
        if(ctx.CHAR_LIT() != null) return new WaccType(CHAR);
        if(ctx.STRING_LIT() != null) return new WaccType(STRING);
        if(ctx.pairLiter() != null) return visit(ctx.pairLiter());
        if(ctx.ident() != null) return visit(ctx.ident());
        if(ctx.arrayElem() != null) return visit(ctx.arrayElem());
        if(ctx.unaryOper() != null) visit(ctx.unaryOper());
        if(ctx.otherBinaryOper() != null) visit(ctx.otherBinaryOper());
        if(ctx.boolBinaryOper() != null) visit(ctx.boolBinaryOper());
        if(ctx.OPEN_PARENTHESES() != null) visit(ctx.expr(0));
        return null;
    }

    @Override
    public WaccType visitUnaryOper(UnaryOperContext ctx) {
        WaccType exprType = visit(((ExprContext) ctx.getParent()).expr(0));
        int op = ((TerminalNode) ctx.getChild(0)).getSymbol().getType();

        if(op == NOT) {
            if(!typesMatch(BOOL, exprType)) {
                errorHandler.typeMismatch(ctx, new WaccType(BOOL), exprType);
            }
            return new WaccType(BOOL);
        }
        if(op == MINUS) {
            if(!typesMatch(INT, exprType)) {
                errorHandler.typeMismatch(ctx, new WaccType(INT), exprType);
            }
            return new WaccType(INT);
        }
        if(op == LEN) {
            if(!exprType.isArray()) {
                errorHandler.typeMismatch(ctx, new WaccType(WaccType.ALL_ID).toArray(), exprType);
            }
            return new WaccType(INT);
        }
        if(op == ORD) {
            if(!typesMatch(CHAR, exprType)) {
                errorHandler.typeMismatch(ctx, new WaccType(CHAR), exprType);
            }
            return new WaccType(INT);
        }
        if(op == CHR) {
            if(!typesMatch(INT, exprType)) {
                errorHandler.typeMismatch(ctx, new WaccType(INT), exprType);
            }
            return new WaccType(CHR);
        }
        return null;
    }

    @Override
    public WaccType visitBoolBinaryOper(BoolBinaryOperContext ctx) {
        ExprContext parentCtx = (ExprContext) ctx.getParent();
        WaccType t1 = visit(parentCtx.expr(0));
        WaccType t2 = visit(parentCtx.expr(1));
        if(!typesMatch(BOOL, t1)) {
            errorHandler.typeMismatch(parentCtx.expr(0), new WaccType(BOOL), t1);
        }
        if(!typesMatch(BOOL, t2)) {
            errorHandler.typeMismatch(parentCtx.expr(1), new WaccType(BOOL), t2);
        }
        return new WaccType(BOOL);
    }

    @Override
    public WaccType visitOtherBinaryOper(OtherBinaryOperContext ctx) {
        ExprContext parentCtx = (ExprContext) ctx.getParent();
        WaccType t1 = visit(parentCtx.expr(0));
        WaccType t2 = visit(parentCtx.expr(1));
        int op = ((TerminalNode) ctx.getChild(0)).getSymbol().getType();
        WaccType opType = WaccType.fromBinaryOperator(op);

        if(op == EQ || op == NOT_EQ) return new WaccType(BOOL);

        if(t1.isPair() || t1.isArray()) {
            errorHandler.invalidOperator(parentCtx, tokenNames[op]);
        };

        if(op == MULT || op == DIV || op == MOD || op == PLUS || op == MINUS) {
            if(!typesMatch(INT, t1)) {
                errorHandler.typeMismatch(parentCtx.expr(0), new WaccType(INT), t1);
            }
            if(!typesMatch(INT, t2)) {
                errorHandler.typeMismatch(parentCtx.expr(1), new WaccType(INT), t2);
            }
            return new WaccType(INT);
        }

        if(op == GREATER_THAN || op == GREATER_THAN_EQ || op == LESS_THAN || op == LESS_THAN_EQ) {
            if(!typesMatch(t1, t2)) {
                errorHandler.typeMismatch(parentCtx.expr(0), t1, t2);
            }
            return new WaccType(BOOL);
        }
        return null;
    }

    @Override
    public WaccType visitIdent(IdentContext ctx) {
        String ident = ctx.getText();

        if(st.isFunction(ident)) {
            errorHandler.assignmentToFunction(ctx, ident);
        }

        if(!st.isDeclared(ident)) {
            errorHandler.symbolNotFound(ctx, ident);
        }

        return st.lookupType(ident);
    }

    @Override
    public WaccType visitArrayElem(ArrayElemContext ctx) {
        String ident = ctx.ident().getText();

        WaccType baseType = visit(ctx.ident());

        int[] arrayLengths = st.getArrayLength(ident);

        // Check that each index expression is integer
        for(ExprContext expr : ctx.expr()) {
            WaccType exprType = visit(expr);
            if(!typesMatch(INT, exprType)) {
                errorHandler.typeMismatch(expr, new WaccType(INT), exprType);
            }
        }

        return baseType.toArray();
    }

    @Override
    public WaccType visitArrayLiter(ArrayLiterContext ctx) {
        if(ctx.expr().size() != 0) {
            WaccType arrayType = visit(ctx.expr(0));
            for(int i = 1; i < ctx.expr().size(); i++) {
                WaccType nextType = visit(ctx.expr(i));
                if(!typesMatch(arrayType, nextType)) {
                    errorHandler.incompatibleArrayElemTypes(ctx.expr(i));
                }
            }
            return arrayType;
        }
        return WaccType.ALL;
    }

    ////////// OUTPUT FUNCTIONS ////////////

    public void output(String s) {
        if(verbose) System.out.print(s);
    }

    public void outputln(String s) {
        if(verbose) System.out.println(s);
    }
}
