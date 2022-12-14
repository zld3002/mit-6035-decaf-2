package edu.mit.compilers.ir.statement;

import java.util.Arrays;
import java.util.List;

import antlr.Token;
import antlr.CommonToken;

import edu.mit.compilers.grammar.DecafParserTokenTypes;
import edu.mit.compilers.ir.IRNode;
import edu.mit.compilers.ir.expression.IRExpression;
import edu.mit.compilers.ir.expression.IRVariableExpression;
import edu.mit.compilers.trees.ConcreteTree;

public class IRAssignStatement extends IRStatement {

	private IRVariableExpression varAssigned;
	private String operator;
	private IRExpression value; // NOTE null if operator is ++ or --

	public IRAssignStatement(IRVariableExpression varAssigned, Token operator, IRExpression value) {
		this.varAssigned = varAssigned;
		this.operator = operator.getText();
		this.value = value;
        statementType = IRStatement.StatementType.ASSIGN_EXPR;
	}

	public IRAssignStatement(IRVariableExpression varAssigned, String operator, IRExpression value) {
		this.varAssigned = varAssigned;
		this.operator = operator;
		this.value = value;
        statementType = IRStatement.StatementType.ASSIGN_EXPR;
	}

	public IRAssignStatement(IRVariableExpression varAssigned, IRExpression value) {
		this.varAssigned = varAssigned;
		this.operator = "=";
		this.value = value;
		statementType = IRStatement.StatementType.ASSIGN_EXPR;
	}

	public String getVariableName(){
		return varAssigned.getName();
	}
	public IRVariableExpression getVarAssigned() { return varAssigned; }
	public String getOperator() { return operator; }
	public IRExpression getValue() { return value; }

	//@Override
	public List<? extends IRNode> getChildren() {
		return Arrays.asList(varAssigned, value);
	}

	@Override
	public String toString() {
		return varAssigned + " " + operator + (value == null ? "" : " " + value);
	}

	@Override
	public <R> R accept(IRStatementVisitor<R> visitor) {
		return visitor.on(this);
	}

	@Override
	public <R> R accept(IRNodeVisitor<R> visitor) {
		return visitor.on(this);
	}

}
