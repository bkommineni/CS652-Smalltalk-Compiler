package smalltalk.compiler;

import org.antlr.symtab.*;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.stringtemplate.v4.ST;
import smalltalk.compiler.symbols.*;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

import static smalltalk.compiler.DefineSymbols.getTextValues;
import static smalltalk.compiler.misc.Utils.shortToBytes;

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
		int blockindex =0;
		currentScope = ctx.scope;
		currentClassScope = ctx.classScope;
		pushScope(ctx.scope);
		Code code = Code.None;
		if(currentScope != null)
		{
			STMethod stMethod = ctx.scope;

			STCompiledBlock block = new STCompiledBlock(currentClassScope, (STBlock) currentScope);
			block.blocks = new STCompiledBlock[stMethod.getAllNestedScopedSymbols().size()];
			code = aggregateResult(code, visitChildren(ctx));
			for (Scope symbol : stMethod.getAllNestedScopedSymbols()) {
				STCompiledBlock stCompiledBlock = new STCompiledBlock(currentClassScope, (STBlock) symbol);
				stCompiledBlock.bytecode = ((STBlock) symbol).compiledBlock.bytecode;
				block.blocks[blockindex] = stCompiledBlock;
				blockindex++;
			}
			ctx.scope.compiledBlock = block;
			popScope();
			code = aggregateResult(code, Compiler.pop());
			code = aggregateResult(code, Compiler.push_self());
			code = aggregateResult(code, Compiler.method_return());
			ctx.scope.compiledBlock.bytecode = code.bytes();
		}
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
		Code code = defaultResult();
		int statSize = ctx.stat().size();
		int i=0;
		for(SmalltalkParser.StatContext stat : ctx.stat())
		{
			i++;
			code = aggregateResult(code,visit(stat));
			if(i!=statSize)
				code = aggregateResult(code,Compiler.pop());
		}
		if((currentScope instanceof STMethod))
		{
			if(!currentScope.getName().equals("main"))
				code = aggregateResult(code, Compiler.pop());
		}
		return code;
	}

	@Override
	public Code visitNamedMethod(SmalltalkParser.NamedMethodContext ctx) {
		currentScope = ctx.scope;
		Code code = Code.None;
		int blockindex = 0;
		STMethod stMethod = ctx.scope;
		STCompiledBlock block = new STCompiledBlock(currentClassScope, (STBlock) currentScope);
		block.blocks = new STCompiledBlock[stMethod.getAllNestedScopedSymbols().size()];
		code = aggregateResult(code,visit(ctx.methodBlock()));
		for (Scope symbol : stMethod.getAllNestedScopedSymbols())
		{
			STCompiledBlock stCompiledBlock = new STCompiledBlock(currentClassScope, (STBlock) symbol);
			stCompiledBlock.bytecode = ((STBlock) symbol).compiledBlock.bytecode;
			block.blocks[blockindex] = stCompiledBlock;
			blockindex++;
		}

		ctx.scope.compiledBlock = block;
		ctx.scope.compiledBlock.bytecode = code.bytes();
		popScope();
		return Code.None;
	}

	@Override
	public Code visitOperatorMethod(SmalltalkParser.OperatorMethodContext ctx) {

		currentScope = ctx.scope;
		Code code = Code.None;
		int blockindex = 0;
		STMethod stMethod = ctx.scope;
		STCompiledBlock block = new STCompiledBlock(currentClassScope, (STBlock) currentScope);
		block.blocks = new STCompiledBlock[stMethod.getAllNestedScopedSymbols().size()];
		code = aggregateResult(code,visit(ctx.methodBlock()));
		for (Scope symbol : stMethod.getAllNestedScopedSymbols())
		{
			STCompiledBlock stCompiledBlock = new STCompiledBlock(currentClassScope, (STBlock) symbol);
			stCompiledBlock.bytecode = ((STBlock) symbol).compiledBlock.bytecode;
			block.blocks[blockindex] = stCompiledBlock;
			blockindex++;
		}

		ctx.scope.compiledBlock = block;
		ctx.scope.compiledBlock.bytecode = code.bytes();
		popScope();
		return Code.None;
	}

	@Override
	public Code visitKeywordMethod(SmalltalkParser.KeywordMethodContext ctx) {
		Code code = Code.None;
		currentScope = ctx.scope;
		int blockindex = 0;
		STMethod stMethod = ctx.scope;
		STCompiledBlock block = new STCompiledBlock(currentClassScope, (STBlock) currentScope);
		block.blocks = new STCompiledBlock[stMethod.getAllNestedScopedSymbols().size()];
		code = aggregateResult(code,visit(ctx.methodBlock()));

		for (Scope symbol : stMethod.getAllNestedScopedSymbols())
		{
			STCompiledBlock stCompiledBlock = new STCompiledBlock(currentClassScope, (STBlock) symbol);
			stCompiledBlock.bytecode = ((STBlock) symbol).compiledBlock.bytecode;
			block.blocks[blockindex] = stCompiledBlock;
			blockindex++;
		}

		ctx.scope.compiledBlock = block;
		ctx.scope.compiledBlock.bytecode = code.bytes();
		popScope();
		return code;
	}

	@Override
	public Code visitUnaryMsgSend(SmalltalkParser.UnaryMsgSendContext ctx) {
		Code code = Code.None;
		code = aggregateResult(code,visit(ctx.unaryExpression()));
		code = aggregateResult(code,Compiler.send(0,currentClassScope.stringTable.add(ctx.ID().getText())));
		return code;
	}

	@Override
	public Code visitUnarySuperMsgSend(SmalltalkParser.UnarySuperMsgSendContext ctx) {
		Code code = Code.None;
		code = aggregateResult(code,Compiler.push_self());
		code = aggregateResult(code,Compiler.send_super(0,currentClassScope.stringTable.add(ctx.ID().getText())));
		return code;
	}

	@Override
	public Code visitSmalltalkMethodBlock(SmalltalkParser.SmalltalkMethodBlockContext ctx) {
		Code code = visit(ctx.body());
		code = aggregateResult(code, Compiler.push_self());
		code = aggregateResult(code, Compiler.method_return());
		return code;
	}

	@Override
	public Code visitPrimitiveMethodBlock(SmalltalkParser.PrimitiveMethodBlockContext ctx)
	{
		return Code.None;
	}

	@Override
	public Code visitAssign(SmalltalkParser.AssignContext ctx)
	{
		Code right = visit(ctx.messageExpression());
		Code left = visit(ctx.lvalue());
		Code code = aggregateResult(right,left);
		return code;
	}

	@Override
	public Code visitLvalue(SmalltalkParser.LvalueContext ctx) {
		Code code = Code.None;
		STBlock stBlock = (STBlock) currentScope;
		if(ctx.sym instanceof STField)
			code = Compiler.store_field(ctx.sym.getInsertionOrderNumber());
		else
		{
			int relScopeCount  = stBlock.getRelativeScopeCount(ctx.sym.getName());
			int localIndex = stBlock.getLocalIndex(ctx.sym.getName());
			code = Compiler.store_local(relScopeCount,localIndex);
		}
		return code;
	}

	@Override
	public Code visitEmptyBody(SmalltalkParser.EmptyBodyContext ctx) {
		Code code = Code.None;
		if(currentClassScope.getName().equals("MainClass"))
		{
			code = Compiler.push_nil();
		}
		return code;
	}

	@Override
	public Code visitReturn(SmalltalkParser.ReturnContext ctx)
	{
		Code code  = visit(ctx.messageExpression());
		code = aggregateResult(code,Compiler.method_return());
		return code;
	}

	@Override
	public Code visitKeywordSend(SmalltalkParser.KeywordSendContext ctx) {
		Code code = visit(ctx.recv);
		//code = aggregateResult(code,visit(ctx.binaryExpression(1)));
		for(SmalltalkParser.BinaryExpressionContext arg : ctx.args)
		{
			code = aggregateResult(code,visit(arg));
		}
		code = sendKeywordMsg(ctx.recv,code,ctx.args,ctx.KEYWORD());
		return code;
	}

	/*@Override
	public Code visitBinaryExpression(SmalltalkParser.BinaryExpressionContext ctx) {
		//Code code = visit(ctx.unaryExpression(0));
		Code code = Code.None;
		if(ctx.unaryExpression().size() > 0)
		{
			for (int i = 1; i < ctx.unaryExpression().size(); i++)
			{
				code = aggregateResult(code, visit(ctx.unaryExpression(i)));
				int index = currentClassScope.stringTable.add(ctx.bop(i - 1).getText());
				code = aggregateResult(code,Compiler.send(1,index));
			}
		}

		return code;
	}*/

	@Override
	public Code visitBop(SmalltalkParser.BopContext ctx) {
		Code code = Code.None;
		int index = currentClassScope.stringTable.add(ctx.getText());
		code = aggregateResult(code,Compiler.send(1,index));
		return code;
	}

	@Override
	public Code visitUnaryIsPrimary(SmalltalkParser.UnaryIsPrimaryContext ctx) {
		Code code = visit(ctx.primary());
		return code;
	}

	@Override
	public Code visitId(SmalltalkParser.IdContext ctx) {
		Code code = Code.None;
		int index = 0;

		if(ctx.sym instanceof STField)
		{
			code = Compiler.push_field(ctx.sym.getInsertionOrderNumber());
		}
		else if((ctx.sym instanceof STVariable) || (ctx.sym instanceof STArg))
		{
			STBlock stBlock = (STBlock) currentScope;
			index = stBlock.getLocalIndex(ctx.ID().getText());
			int relScopeCount = stBlock.getRelativeScopeCount(ctx.ID().getText());
			code = Compiler.push_local(relScopeCount, index);
		}
		else
		{
			index = currentClassScope.stringTable.add(ctx.ID().getText());
			code = Compiler.push_global(index);
		}
		return code;
	}

	@Override
	public Code visitLiteral(SmalltalkParser.LiteralContext ctx) {
		Code code = Code.None;
		if(ctx.NUMBER() != null)
		{
			if(ctx.NUMBER().getText().contains("."))
				code = Compiler.push_float(Float.parseFloat(ctx.NUMBER().getText()));
			else
				code = Compiler.push_int(Integer.parseInt(ctx.NUMBER().getText()));
		}
		else
		{
			if (!(ctx.getText().equals("nil") ||
					ctx.getText().equals("self") ||
					ctx.getText().equals("true") ||
					ctx.getText().equals("false")))
			{
				String str = ctx.getText();
				if (str.contains("\'")) {
					str = str.replace("\'", "");
				}
				int literalIndex = getLiteralIndex(str);
				code = Compiler.push_literal(literalIndex);
			}
			else
			{
				switch (ctx.getText())
				{
					case "nil":
						code = Compiler.push_nil();
						break;
					case "self":
						code = Compiler.push_self();
						break;
					case "true":
						code = Compiler.push_true();
						break;
					case "false":
						code = Compiler.push_false();
						break;
				}
			}
		}
		return code;
	}

	@Override
	public Code visitBlock(SmalltalkParser.BlockContext ctx) {
		currentScope = ctx.scope;
		STBlock stBlock = (STBlock)currentScope;
		Code blockd = Compiler.block(stBlock.index);
		Code code = visit(ctx.body());
		code = aggregateResult(code,Compiler.block_return());
		ctx.scope.compiledBlock = new STCompiledBlock(currentClassScope,(STBlock) currentScope);
		ctx.scope.compiledBlock.bytecode = code.bytes();
		popScope();
		return blockd;
	}

	@Override
	public Code visitPassThrough(SmalltalkParser.PassThroughContext ctx) {
		Code code = Code.None;
		//code = aggregateResult(code,visit(ctx.recv));
		code = aggregateResult(code,visit(ctx.binaryExpression().unaryExpression(0)));
		if(ctx.binaryExpression().unaryExpression().size() > 0)
		{
			for (int i = 1; i < ctx.binaryExpression().unaryExpression().size(); i++)
			{
				code = aggregateResult(code, visit(ctx.binaryExpression().unaryExpression(i)));
				int index = currentClassScope.stringTable.add(ctx.binaryExpression().bop(i - 1).getText());
				code = aggregateResult(code,Compiler.send(1,index));
			}
		}
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
		int index  = currentClassScope.stringTable.add(s);
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
		StringBuffer buffer = new StringBuffer();
		for (int i=0;i<keywords.size();i++)
		{
			buffer.append(keywords.get(i).getText());
		}
		Code e = Compiler.send(args.size(),currentClassScope.stringTable.add(buffer.toString()));
		code = aggregateResult(code,e);
		return code;
	}

	public String getProgramSourceForSubtree(ParserRuleContext ctx) {
		return null;
	}
}
