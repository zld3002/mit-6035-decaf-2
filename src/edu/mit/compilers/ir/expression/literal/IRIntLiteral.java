package edu.mit.compilers.ir.expression.literal;

import java.util.ArrayList;
import java.util.List;

import edu.mit.compilers.ir.IRNode;
import edu.mit.compilers.ir.IRType;
import edu.mit.compilers.ir.expression.IRExpression;

public class IRIntLiteral extends IRLiteral<Integer> {

	public IRIntLiteral(Integer value) {
		super(value);
		expressionType = IRExpression.ExpressionType.INT_LITERAL;
	}

	@Override
	public IRType.Type getType() {
		return IRType.Type.INT;
	}

	@Override
	public List<? extends IRNode> getChildren() {
		return new ArrayList<IRNode>();
	}
}
