package edu.mit.compilers.ir.expression.literal;

import java.util.ArrayList;
import java.util.List;

import edu.mit.compilers.ir.IRNode;
import edu.mit.compilers.ir.IRType;
import edu.mit.compilers.ir.IRNode.IRNodeVisitor;
import edu.mit.compilers.ir.expression.IRExpression;

public class IRStringLiteral extends IRLiteral<String> {

	public IRStringLiteral(String value) {
		super(value);
		expressionType = IRExpression.ExpressionType.STRING_LITERAL;
	}

	@Override
	public IRType.Type getType() {
		return IRType.Type.STRING;
	}

	@Override
	public String toString() {
		return "\"" + value + "\"";
	}
	
	@Override
    public <R> R accept(IRNodeVisitor<R> visitor) {
        return visitor.onString(this);
    }
}
