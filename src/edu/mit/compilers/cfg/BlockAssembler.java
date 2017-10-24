package edu.mit.compilers.cfg;

import java.io.OutputStream;
import java.io.*;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

import antlr.Token;
import edu.mit.compilers.ir.*;
import edu.mit.compilers.ir.decl.*;
import edu.mit.compilers.ir.expression.*;
import edu.mit.compilers.ir.expression.literal.*;
import edu.mit.compilers.ir.operator.*;
import edu.mit.compilers.ir.statement.*;
import edu.mit.compilers.symbol_tables.*;
import edu.mit.compilers.trees.EnvStack;
import edu.mit.compilers.cfg.*;


public class BlockAssembler {

    String methodLabel;
    int blockCount;
    int stringCount;
    int numAllocs;
    Map<CFGBlock, String> blockLabels;
    Map<String, String> stringLabels;
    CFGEnvStack envStack;

    public BlockAssembler(String label, int numParams) {
        this.methodLabel = label;
        this.blockCount = 0;
        this.stringCount = 0;
        this.numAllocs = numParams;  // will increment this as we add locals
        this.blockLabels = new HashMap<>();
        this.stringLabels = new HashMap<>();
        this.envStack = new CFGEnvStack();
    }

    public String makeCode(CFGBlock block, VariableTable parameters) {
        envStack.pushEnvironment(CFGEnv.EnvType.BLOCK);
        String prefix = methodLabel + ":\n";
        String code = "";

        if (parameters != null && parameters.getVariableDescriptorList() != null){ // check for nonexistence of parameters in imports
            for (VariableDescriptor v : parameters.getVariableDescriptorList()) {
                // TODO mov input params to stack
                if (v.getDecl().getLength() > 0) {
                    // TODO arrays, allocate v.getLength, wait how do we do this???
                    //this.numAllocs += (v.getDecl().getLength() - 1);
                    //addVariableToStack(v);
                }
                else {
                    addVariableToStack(v);
                }
            }
        }

        code += makeCodeHelper(block);

        for (String stringLiteral : stringLabels.keySet()) {
            String label = stringLabels.get(stringLiteral);
            code += label + ":\n";
            code += ".string " + stringLiteral + "\n\n";
        }

        String allocSpace = new Integer(8*numAllocs).toString();
        code += "\n"+ methodLabel + "_end:\n";
        code += "leave\n" + "ret\n";
        prefix += "enter $" + allocSpace + ", $0\n";
        envStack.removeEnvironment();
        if (envStack.getSize() != 0){
            throw new RuntimeException("envStack should push and pop equal numbers of things.");
        }
        return prefix + code;
    }

    private String makeCodeHelper(CFGBlock block) {
        if (blockLabels.containsKey(block)) {
            throw new RuntimeException("Making code for a block that already has code generated.");
        }
        blockCount += 1;
        String label = "."+methodLabel+"_"+new Integer(blockCount).toString();
        blockLabels.put(block, label);
        String code = "\n" + label + ":\n";
        for (CFGLine line : block.getLines()) {
            code += makeCodeLine(line);
        }

        // add code for true child
        String childCode = "";
        CFGBlock child = (CFGBlock)block.getTrueBranch();
        if (child != null) {
            if (!blockLabels.containsKey(child)) {
                childCode += makeCodeHelper(child);
            }
            else if (block.isBranch()) {
                code += "mov $1, %r11\n";
                code += "cmp %r10, %r11\n";
                code += "je " + blockLabels.get(child) + "\n";
            }
            else {
                code += "jmp " + blockLabels.get(child) + "\n";
            }
        }
        else {
            //null child means we want to jump to the end of method where we return
            code += "jmp " + methodLabel + "_end\n";
        }
        if (block.isBranch()) {
            //  add code for false child, and jump statement
            child = (CFGBlock)block.getFalseBranch();
            if (child != null) {
                if (!blockLabels.containsKey(child)) {
                    childCode += makeCodeHelper(child);
                }
                // jump to it
                code += "mov $0, %r11\n";
                code += "cmp %r10, %r11\n";
                code += "je " + blockLabels.get(child) + "\n";
            }
            else {
                throw new RuntimeException("null false child of block when true child is not null.");
            }
        }
        return code + childCode;
    }

    private String makeCodeLine(CFGLine line) {
        /*
        CFGExpression: compute result of expression and store in %r10 so it can be used to jump/etc. next
        CFGStatement: compute result and store in given variable
        CFGDecl: increment numAllocs???
        CFGBlock: RuntimeException
        CFGNoOp: empty code
        CFGMethodDecl: call methodName???
        */
        if (line.getEnvType() != null){
            envStack.pushEnvironment(line.getEnvType());
            // System.out.println("Adding env: " + line.getEnvType() + ": " + line.ownValue());
        }

        String code = "";

        try {
            if (line instanceof CFGNoOp) {
                code += "";
            }
            else if (line instanceof CFGDecl) {
                code += makeCodeCFGDecl((CFGDecl)line);
            }
            else if (line instanceof CFGExpression) {
                code += makeCodeCFGExpression((CFGExpression)line);
            }
            else if (line instanceof CFGMethodDecl) {
                code += makeCodeCFGMethodDecl((CFGMethodDecl)line);
            }
            else if (line instanceof CFGStatement) {
                code += makeCodeCFGStatement((CFGStatement)line);
            }
            else {
                throw new RuntimeException("CFGLine of unaccepted type.");
            }
        }
        catch (RuntimeException e) {
            // for printing niceness, show things for which we haven't yet implemented codegen
            code = line.ownValue() + "\n";
        }

        int numEnvsToRemove = line.getNumEnvsEnded();
        while (numEnvsToRemove > 0) {
            // System.out.println("Removing envs (" + new Integer(numEnvsToRemove).toString() + ")" + ": " + line.ownValue());
            envStack.removeEnvironment();
            numEnvsToRemove -= 1;
        }

        return code;
    }

    private String makeCodeCFGDecl(CFGDecl line) {
        // allocate a space on stack for the declared variable, update the total number of allocations
        numAllocs += 1;
        IRMemberDecl vDecl = line.getDecl();
        VariableDescriptor v = new VariableDescriptor(vDecl);
        addVariableToStack(v);
        return "";
    }

    private String makeCodeCFGExpression(CFGExpression line) {
        return makeCodeIRExpression(line.getExpression());
    }

    private String makeCodeCFGMethodDecl(CFGMethodDecl line) {
        // TODO
        throw new RuntimeException("Unimplemented");
    }

    private String makeCodeCFGStatement(CFGStatement line) {
        IRStatement statement = line.getStatement();
        String code = "";
        switch(statement.getStatementType()) {
            case IF_BLOCK: case FOR_BLOCK: case WHILE_BLOCK: {
                throw new RuntimeException("if/for/while statements should have been destructed.");
            }
            case METHOD_CALL: {
                IRMethodCallExpression methodCall = ((IRMethodCallStatement)statement).getMethodCall();
                code += makeCodeIRExpression(methodCall);
                return code;
            }
            case RETURN_EXPR: {
                IRExpression returnExpr = ((IRReturnStatement) statement).getReturnExpr();
                code += makeCodeIRExpression(returnExpr);  // return value now in %r10
                code += "mov %r10, %rax\n";
                code += "leave\n";
                code += "ret\n";
                return code;
            }
            case ASSIGN_EXPR: {
                return makeCodeIRAssignStatement((IRAssignStatement)statement);
            }
            case BREAK: {
                // TODO
            }
            case CONTINUE: {
                // TODO
            } default: {
                throw new RuntimeException("destructIR error: UNSPECIFIED statement");
            }
        }
    }

    private String makeCodeIRAssignStatement(IRAssignStatement s) {
        String code = "";
        IRVariableExpression varAssigned = s.getVarAssigned();
        String operator = s.getOperator();
        IRExpression value = s.getValue();
        if (value != null){
            code += makeCodeIRExpression(value);  // value now in %r10
        }
        String stackLocation = getVariableStackLocation(varAssigned);
        switch (operator) {
            case "=":
                code += "mov %r10, " + stackLocation + "\n";
                return code;
            case "+=":
                code += "mov " + stackLocation +", %r11\n";
                code += "add %r10, %r11\n";
                code += "mov %r11, " + stackLocation + "\n";
                return code;
            case "-=":
                code += "mov " + stackLocation +", %r11\n";
                code += "sub %r10, %r11\n";
                code += "mov %r11, " + stackLocation + "\n";
                return code;
            case "++":
                code += "mov " + stackLocation +", %r11\n";
                code += "add $1, %r11\n";
                code += "mov %r11, " + stackLocation + "\n";
                return code;
            case "--":
                code += "mov " + stackLocation +", %r11\n";
                code += "sub $1, %r11\n";
                code += "mov %r11, " + stackLocation + "\n";
                return code;
            default:
                throw new RuntimeException("Operator must be an assignment of some kind.");
        }
    }

    // return the code to evaluate the expression and store its result in %r10
    private String makeCodeIRExpression(IRExpression expr) {
        //TODO
        String code = "";
        switch (expr.getExpressionType()) {
            case INT_LITERAL:
                String valueAsStr = ((IRIntLiteral)expr).toString();
                return "mov $" + valueAsStr + ", %r10\n";
            case BOOL_LITERAL:
                Boolean booleanValue = ((IRBoolLiteral)expr).getValue();
                return (booleanValue ? "mov $1, %r10" : "mov $0, %r10\n");
            case STRING_LITERAL:
                String stringValue = ((IRStringLiteral)expr).toString();
                stringCount += 1;
                String label = "."+methodLabel+"_string_"+new Integer(stringCount).toString();
                stringLabels.put(stringValue, label);
                return "mov $" + label + ", %r10\n";
            case METHOD_CALL:
                IRMethodCallExpression methodCall = (IRMethodCallExpression)expr;
                List<IRExpression> arguments = methodCall.getArguments();
                for (int i=arguments.size()-1; i>=0; i--) {
                    IRExpression arg = arguments.get(i);
                    code += makeCodeIRExpression(arg);
                    code += "push %r10\n";
                }
                code += "call " + methodCall.getName() + "\n";
                return code;
            case VARIABLE:
                String stackLoc = getVariableStackLocation((IRVariableExpression)expr);
                return "mov " + stackLoc + ", %r10\n";
            case LEN:
                String arg = ((IRLenExpression)expr).getArgument();
                Integer lenValue = new Integer(0);
                // ^ TODO lookup variable and put the len here
                return "mov $" + lenValue.toString() + ", %r10\n";
            case UNARY:
                String op = ((IRUnaryOpExpression)expr).getOperator().getText();
                IRExpression argExpr = ((IRUnaryOpExpression)expr).getArgument();
                code += makeCodeIRExpression(argExpr); // value in %r10
                if (op.equals("!")){
                    code += "mov $1 %r11\n";
                    code += "sub %r10 %r11\n";
                    code += "mov %r11 %r10\n"; // TODO how do you ACTUALLY do ! ?
                }
                else { // "-"
                    code += "mov $0 %r11\n";
                    code += "sub %r10 %r11\n";
                    code += "mov %r11 %r10\n"; // TODO is there a better way to do - ?
                }
                return code;
            case TERNARY:
                throw new RuntimeException("Ternary operations should have been deconstructed by CFGCreator.");
            case BINARY:
                // TODO
            default:
                return "<CODE FOR EXPRESSION " + expr.toString() + ">\n";
                //throw new RuntimeException("Unspecified expression type");
        }
        //return "<CODE FOR EXPRESSION " + expr.toString() + ">\n";
    }

    private void addVariableToStack(VariableDescriptor var) {
        envStack.addVariable(var);
        return;
    }

    private String getVariableStackLocation(IRVariableExpression var) {
        // return "-8(%rbp)";
        int offset = envStack.getStackOffset(var.getName());
        return "-" + new Integer(offset).toString() + "(%rbp)";
        //throw new RuntimeException("Unimplemented");
    }
}
