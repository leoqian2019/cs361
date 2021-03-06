package proj8EnglishHillisonQian.bantam.visitor;

import proj8EnglishHillisonQian.bantam.ast.*;

public class MainMethodFinder extends Visitor{
    private boolean hasMain = false;

    @Override
    public Object visit(Method methodNode){
        // if the method has a name "main"
        if(methodNode.getName().equals("main") &&
                // if the method has a return type "void"
                methodNode.getReturnType().equals("void") &&
                // if the method has no parameter
                methodNode.getFormalList().getSize() == 0) {
            hasMain = true;
        }
        return null;
    }

    @Override
    public Object visit(Field fieldNode){
        return null;
    }

    @Override
    public Object visit(Class_ classNode){
        // advance if the class has a name "Main"
        if(classNode.getName().equals("Main")){
            classNode.getMemberList().accept(this);
        }
        return null;
    }

    public boolean hasMain(ASTNode rootNode){
        rootNode.accept(this);
        return hasMain;
    }
}
