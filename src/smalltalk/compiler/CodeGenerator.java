package smalltalk.compiler;

import org.antlr.symtab.ClassSymbol;
import org.antlr.symtab.Scope;
import org.antlr.symtab.Symbol;
import org.antlr.symtab.Utils;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;
import smalltalk.compiler.symbols.*;

import java.util.ArrayList;
import java.util.List;

/** Fill STBlock, STMethod objects in Symbol table with bytecode,
 * {@link STCompiledBlock}.
 */
public class CodeGenerator extends SmalltalkBaseVisitor<Code> {
	public static final boolean dumpCode = false;

	public STClass currentClassScope;
	public Scope currentScope;

	/** With which compiler are we generating code? */
	public final Compiler compiler;

	public CodeGenerator(Compiler compiler) {
		this.compiler = compiler;
	}

	/** This and defaultResult() critical to getting code to bubble up the
	 *  visitor call stack when we don't implement every method.
	 */
	@Override
	protected Code aggregateResult(Code aggregate, Code nextResult) {
		if ( aggregate!=Code.None ) {
			if ( nextResult!=Code.None ) {
				return aggregate.join(nextResult);
			}
			return aggregate;
		}
		else {
			return nextResult;
		}
	}

	@Override
	protected Code defaultResult() {
		return Code.None;
	}

	@Override
	public Code visitFile(SmalltalkParser.FileContext ctx) {
		currentScope = compiler.symtab.GLOBALS;
		visitChildren(ctx);
		return Code.None;
	}

	@Override
	public Code visitClassDef(SmalltalkParser.ClassDefContext ctx) {
		currentClassScope = ctx.scope;
		pushScope(ctx.scope);
		Code code = visitChildren(ctx);
		popScope();
		currentClassScope = null;
		return code;
	}

	public STCompiledBlock getCompiledPrimitive(STPrimitiveMethod primitive) {
		STCompiledBlock compiledMethod = new STCompiledBlock(currentClassScope, primitive);
		return compiledMethod;
	}

	@Override
	public Code visitMain(SmalltalkParser.MainContext ctx) {
		currentScope = ctx.scope;
		currentClassScope = ctx.classScope;
		pushScope(ctx.scope);
		Code code = visitChildren(ctx);
		STCompiledBlock block = new STCompiledBlock(currentClassScope,(STBlock) currentScope);
		ctx.scope.compiledBlock = block;
		popScope();
		code = aggregateResult(code,Compiler.pop());
		code = aggregateResult(code,Compiler.push_self());
		code = aggregateResult(code,Compiler.method_return());
		ctx.scope.compiledBlock.bytecode = code.bytes();
		return code;
	}

	/**
	 All expressions have values. Must pop each expression value off, except
	 last one, which is the block return value. Visit method for blocks will
	 issue block_return instruction. Visit method for method will issue
	 pop self return.  If last expression is ^expr, the block_return or
	 pop self return is dead code but it is always there as a failsafe.

	 localVars? expr ('.' expr)* '.'?
	 */
	@Override
	public Code visitFullBody(SmalltalkParser.FullBodyContext ctx)
	{
		// fill in
		Code code = defaultResult();
		for(SmalltalkParser.StatContext stat : ctx.stat())
		{
			code = aggregateResult(code,visit(stat));
		}
		return code;
	}

	@Override
	public Code visitReturn(SmalltalkParser.ReturnContext ctx)
	{
		Code e = visit(ctx.messageExpression());
		Code code = e.join(Compiler.method_return());
		return code;
	}

	@Override
	public Code visitKeywordSend(SmalltalkParser.KeywordSendContext ctx) {
		Code code = visit(ctx.recv);
		for(SmalltalkParser.BinaryExpressionContext arg : ctx.args)
		{
			code = aggregateResult(code,visit(arg));
		}
		code = sendKeywordMsg(ctx.recv,code,ctx.args,ctx.KEYWORD());
		return code;
	}

	@Override
	public Code visitBinaryExpression(SmalltalkParser.BinaryExpressionContext ctx) {
		Code code = visit(ctx.unaryExpression(0));
		return code;
	}

	@Override
	public Code visitUnaryIsPrimary(SmalltalkParser.UnaryIsPrimaryContext ctx) {
		Code code = visit(ctx.primary());
		return code;
	}

	@Override
	public Code visitId(SmalltalkParser.IdContext ctx) {
		int index = currentClassScope.stringTable.add(ctx.ID().getText());
		Code code = Compiler.push_global(index);
		return code;
	}

	@Override
	public Code visitLiteral(SmalltalkParser.LiteralContext ctx) {
		int literalIndex = getLiteralIndex(ctx.getText());
		Code code = Compiler.push_literal(literalIndex);
		return code;
	}

	@Override
	public Code visitBlock(SmalltalkParser.BlockContext ctx) {
		Code code = visit(ctx.body());
		return code;
	}

	public void pushScope(Scope scope)
	{
		currentScope = scope;
	}

	public void popScope()
	{
		currentScope = currentScope.getEnclosingScope();
	}

	public int getLiteralIndex(String s)
	{
		System.out.println(currentClassScope);
		int index  = 0;
		return index;
	}

	public Code store(String id)
	{
		return null;
	}

	public Code push(String id)
	{
		return null;
	}

	public Code sendKeywordMsg(ParserRuleContext receiver,
							   Code receiverCode,
							   List<SmalltalkParser.BinaryExpressionContext> args,
							   List<TerminalNode> keywords)
	{
		Code code = receiverCode;
		Code e = Compiler.send(args.size(),currentClassScope.stringTable.add(keywords.get(0).getText()));
		code = code.join(e);
		return code;
	}

	public String getProgramSourceForSubtree(ParserRuleContext ctx) {
		return null;
	}
}
