 package edu.mit.compilers.cfg;

 import java.util.Map;
 import java.util.HashMap;
 import java.util.List;
 import java.util.ArrayList;
 import java.util.Arrays;

 import edu.mit.compilers.ir.decl.*;
 import edu.mit.compilers.ir.expression.*;
 import edu.mit.compilers.ir.expression.literal.*;
 import edu.mit.compilers.ir.statement.*;
 import edu.mit.compilers.symbol_tables.*;
 import edu.mit.compilers.cfg.lines.*;

 public class BlockAssembler {

     String methodLabel;
     int blockCount;
     int stringCount;
     int numAllocs;
     int ternCount = 0;
     TypeDescriptor returnType;
     Map<CFGBlock, String> blockLabels;
     Map<String, String> stringLabels;
     VariableTable universalVariableTable;

     public BlockAssembler(String label, int numParams, VariableTable globalVars, TypeDescriptor returnType) {
         this.methodLabel = label;
         this.blockCount = 0;
         this.stringCount = 0;
         this.numAllocs = numParams;  // will increment this as we add locals
         this.blockLabels = new HashMap<>();
         this.stringLabels = new HashMap<>();
         this.universalVariableTable = new VariableTable(globalVars);
         this.returnType = returnType;
     }

     public String makeCode(CFGBlock block, VariableTable parameters) {
         String prefix = methodLabel + ":\n";
         String code = "";

         if (parameters != null && parameters.getVariableDescriptorList() != null){ // check for nonexistence of parameters in imports
             for (VariableDescriptor v : parameters.getVariableDescriptorList()) {
                 if (v.getDecl().getLength() > 0) {
                     this.numAllocs += (v.getDecl().getLength() - 1);
                 }
                 addVariableToStack(v);
             }
         }

         code += makeCodeHelper(block);

         if (this.returnType != TypeDescriptor.VOID) {
             // if it doesn't have anywhere returning, have it jump to the runtime error
             code += "jmp .nonreturning_method\n";
         }

         // if it has void return, or if a return statement tells it to jump here, leave
         code += "\n"+ methodLabel + "_end:\n";
         if (methodLabel.equals("main")) { // makes sure exit code is 0
             code += "mov $0, %rax\n";
         }
         code += "leave\n" + "ret\n";

         // string literals
         for (String stringLiteral : stringLabels.keySet()) {
             String label = stringLabels.get(stringLiteral);
             code += "\n" + label + ":\n";
             code += ".string " + stringLiteral + "\n";
         }

         // figure out how many allocations we did
         String allocSpace = new Integer(8*numAllocs).toString();
         prefix += "enter $" + allocSpace + ", $0\n";

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
                 code += "cmp %r11, %r10\n";
                 code += "je " + blockLabels.get(child) + "\n";
             }
             else {
                 code += "jmp " + blockLabels.get(child) + "\n";
             }
         }
         else {
             if (this.returnType != TypeDescriptor.VOID) {
                 // if it doesn't have anywhere returning, have it jump to the runtime error
                 code += "jmp .nonreturning_method\n";
             }
             else {
                 //null child means we want to jump to the end of method where we return
                 code += "jmp " + methodLabel + "_end\n";
             }
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
                 code += "cmp %r11, %r10\n";
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

         String code = "";

         //try {
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
         //}
         // catch (RuntimeException e) {
         //     // for printing niceness, show things for which we haven't yet implemented codegen
         //     code = line.ownValue() + "\n";
         // }

         return code;
     }

     private String makeCodeCFGDecl(CFGDecl line) {
         // allocate a space on stack for the declared variable, update the total number of allocations
         int length = (line.getLength() == 0) ? 1 : line.getLength();
         numAllocs += length;
         IRMemberDecl vDecl = line.getDecl();
         VariableDescriptor v = new VariableDescriptor(vDecl);
         int offset = addVariableToStack(v);
         String code = "";
         for (int i=0; i<length; i++){
             String stackLocation = "-" + (new Integer(offset).toString()) + "(%rbp)";
             code += "mov $0, %r10\n";
             code += "mov %r10, " + stackLocation + "\n";
             offset -= 8;  // go through and initialize them all to 0 in reverse order
         }
         return code;
     }

     private String makeCodeCFGExpression(CFGExpression line) {
         return makeCodeIRExpression(line.getExpression());
     }

     private String makeCodeCFGMethodDecl(CFGMethodDecl line) {
         String code = "";
         List<IRMemberDecl> parameters = line.getMethodDecl().getParameters().getVariableList();
     	for(int i=0; i < parameters.size(); ++i) {
     		IRMemberDecl param = parameters.get(i);
     		String paramStackLoc = getVariableStackLocation(param.getName());
     		String paramLoc = getParamLoc(i);
     		if(i<=5) {
     			code += String.format("mov %s, %s\n", paramLoc, paramStackLoc);
     		} else {
     			code += String.format("mov %s, %%r10\n", paramLoc);
     			code += String.format("mov %%r10, %s\n", paramStackLoc);
     		}
     	}
     	return code;
     }

     private String getParamLoc(int i) {
     	if(i==0) {
     		return "%rdi";
     	} else if (i==1) {
     		return "%rsi";
     	} else if (i==2) {
     		return "%rdx";
     	} else if (i==3) {
     		return "%rcx";
     	} else if (i==4) {
     		return "%r8";
     	} else if (i==5) {
     		return "%r9";
     	} else {
     		return (i-4)*8 + "(%rbp)";
     	}
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
                 IRReturnStatement retStatement = (IRReturnStatement) statement;
                 if (!retStatement.isVoid()) { // TODO from mayars -- make sure this is correct in case of void code
                     IRExpression returnExpr = ((IRReturnStatement) statement).getReturnExpr();
                     code += makeCodeIRExpression(returnExpr);  // return value now in %r10
                     code += "mov %r10, %rax\n";
                 }
                 code += "jmp " + methodLabel + "_end\n"; // jump to end of method where we return
                 return code;
             }
             case ASSIGN_EXPR: {
                 return makeCodeIRAssignStatement((IRAssignStatement)statement);
             }
             case BREAK: {
                 //  addressed in the CFG instead of at assembly
                 return "";
             }
             case CONTINUE: {
                 return "";
             } default: {
                 throw new RuntimeException("destructIR error: UNSPECIFIED statement");
             }
         }
     }

     private String makeCodeIRAssignStatement(IRAssignStatement s) {
         String code = "";
         IRVariableExpression varAssigned = s.getVarAssigned();
         code += getCodeForIndexExpr(varAssigned);
         String stackLocation = getVariableStackLocation(varAssigned);
         code += "push %r10\n";
         String operator = s.getOperator();
         IRExpression value = s.getValue();
         if (value != null){
             code += makeCodeIRExpression(value);  // value now in %r10
             code += "mov %r10, %r11\n"; // now in %r11
         }
         code += "pop %r10\n";
         switch (operator) {
             case "=":
                 code += "mov %r11, " + stackLocation + "\n";
                 return code;
             case "+=":
                 code += "push %r10\n";
                 code += "mov " + stackLocation +", %r10\n";
                 code += "add %r11, %r10\n";
                 code += "mov %r10, %r11\n";
                 code += "pop %r10\n";
                 code += "mov %r11, " + stackLocation + "\n";
                 return code;
             case "-=":
                 code += "push %r10\n";
                 code += "mov " + stackLocation +", %r10\n";
                 code += "sub %r11, %r10\n";
                 code += "mov %r10, %r11\n";
                 code += "pop %r10\n";
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
         String code = "";
         switch (expr.getExpressionType()) {
             case INT_LITERAL:
                 String valueAsStr = ((IRIntLiteral)expr).toString();
                 return "mov $" + valueAsStr + ", %r10\n";
             case BOOL_LITERAL:
                 Boolean booleanValue = ((IRBoolLiteral)expr).getValue();
                 return (booleanValue ? "mov $1, %r10\n" : "mov $0, %r10\n");
             case STRING_LITERAL:
                 String stringValue = ((IRStringLiteral)expr).toString();
                 String label;
                 if (! stringLabels.containsKey(stringValue)) {
                     stringCount += 1;
                     label = "."+methodLabel+"_string_"+new Integer(stringCount).toString();
                     stringLabels.put(stringValue, label);
                 }
                 else {
                     label = stringLabels.get(stringValue);
                 }
                 return "mov $" + label + ", %r10\n";
             case METHOD_CALL:
                 IRMethodCallExpression methodCall = (IRMethodCallExpression)expr;
                 List<IRExpression> arguments = methodCall.getArguments();
                 List<String> registers = new ArrayList<>(Arrays.asList("%rdi", "%rsi", "%rdx", "%rcx", "%r8", "%r9"));
                 for (int i=arguments.size()-1; i>=6; i--) {
                     // if so many params we need stack pushes: iterate from size-1 down to 6 and push/pop them
                     IRExpression arg = arguments.get(i);
                     code += makeCodeIRExpression(arg);
                     code += "push %r10\n";
                 }
                 for (int i=0; i<arguments.size() && i < 6; i++) {
                     IRExpression arg = arguments.get(i);
                     code += makeCodeIRExpression(arg);
                     code += "mov %r10, " + registers.get(i) + "\n";
                 }
                 code += "mov $0, %rax\n";
                 code += "call " + methodCall.getName() + "\n";
                 for (int i=arguments.size()-1; i>=6; i--) {
                     code += "pop %r10\n";
                 }
 		code += "mov %rax, %r10\n";
                 return code;
             case VARIABLE:
                 IRVariableExpression varExpr = (IRVariableExpression)expr;
                 code += getCodeForIndexExpr(varExpr);
                 String stackLoc = getVariableStackLocation(varExpr);
                 code += "mov " + stackLoc + ", %r10\n";
                 return code;
             case LEN:
                 String arg = ((IRLenExpression)expr).getArgument();
                 VariableDescriptor var = universalVariableTable.get(arg);
                 Integer lenValue = new Integer(var.getLength());
                 return "mov $" + lenValue.toString() + ", %r10\n";
             case UNARY:
                 String op = ((IRUnaryOpExpression)expr).getOperator();
                 IRExpression argExpr = ((IRUnaryOpExpression)expr).getArgument();
                 code += makeCodeIRExpression(argExpr); // value in %r10
                 if (op.equals("!")){
                     code += "mov $1, %r11\n";
                     code += "sub %r10, %r11\n";
                     code += "mov %r11, %r10\n"; // TODO how do you ACTUALLY do ! ?
                 }
                 else { // "-"
                     code += "mov $0, %r11\n";
                     code += "sub %r10, %r11\n";
                     code += "mov %r11, %r10\n"; // TODO is there a better way to do - ?
                 }
                 return code;
             case TERNARY:
             	String toFalse = ".tern_" + ternCount +"_to_false\n";
             	String toEnd = ".tern_" + ternCount +"_to_end\n";
                 IRTernaryOpExpression tern = (IRTernaryOpExpression) expr;
                 IRExpression condition = tern.getCondition();
                 code += makeCodeIRExpression(condition); //bool for tern now in %r10
                 code += "cmp 0, %r10\n";
                 code += "je "+ toFalse;
                 IRExpression trueExpr = tern.getTrueExpression();
                 code += makeCodeIRExpression(trueExpr);
                 code += "jmp " + toEnd;
                 code += toFalse;
                 IRExpression falseExpr = tern.getFalseExpression();
                 code += makeCodeIRExpression(falseExpr);
                 code += toEnd;
                 return code;
             case BINARY:
                 return makeCodeIRBinaryOpExpression((IRBinaryOpExpression)expr);
             default:
                 return "<CODE FOR EXPRESSION " + expr.toString() + ">\n";
                 //throw new RuntimeException("Unspecified expression type");
         }
         //return "<CODE FOR EXPRESSION " + expr.toString() + ">\n";
     }

     private String makeCodeIRBinaryOpExpression(IRBinaryOpExpression expr){
         String op = expr.getOperator();
         IRExpression leftExpr = expr.getLeftExpr();
         IRExpression rightExpr = expr.getRightExpr();
         String code = "";
         code += makeCodeIRExpression(rightExpr); // right value in %r10
         code += "mov %r10, %r11\n"; // right value in %r11
         code += makeCodeIRExpression(leftExpr); // left value in %r10, right value in %r11
         switch (op) {
             case "+":
                 code += "add %r11, %r10\n";  // expression output value in %r10
                 return code;
             case "-":
                 code += "sub %r11, %r10\n";
                 return code;
             case "*":
                 code += "imul %r11, %r10\n";
                 return code;
             case "/":
                 code += "mov $0, %rdx\n";
                 code += "mov %r10, %rax\n";
                 code += "idiv %r11\n";
                 code += "mov %rax, %r10\n";
                 return code;
             case "%":
                 code += "mov $0, %rdx\n";
                 code += "mov %r10, %rax\n";
                 code += "idiv %r11\n";
                 code += "mov %rdx, %r10\n";
                 return code;
             case "&&":
                 code += "and %r11, %r10\n";
                 return code;
             case "||":
                 code += "or %r11, %r10\n";
                 return code;
             case "==":
                 code += "cmp %r11, %r10\n";
                 code += "mov $0, %r10\n";
                 code += "mov $1, %r11\n";
                 code += "cmove %r11, %r10\n";
                 return code;
             case "!=":
                 code += "cmp %r11, %r10\n";
                 code += "mov $0, %r10\n";
                 code += "mov $1, %r11\n";
                 code += "cmovne %r11, %r10\n";
                 return code;
             case "<":
                 code += "cmp %r11, %r10\n";
                 code += "mov $0, %r10\n";
                 code += "mov $1, %r11\n";
                 code += "cmovl %r11, %r10\n";
                 return code;
             case ">":
                 code += "cmp %r11, %r10\n";
                 code += "mov $0, %r10\n";
                 code += "mov $1, %r11\n";
                 code += "cmovg %r11, %r10\n";
                 return code;
             case "<=":
                 code += "cmp %r11, %r10\n";
                 code += "mov $0, %r10\n";
                 code += "mov $1, %r11\n";
                 code += "cmovle %r11, %r10\n";
                 return code;
             case ">=":
                 code += "cmp %r11, %r10\n";
                 code += "mov $0, %r10\n";
                 code += "mov $1, %r11\n";
                 code += "cmovge %r11, %r10\n";
                 return code;
             default:
                 throw new RuntimeException("unsupported operation in binary expression");
         }

     }

     private int addVariableToStack(VariableDescriptor var) {
         // System.out.println("Added variable: " + var.toString());
         return universalVariableTable.add(var);
     }

     // returns emptyset for non-array-expression var.
     // returns code for which r10 ends up with array index if var is an array expr.
     private String getCodeForIndexExpr(IRVariableExpression var) {
         String code = "";
         if (var.isArray()) {
             code += makeCodeIRExpression(var.getIndexExpression());
             // bounds checking
             code += "push %r11\n"; // TODO mayars is this push/pop necessary?
             int max_index = universalVariableTable.get(var.getName()).getLength();
             if (max_index <= 0) {
                 throw new RuntimeException("array cannot have nonpositive index at codegen step");
             }
             code += "mov $" + (new Integer(max_index).toString()) + ", %r11\n";
             code += "cmp %r11, %r10\n";
             code += "jge .out_of_bounds\n";
             code += "cmp $0, %r10\n";
             code += "jl .out_of_bounds\n";
             // being able to index into global variables is hard
             if (!universalVariableTable.containsInThisScope(var.getName())) {
                 // TODO mayars make sure this is correct
                 code += "mov $8, %r11\n";
                 code += "imul %r11, %r10\n"; // TODO do via shifting instead
                 code += "mov $" + var.getName() + ", %r11\n";
                 code += "add %r11, %r10\n";
             }
             code += "pop %r11\n";
         }
         return code;
     }

     // returns something like -8(rbp) or varname or -8(rbp, r10, 8), etc.
     private String getVariableStackLocation(IRVariableExpression var) {
         if (universalVariableTable.containsInThisScope(var.getName())) {
             int offset = universalVariableTable.getStackOffset(var.getName());
             if (var.isArray()) {
                 return "-" + (new Integer(offset).toString()) + "(%rbp, %r10, 8)";
             } else {
                 return "-" + (new Integer(offset).toString()) + "(%rbp)";
             }
         } else {
             if (var.isArray()) {
                 return "0(%r10)";
             } else {
                 return var.getName();
             }
         }
     }

     // NOTE: generally don't use this method --mayars
     private String getVariableStackLocation(String varName) {
         int offset = universalVariableTable.getStackOffset(varName);
         return "-" + new Integer(offset).toString() + "(%rbp)";
     }
 }
