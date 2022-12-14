package edu.mit.compilers.cfg;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import edu.mit.compilers.ir.expression.IRExpression;
import edu.mit.compilers.cfg.lines.CFGLine;

public class CFGBlock extends CFGLine {
    private List<CFGLine> lines;
    private CFGLine firstLine;
    private CFGLine lastLine;

    public CFGBlock(CFGBlock trueBranch, CFGBlock falseBranch) {
        super(trueBranch, falseBranch);
        this.lines = new ArrayList<CFGLine>();
    }

    public CFGBlock () {
        super();
        this.lines = new ArrayList<CFGLine>();
    }

    public List<CFGLine> getLines() { return lines; }

    // public void setTrueBranch(CFGBlock next) {
    //     this.trueBranch = next;
    //     // doesn't increment parent counter because CFGBlock just copies the underlying structure of lines
    // }
    //
    // public void setFalseBranch(CFGBlock next) {
    //     this.falseBranch = next;
    //     // doesn't increment parent counter because CFGBlock just copies the underlying structure of lines
    // }

    public void addLine(CFGLine l) {
        lines.add(l);
        if(firstLine == null) {
            firstLine = l;
        }
        lastLine = l;
        l.setCorrespondingBlock(this);
    }

    @Override
    public <R> R accept(CFGVisitor<R> visitor){
		return visitor.on(this);
	}

    @Override
    public CFGLine getTrueBranch() {
        return lastLine.getTrueBranch().getCorrespondingBlock();
    }

    @Override
    public CFGLine getFalseBranch() {
        return lastLine.getFalseBranch().getCorrespondingBlock();
    }

    @Override
    public CFGBlock getCorrespondingBlock() {
        return this;
    }

    @Override
    public List<CFGLine> getParents() {
        List<CFGLine> answer = new ArrayList<>();
        for(CFGLine line: firstLine.getParents()) {
            answer.add(line.getCorrespondingBlock());
        }

        return answer;
    }

    @Override
    public boolean isAssign() {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public CFGLine copy() {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public List<IRExpression> getExpressions() {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public boolean isEnd() {
        if (lastLine.getTrueBranch() == null) {
            if (lastLine.getFalseBranch() != null) {
                throw new RuntimeException("CFGLine has one null branch and one non-null branch.");
            }
            else {
                return true;
            }
        }
        else if (lastLine.getFalseBranch() == null) {
            throw new RuntimeException("CFGLine has one null branch and one non-null branch.");
        }
        return false;
    }

	@Override
	public boolean isBranch() {
		return lastLine.isBranch();
	}
}
