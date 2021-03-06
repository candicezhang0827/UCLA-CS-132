package context;

import syntaxtree.*;
import visitor.GJVoidDepthFirst;

import java.util.Map;

/**
 * ContextVisitor runs the first pass through the JTB generated Abstract
 * Syntax Tree. It builds the ContextTable from a global perspective.
 * For speed of development, we throw runtime exceptions for type errors. We only
 * need to go through nodes that are storeable as MJClasses/MJMethods/MJTypes. Thus
 * we can ignore all statements in this visitor. Distinction is checked for here.
 * In all instances, n represents the Node we are at in the AST and argu is our
 * global context table. We return MJType for being able to pass around expression types.
 */
public class ContextVisitor extends GJVoidDepthFirst<MJClass> {

    public ContextTable context = new ContextTable();


    /**
     * f0 -> MainClass()
     * f1 -> ( TypeDeclaration() )*
     * f2 -> <EOF>
     *
     * @param n
     * @param argu
     */
    @Override
    public void visit(Goal n, MJClass argu) {
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
    }

    /**
     * f0 -> "class"
     * f1 -> Identifier()
     * f2 -> "{"
     * f3 -> "public"
     * f4 -> "static"
     * f5 -> "void"
     * f6 -> "main"
     * f7 -> "("
     * f8 -> "String"
     * f9 -> "["
     * f10 -> "]"
     * f11 -> Identifier()
     * f12 -> ")"
     * f13 -> "{"
     * f14 -> ( VarDeclaration() )*
     * f15 -> ( Statement() )*
     * f16 -> "}"
     * f17 -> "}"
     *
     * @param n
     * @param argu
     */
    @Override
    public void visit(MainClass n, MJClass argu) {
        String className = n.f1.f0.toString();
        // Create a main class
        MJClass mainClass = new MJClass(className);
        mainClass.isMain = true;
        // Redundant but could be used further down the line if need be
        MJType mainParam = new MJType(n.f11.f0.toString(), MJType.Type.OTHER);
        MJMethod mainMethod = new MJMethod("main", new MJType(null, MJType.Type.OTHER));
        mainMethod.params.add(mainParam);
        mainClass.addMethod(mainMethod);
        context.addClass(mainClass);
        // Add local variables to main method for main class
        for(Node node : n.f14.nodes) {
            node.accept(this, mainClass);
        }
    }

    /**
     * f0 -> ClassDeclaration()
     * | ClassExtendsDeclaration()
     *
     * @param n
     * @param argu
     */
    @Override
    public void visit(TypeDeclaration n, MJClass argu) {
        n.f0.accept(this, argu);
    }

    /**
     * f0 -> "class"
     * f1 -> Identifier()
     * f2 -> "{"
     * f3 -> ( VarDeclaration() )*
     * f4 -> ( MethodDeclaration() )*
     * f5 -> "}"
     *
     * @param n
     * @param argu
     */
    @Override
    public void visit(ClassDeclaration n, MJClass argu) throws MJTypeCheckException {
        String className = n.f1.f0.toString();
        if (context.getClass(className) != null
                && !context.getClass(className).preInitialize) {
            throw new MJTypeCheckException("Duplicate class names");
        }
        MJClass newClass = new MJClass(className);
        // If its been initialized in the past
        if (context.getClass(className) != null &&
                context.getClass(className).preInitialize) {
            // Get the child
            for(Map.Entry<String, MJClass> entry: context.classes.entrySet()) {
                // Reset parent to new reference
                if(entry.getValue().hasParent() &&
                        entry.getValue().getParent().getClassName().equals(className)) {
                    context.classes.get(entry.getKey()).setParent(newClass);
                    break;
                }
            }
        }
        context.addClass(newClass);
        // Add fields to class
        for(Node node : n.f3.nodes) {
            node.accept(this, newClass);
        }
        // Add methods to class
        for(Node node : n.f4.nodes) {
            node.accept(this, newClass);
        }

    }

    /**
     * f0 -> "class"
     * f1 -> Identifier()
     * f2 -> "extends"
     * f3 -> Identifier()
     * f4 -> "{"
     * f5 -> ( VarDeclaration() )*
     * f6 -> ( MethodDeclaration() )*
     * f7 -> "}"
     *
     * @param n
     * @param argu
     */
    @Override
    public void visit(ClassExtendsDeclaration n, MJClass argu) throws MJTypeCheckException {
        String className = n.f1.f0.toString();
        MJClass childClass = new MJClass(className);
        String baseClassName = n.f3.f0.toString();
        MJClass baseClass = context.getClass(baseClassName);
        // Dummy parent
        if (baseClass == null) {
            baseClass = new MJClass(baseClassName);
            baseClass.preInitialize = true;
        }
        childClass.setParent(baseClass);
        // Start building inheritance link
        context.addClass(baseClass);
        context.addClass(childClass);
        // Add fields to class
        for(Node node : n.f5.nodes) {
            node.accept(this, childClass);
        }
        // Add methods to class
        for(Node node : n.f6.nodes) {
            node.accept(this, childClass);
        }
    }

    /**
     * f0 -> Type()
     * f1 -> Identifier()
     * f2 -> ";"
     *
     * @param n
     * @param argu
     */
    @Override
    public void visit(VarDeclaration n, MJClass argu) throws MJTypeCheckException {
        MJType.Type type = MJType.Type.fromInteger(n.f0.f0.which);
        MJType variable = new MJType(n.f1.f0.toString(), type);
        // Cast needed to force to identifier - this is safe as it is guaranteed
        if (type == MJType.Type.IDENT)
            variable.setSubtype(((Identifier)n.f0.f0.choice).f0.toString());
        // No methods --> Still looking through fields
        if(!argu.hasMethods()) {
            if(!argu.fields.add(variable)) {
                throw new MJTypeCheckException("Duplicate field name");
            }
        } else {
            if(!argu.getMRUMethod().vars.add(variable)) {
                throw new MJTypeCheckException("Duplicate local variable");
            }
        }
    }

    /**
     * f0 -> "public"
     * f1 -> Type()
     * f2 -> Identifier()
     * f3 -> "("
     * f4 -> ( FormalParameterList() )?
     * f5 -> ")"
     * f6 -> "{"
     * f7 -> ( VarDeclaration() )*
     * f8 -> ( Statement() )*
     * f9 -> "return"
     * f10 -> Expression()
     * f11 -> ";"
     * f12 -> "}"
     *
     * @param n
     * @param argu
     */
    @Override
    public void visit(MethodDeclaration n, MJClass argu) throws MJTypeCheckException {
        String methodName = n.f2.f0.toString();
        MJType returnType = new MJType(null, MJType.Type.fromInteger(n.f1.f0.which));
        // Cast needed to force to identifier - this is safe as it is guaranteed
        if (returnType.getType() == MJType.Type.IDENT)
            returnType.setSubtype(((Identifier)n.f1.f0.choice).f0.toString());
        MJMethod method = new MJMethod(methodName, returnType);
        // Check for overloading
        if(argu.getClassMethod(methodName) != null) {
            if (!argu.hasParent()) {
                throw new MJTypeCheckException("Found overloading through duplicate method declaration");
            } else {
                if(argu.getParent().getClassMethod(methodName) == null)
                    throw new MJTypeCheckException("Found overloading through duplicate method declaration");
            }
        }
            argu.addMethod(method);
            // Add parameters to method
            n.f4.accept(this, argu);
            // Add variables to most recently used method
            for(Node node : n.f7.nodes) {
                node.accept(this, argu);
            }
        
    }

    /**
     * f0 -> FormalParameter()
     * f1 -> ( FormalParameterRest() )*
     *
     * @param n
     * @param argu
     */
    @Override
    public void visit(FormalParameterList n, MJClass argu) {
        n.f0.accept(this, argu);
        for(Node node : n.f1.nodes) {
            node.accept(this, argu);
        }
    }

    /**
     * f0 -> Type()
     * f1 -> Identifier()
     *
     * @param n
     * @param argu
     */
    @Override
    public void visit(FormalParameter n, MJClass argu) throws MJTypeCheckException {
        MJType.Type type = MJType.Type.fromInteger(n.f0.f0.which);
        MJType param = new MJType(n.f1.f0.toString(), type);
        // Cast needed to force to identifier - this is safe as it is guaranteed
        if (type == MJType.Type.IDENT)
            param.setSubtype(((Identifier)n.f0.f0.choice).f0.toString());
        if(!argu.getMRUMethod().params.add(param)) {
            throw new MJTypeCheckException("Duplicate parameter");
        }
    }

    /**
     * f0 -> ","
     * f1 -> FormalParameter()
     *
     * @param n
     * @param argu
     */
    @Override
    public void visit(FormalParameterRest n, MJClass argu) {
        n.f1.accept(this, argu);
    }
}
