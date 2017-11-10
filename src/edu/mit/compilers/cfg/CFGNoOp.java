package edu.mit.compilers.cfg;
import edu.mit.compilers.cfg.CFGLine;
import java.util.Set;

public class CFGNoOp extends CFGLine {

    public CFGNoOp() {
        super();
    }

    public CFGNoOp(CFGLine trueBranch, CFGLine falseBranch) {
        super(trueBranch, falseBranch);
    }

    @Override
    public boolean isNoOp() { return true; }

    @Override
    public String ownValue() {
        return "NOOP";
    }

    @Override
    public <R> R accept(CFGVisitor<R> visitor){
		return visitor.on(this);
	}
}
