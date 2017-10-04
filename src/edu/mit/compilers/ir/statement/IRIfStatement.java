package edu.mit.compilers.ir.statement;

import java.util.Arrays;
import java.util.List;

import edu.mit.compilers.ir.IRNode;
import edu.mit.compilers.ir.expression.IRExpression;
import edu.mit.compilers.trees.ConcreteTree;

public class IRIfStatement extends IRStatement {

	private IRExpression ifCondition;
	private IRBlock thenBlock;
	private IRBlock elseBlock;

	public IRIfStatement(ConcreteTree tree) {
		statementType = IRStatement.StatementType.IF_BLOCK;
		ConcreteTree child = tree.getFirstChild();
		ifCondition = IRExpression.makeIRExpression(child);
		child = child.getRightSibling();
		thenBlock = new IRBlock(child);
		child = child.getRightSibling();
		if (child == null) {
			elseBlock = null;
		} else {
			elseBlock = new IRBlock(child);
		}
	}

	@Override
	String toString(int indent) {
		String whitespace = "";
		for (int i = 0; i < indent; ++i) {
			whitespace += "  ";
		}
		String answer = whitespace + "if " // TODO + expr
						+ thenBlock.toString(indent + 1) + "\n";
		if (elseBlock != null) {
			answer += whitespace + "else" + elseBlock.toString(indent + 1) + "\n";
		}
		return answer;
	}


	//@Override
	public List<? extends IRNode> getChildren() {
		return Arrays.asList(ifCondition, thenBlock, elseBlock);
	}

}
