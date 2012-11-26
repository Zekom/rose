import org.eclipse.jdt.internal.compiler.batch.*;

// Test if this works in Java.
// import java; // fails
// import java.io; // fails
// import java.lang; // fails
// import java.lang.*; // works
// import java.*; // works

import java.io.*;
import java.text.*;
import java.util.*;

import org.eclipse.jdt.internal.compiler.ast.Annotation;
import org.eclipse.jdt.core.compiler.*;
import org.eclipse.jdt.core.compiler.batch.*;
import org.eclipse.jdt.internal.compiler.*;
import org.eclipse.jdt.internal.compiler.env.*;
import org.eclipse.jdt.internal.compiler.impl.*;
import org.eclipse.jdt.internal.compiler.ast.*;
import org.eclipse.jdt.internal.compiler.lookup.*;
import org.eclipse.jdt.internal.compiler.parser.*;
import org.eclipse.jdt.internal.compiler.problem.*;
import org.eclipse.jdt.internal.compiler.util.*;

// import org.eclipse.jdt.core.dom.ASTVisitor;

// DQ (10/30/2010): Added support for reflection to get methods in implicitly included objects.
import java.lang.reflect.*;

// DQ (8/13/2011): Used to support modifier handling.
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;

class ecjASTVisitor extends ExtendedASTVisitor {
    //
    // Keep track of how many anonymous types is associated with a top-level type declaration.
    //
    HashMap<String, Integer> anonymousClassCounter = new HashMap<String, Integer>();

    //
    // Keep track of how many local typedeclaration of a given name is associated with a top-level 
    // type declaration.
    //
    public HashMap<String, HashMap<String, Integer>> localClassCounter = new HashMap<String, HashMap<String, Integer>>();

    //
    // Keep track of the set of Catch block arguments.
    // Keep track of the set of Blocks that are Catch blocks.
    //
    HashSet<Argument> catchArguments = new HashSet<Argument>();
    HashSet<Block> catchBlocks = new HashSet<Block>();

    //
    // Keep track of Argument(s) that were already processed in a different context.
    //
    HashSet<TypeDeclaration> TypesWithNoBody = new HashSet<TypeDeclaration>();
    HashMap<ASTNode, TypeDeclaration> TypeHeaderDelimiters = new HashMap<ASTNode, TypeDeclaration>();
    HashSet<AbstractMethodDeclaration> MethodsWithNoBody = new HashSet<AbstractMethodDeclaration>();
    HashMap<ASTNode, AbstractMethodDeclaration> MethodHeaderDelimiters = new HashMap<ASTNode, AbstractMethodDeclaration>();

    JavaParserSupport javaParserSupport;

    // *************************************************
    // Support for source code position (from Vincent).
    // *************************************************
    public ecjASTVisitor(CompilationUnitDeclaration unit, JavaParserSupport java_parser_support) {
        this.javaParserSupport = java_parser_support;
    }

    // *************************************************
    public boolean preVisit(ASTNode node) {
        if (TypeHeaderDelimiters.containsKey(node)) {
            TypeDeclaration type = TypeHeaderDelimiters.get(node);
            if (javaParserSupport.verboseLevel > 1)
                System.out.println("    Side-visiting Type Declaration Header for " + type.getClass().getName());
            JavaParser.cactionTypeDeclarationHeader(type.superclass != null,
                                                    type.superInterfaces == null ? 0 : type.superInterfaces.length,
                                                    type.typeParameters == null  ? 0 : type.typeParameters.length,
                                                    javaParserSupport.createJavaToken(type));
        }
        else if (MethodHeaderDelimiters.containsKey(node)) {
            AbstractMethodDeclaration method = MethodHeaderDelimiters.get(node);
            if (method instanceof ConstructorDeclaration) {
                if (javaParserSupport.verboseLevel > 1)
                    System.out.println("    Side-visiting Constructor Declaration Header for " + method.getClass().getName());
                ConstructorDeclaration constructor = (ConstructorDeclaration) method;
                javaParserSupport.processConstructorDeclarationHeader(constructor, javaParserSupport.createJavaToken(method));
            }
            else {
                if (javaParserSupport.verboseLevel > 1)
                    System.out.println("    Side-visiting Method Declaration Header for " + method.getClass().getName());
                javaParserSupport.processMethodDeclarationHeader((MethodDeclaration) method, javaParserSupport.createJavaToken(method)); 
            }
        }

        if (javaParserSupport.verboseLevel > 1)
            System.out.println("Pre-visiting " + node.getClass().getName());

        //
        // Ignore default constructors.
        //
        if (node instanceof ConstructorDeclaration) { // a constructor declaration?
            ConstructorDeclaration constructor = (ConstructorDeclaration) node;
            if (constructor.isDefaultConstructor()) { // a default constructor?
                return false; // ignore it !
            }
        }

        return true;
    }

    public void postVisit(ASTNode node) {
        if (javaParserSupport.verboseLevel > 1)
            System.out.println("Post-visiting " + node.getClass().getName());

        //
        // If you need to do something at the end of a visit, do it here.
        //
        if (node instanceof Expression) {
            int paren_count = (node.bits & ASTNode.ParenthesizedMASK) >> ASTNode.ParenthesizedSHIFT;
            if (paren_count > 0) {
                JavaParser.cactionParenthesizedExpression(paren_count);
            }
        }

        return;
    }

    // *************************************************

    private void enterSingleNameReference(SingleNameReference node) {
        String varRefName = node.toString();
        if (javaParserSupport.verboseLevel > 0) {
            System.out.println("Building a variable reference for name = " + varRefName);
            System.out.println("node.genericCast = " + node.genericCast);
        }

        if (node.binding instanceof TypeBinding) { // is this name a type?
            TypeBinding type_binding = (TypeBinding) node.binding;
            assert(type_binding.isClass() || type_binding.isInterface());
            if (javaParserSupport.verboseLevel > 0) {
                System.out.println("The Single name referenced " + varRefName + " is bound to type " + type_binding.debugName());
            }
            javaParserSupport.preprocessClass(type_binding);

            JavaParser.cactionTypeReference(javaParserSupport.getPackageName(type_binding),
                                            javaParserSupport.getTypeName(type_binding),
                                            node.binding instanceof TypeVariableBinding,
                                            javaParserSupport.createJavaToken(node));
        }
        else { // the name is a variable
            String package_name = "",
                   type_name = "";

            if (node.localVariableBinding() == null) { // not a local variable
                TypeBinding type_binding = node.actualReceiverType;
                assert(type_binding.isClass() || type_binding.isInterface());
                if (javaParserSupport.verboseLevel > 0) {
                    System.out.println("The  Single name referenced " + varRefName + " is bound to type " + type_binding.debugName());
                }
                javaParserSupport.preprocessClass(type_binding);
                package_name = javaParserSupport.getPackageName(type_binding);
                type_name = javaParserSupport.getTypeName(type_binding);
            }

            JavaParser.cactionSingleNameReference(package_name, type_name, varRefName, javaParserSupport.createJavaToken(node));
        }
    }

    private void enterTypeDeclaration(TypeDeclaration node) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enterTypeDeclaration(node)");

        //
        // Get the list of type members in sorted order.
        //
        ASTNode node_list[] = javaParserSupport.orderedClassMembers.get(node);
        assert(node_list != null);
        if (node_list.length > 0) {
            ASTNode member = node_list[0];
            TypeHeaderDelimiters.put(member, node); 
        }
        else TypesWithNoBody.add(node);

        JavaParserSupport.LocalOrAnonymousType special_type = javaParserSupport.localOrAnonymousType.get(node);
        String package_name = (special_type == null
                                      ? javaParserSupport.getPackageName(node.binding)
                                      : special_type.package_name
                              ),
               typename = (special_type == null
                                  ? javaParserSupport.getTypeName(node.binding)
                                  : special_type.isAnonymous()
                                           ? special_type.typename
                                           : special_type.simplename
                          );

        JavaParser.cactionTypeDeclaration(package_name,
                                          typename,
                                          node.kind(node.modifiers) == TypeDeclaration.INTERFACE_DECL,
                                          (node.binding != null && node.binding.isAbstract()),
                                          (node.binding != null && node.binding.isFinal()),
                                          (node.binding != null && node.binding.isPrivate()),
                                          (node.binding != null && node.binding.isPublic()),
                                          (node.binding != null && node.binding.isProtected()),
                                          (node.binding != null && node.binding.isStatic() && node.binding.isNestedType()),
                                          (node.binding != null && node.binding.isStrictfp()),
                                          javaParserSupport.createJavaToken(node));

        if (node.javadoc != null) {
            node.javadoc.traverse(this, node.scope);
        }
        if (node.annotations != null) {
            int annotationsLength = node.annotations.length;
            for (int i = 0; i < annotationsLength; i++) {
                node.annotations[i].traverse(this, node.staticInitializerScope);
            }
        }
        if (node.superclass != null) {
            node.superclass.traverse(this, node.scope);
        }
        if (node.superInterfaces != null) {
            int length = node.superInterfaces.length;
            for (int i = 0; i < length; i++) {
                node.superInterfaces[i].traverse(this, node.scope);
            }
        }
        if (node.typeParameters != null) {
            int length = node.typeParameters.length;
            for (int i = 0; i < length; i++) {
                node.typeParameters[i].traverse(this, node.scope);
            }
        }

        //
        // Now, traverse the class members in the order in which they were specified.
        //
        for (ASTNode class_member : node_list) {
            if (class_member instanceof TypeDeclaration) {
                ((TypeDeclaration) class_member).traverse(this, node.scope);
            }
            else if (class_member instanceof FieldDeclaration) {
                FieldDeclaration field = (FieldDeclaration) class_member;
                if (field.isStatic()) {
                    field.traverse(this, node.staticInitializerScope);
                }
                else {
                    field.traverse(this, node.initializerScope);
                }
            }
            else if (class_member instanceof AbstractMethodDeclaration) {
                ((AbstractMethodDeclaration) class_member).traverse(this, node.scope);
            }
            else assert(false); // can't happen
        }
    }

    private void exitTypeDeclaration(TypeDeclaration node) {
        String typename = new String(node.name);
        if (TypesWithNoBody.contains(node)) {
            if (javaParserSupport.verboseLevel > 1)
                System.out.println("    Side-visiting Type Declaration Header for " + node.getClass().getName());
            JavaParser.cactionTypeDeclarationHeader(node.superclass != null,
                                                    node.superInterfaces == null ? 0 : node.superInterfaces.length,
                                                    node.typeParameters == null  ? 0 : node.typeParameters.length,
                                                    javaParserSupport.createJavaToken(node));
        }
        JavaParser.cactionTypeDeclarationEnd(javaParserSupport.createJavaToken(node));
    }


    public boolean enter(AllocationExpression node,BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (AllocationExpression,BlockScope)");

        // Call the Java side of the JNI function.
        JavaParser.cactionAllocationExpression(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (AllocationExpression,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(AllocationExpression node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of exit (AllocationExpression,BlockScope)");

        if (javaParserSupport.verboseLevel > 0) 
            System.out.println("The Allocation type is bound to type " + node.type.resolvedType.debugName());

        javaParserSupport.preprocessClass(node.type.resolvedType);
        
        JavaParser.cactionAllocationExpressionEnd(node.type.toString(),
                                                  node.arguments == null ? 0 : node.arguments.length,
                                                  javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (AllocationExpression,BlockScope)");
    }


    public boolean enter(AND_AND_Expression node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (AND_AND_Expression,BlockScope)");

        // Call the Java side of the JNI function.
        JavaParser.cactionANDANDExpression(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (AND_AND_Expression,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(AND_AND_Expression node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of exit (AND_AND_Expression,BlockScope)");

        // Call the Java side of the JNI function.
        JavaParser.cactionANDANDExpressionEnd(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (AND_AND_Expression,BlockScope)");
    }


    public boolean enter(AnnotationMethodDeclaration node,ClassScope classScope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (AnnotationMethodDeclaration,ClassScope)");

        // Call the Java side of the JNI function.
        JavaParser.cactionAnnotationMethodDeclaration(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (AnnotationMethodDeclaration,ClassScope)");

        return true; // do nothing by default, keep traversing
    }

    public void exit(AnnotationMethodDeclaration node, ClassScope classScope) {
        // do nothing by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (AnnotationMethodDeclaration,ClassScope)");
    }


    public boolean enter(Argument node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (Argument,BlockScope)");

        String name = new String(node.name);

        if (javaParserSupport.verboseLevel > 0) {
            System.out.println(" name      = " + name);
            System.out.println(" type      = " + node.type);
            System.out.println(" modifiers = " + node.modifiers);
        }

        if (catchArguments.contains(node)) {
            JavaParser.cactionCatchArgument(name, javaParserSupport.createJavaToken(node));
        }
        else {
            JavaParser.cactionArgument(name, javaParserSupport.createJavaToken(node));
        }
        
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (Argument,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(Argument node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (Argument,BlockScope)");

        String nameString = new String(node.name);

        boolean is_final = node.binding.isFinal();


        if (catchArguments.contains(node)) {
            JavaParser.cactionCatchArgumentEnd(nameString, is_final, javaParserSupport.createJavaToken(node));
        }
        else {
            JavaParser.cactionArgumentEnd(nameString, is_final, javaParserSupport.createJavaToken(node));
        }

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (Argument,BlockScope)");
    }


    public boolean enter(Argument node, ClassScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (Argument,ClassScope)");

        String name = new String(node.name);

        if (javaParserSupport.verboseLevel > 0) {
            System.out.println(" name      = " + name);
            System.out.println(" type      = " + node.type);
            System.out.println(" modifiers = " + node.modifiers);
        }

        if (catchArguments.contains(node)) {
            JavaParser.cactionCatchArgument(name, javaParserSupport.createJavaToken(node));
        }
        else {
            JavaParser.cactionArgument(name, javaParserSupport.createJavaToken(node));
        }
        
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (Argument,ClassScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(Argument node,ClassScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (Argument,ClassScope)");

        String nameString = new String(node.name);

        boolean is_final = node.binding.isFinal();


        if (catchArguments.contains(node)) {
            JavaParser.cactionCatchArgumentEnd(nameString, is_final, javaParserSupport.createJavaToken(node));
        }
        else {
            JavaParser.cactionArgumentEnd(nameString, is_final, javaParserSupport.createJavaToken(node));
        }

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (Argument,ClassScope)");
    }


    public boolean enter(ArrayAllocationExpression node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (ArrayAllocationExpression,BlockScope)");

        //
        // Traverse the children of this node here because we need to generate a 0 integer
        // expression for each missing dimension.
        //
        int dimensions_length = node.dimensions.length;
        node.type.traverse(this, scope);
        for (int i = 0; i < dimensions_length; i++) {
            if (node.dimensions[i] != null)
                 node.dimensions[i].traverse(this, scope);
            else JavaParser.cactionIntLiteral(0, "0", javaParserSupport.createJavaToken(node));
        }
        if (node.initializer != null) {
            node.initializer.traverse(this, scope);
        }

        JavaParser.cactionArrayAllocationExpression(javaParserSupport.createJavaToken(node));
        
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (ArrayAllocationExpression,BlockScope)");

        return false; // We've already traversed the children of this node. Indicate this by returning false!
    }

    public void exit(ArrayAllocationExpression node, BlockScope scope) {
        // do nothing by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (ArrayAllocationExpression,BlockScope)");
        
        JavaParser.cactionArrayAllocationExpressionEnd(node.type.toString(),
                                                       node.dimensions == null ? 0 : node.dimensions.length,
                                                       node.initializer != null,
                                                       javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (ArrayAllocationExpression,BlockScope)");
    }


    public boolean enter(ArrayInitializer node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (ArrayInitializer,BlockScope)");

        JavaParser.cactionArrayInitializer(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (ArrayInitializer,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(ArrayInitializer node, BlockScope scope) {
        // do nothing by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (ArrayInitializer, BlockScope)");
          
        JavaParser.cactionArrayInitializerEnd(node.expressions == null ? 0 : node.expressions.length,
                                              javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (ArrayInitializer, BlockScope)");
    }


    public boolean enter(ArrayQualifiedTypeReference node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (ArrayQualifiedTypeReference,BlockScope)");

        ArrayBinding arrayType = (ArrayBinding) node.resolvedType;
        TypeBinding baseType = arrayType.leafComponentType;

        JavaParser.cactionArrayTypeReference(javaParserSupport.getPackageName(baseType),
                                             javaParserSupport.getTypeName(baseType),
                                             node.dimensions(),
                                             javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (ArrayQualifiedTypeReference,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(ArrayQualifiedTypeReference node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (ArrayQualifiedTypeReference,BlockScope)");

        // do nothing by default
    }


    public boolean enter(ArrayQualifiedTypeReference node, ClassScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (ArrayQualifiedTypeReference,ClassScope)");

        ArrayBinding arrayType = (ArrayBinding) node.resolvedType;
        TypeBinding baseType = arrayType.leafComponentType;

        JavaParser.cactionArrayTypeReference(javaParserSupport.getPackageName(baseType),
                                             javaParserSupport.getTypeName(baseType),
                                             node.dimensions(),
                                             javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (ArrayQualifiedTypeReference,ClassScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(ArrayQualifiedTypeReference node, ClassScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (ArrayQualifiedTypeReference,ClassScope)");

        // do nothing by default
    }


    public boolean enter(ArrayReference node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (ArrayReference,BlockScope)");

        JavaParser.cactionArrayReference(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (ArrayReference,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(ArrayReference node, BlockScope scope) {
        // do nothing by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (ArrayReference,BlockScope)");

        JavaParser.cactionArrayReferenceEnd(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (ArrayReference,BlockScope)");
    }


    public boolean enter(ArrayTypeReference node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (ArrayTypeReference,BlockScope)");

        ArrayBinding arrayType = (ArrayBinding) node.resolvedType;
        TypeBinding baseType = arrayType.leafComponentType;

        JavaParser.cactionArrayTypeReference(javaParserSupport.getPackageName(baseType),
                                             javaParserSupport.getTypeName(baseType),
                                             node.dimensions(),
                                             javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (ArrayTypeReference,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(ArrayTypeReference node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of exit (ArrayTypeReference,BlockScope)");

         // do nothing by default

         if (javaParserSupport.verboseLevel > 0)
               System.out.println("Leaving exit (ArrayTypeReference,BlockScope)");
    }


    public boolean enter(ArrayTypeReference node, ClassScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (ArrayTypeReference,ClassScope)");

        ArrayBinding arrayType = (ArrayBinding) node.resolvedType;
        TypeBinding baseType = arrayType.leafComponentType;

        JavaParser.cactionArrayTypeReference(javaParserSupport.getPackageName(baseType),
                                             javaParserSupport.getTypeName(baseType),
                                             node.dimensions(),
                                             javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (ArrayTypeReference, ClassScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(ArrayTypeReference node, ClassScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of exit (ArrayTypeReference,ClassScope)");
        // do nothing by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (ArrayTypeReference,ClassScope)");
    }


    public boolean enter(AssertStatement node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (AssertStatement,BlockScope)");

        JavaParser.cactionAssertStatement(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (AssertStatement,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(AssertStatement node, BlockScope scope) {
        // do nothing by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (AssertStatement,BlockScope)");

        JavaParser.cactionAssertStatementEnd(node.exceptionArgument != null, javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (AssertStatement,BlockScope)");
    }


    public boolean enter(Assignment node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (Assignment,BlockScope)");

        JavaParser.cactionAssignment(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (Assignment,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(Assignment node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of exit (Assignment,BlockScope)");

        JavaParser.cactionAssignmentEnd(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (Assignment,BlockScope)");
    }


    public boolean enter(BinaryExpression node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (BinaryExpression,BlockScope)");

        JavaParser.cactionBinaryExpression(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (BinaryExpression,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(BinaryExpression node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of exit (BinaryExpression,BlockScope)");

        int operatorKind = (node.bits & ASTNode.OperatorMASK) >> ASTNode.OperatorSHIFT;

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of exit (BinaryExpression,BlockScope): operatorKind = " + operatorKind);

        JavaParser.cactionBinaryExpressionEnd(operatorKind, javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (BinaryExpression,BlockScope)");
    }


    public boolean enter(Block node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (Block,BlockScope)");

        JavaParser.cactionBlock(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (Block,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(Block node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of exit (Block,BlockScope)");

        if (javaParserSupport.verboseLevel > 1)
            System.out.println("node.explicitDeclarations = " + node.explicitDeclarations);

        int numberOfStatements = 0;
        if (node.statements != null)
            numberOfStatements = node.statements.length;

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("numberOfStatements = " + numberOfStatements);

        JavaParser.cactionBlockEnd(numberOfStatements, javaParserSupport.createJavaToken(node));

        //
        // charles4 (09/26/2011): If this block belongs to a Catch statement,
        // close the catch statement.
        //
        if (catchBlocks.contains(node)) {
            JavaParser.cactionCatchBlockEnd(javaParserSupport.createJavaToken(node));
        }

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (Block,BlockScope)");
    }


    public boolean enter(BreakStatement node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (BreakStatement,BlockScope)");

        JavaParser.cactionBreakStatement((node.label == null ? "" : new String(node.label)),
                                         javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (BreakStatement,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(BreakStatement node, BlockScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (BreakStatement,BlockScope)");
    }


    public boolean enter(CaseStatement node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (CaseStatement,BlockScope)");

        JavaParser.cactionCaseStatement(node.constantExpression != null, javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (CaseStatement,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(CaseStatement node, BlockScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (CaseStatement,BlockScope)");

        JavaParser.cactionCaseStatementEnd(node.constantExpression != null, javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (CaseStatement,BlockScope)");
    }


    public boolean enter(CastExpression node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (CastExpression,BlockScope)");

        JavaParser.cactionCastExpression(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (CastExpression,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(CastExpression node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of exit (CastExpression,BlockScope)");

        JavaParser.cactionCastExpressionEnd(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (CastExpression,BlockScope)");
    }


    public boolean enter(CharLiteral node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (CharLiteral,BlockScope)");

        JavaParser.cactionCharLiteral(node.constant.charValue(), javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (CharLiteral,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(CharLiteral node, BlockScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (CharLiteral,BlockScope)");
    }


    public boolean enter(ClassLiteralAccess node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (ClassLiteralAccess,BlockScope)");

        JavaParser.cactionClassLiteralAccess(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (ClassLiteralAccess,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(ClassLiteralAccess node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of exit(ClassLiteralAccess,BlockScope)");

        JavaParser.cactionClassLiteralAccessEnd(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (ClassLiteralAccess,BlockScope)");
    }


    public boolean enter(Clinit node, ClassScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (Clinit,ClassScope)");

        JavaParser.cactionClinit(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (Clinit,ClassScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(Clinit node, ClassScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (Clinit,BlockScope)");
    }

    public boolean enter(CompilationUnitDeclaration node, CompilationUnitScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (CompilationUnitDeclaration,CompilationUnitScope)");

        // Except for out here for debugging, the filename string is not used (even on the C++ side of the JNI function.
        String filename = new String(node.getFileName());
        if (javaParserSupport.verboseLevel > 0) {
            System.out.println("Compiling file = " + filename);
        }

        // Ouput some information about the CompilationUnitScope (we don't use the package name currently).
        // DQ (9/11/2011): Static analysis tools suggest using StringBuffer instead of String.
        // String packageReference = "";
        StringBuffer packageReference = new StringBuffer();
        for (int i = 0, tokenArrayLength = scope.currentPackageName.length; i < tokenArrayLength; i++) {
            String tokenString = new String(scope.currentPackageName[i]);

            if (i > 0) {
                packageReference.append('.');
            }

            packageReference.append(tokenString);
        }
        String package_name = new String(packageReference);

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Package name = " + packageReference.toString());

        // Call the Java side of the JNI function.
        // This function only does a few tests on the C++ side to make sure that it is ready to construct the ROSE AST.
        JavaParser.cactionCompilationUnitDeclaration(package_name, filename, javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (CompilationUnitDeclaration,CompilationUnitScope)");

        return true; // do nothing by default, keep traversing
    }

    public void exit(CompilationUnitDeclaration node, CompilationUnitScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of exit (CompilationUnitDeclaration,CompilationUnitScope)");

        int numberOfStatements = 0;
        if (node.types != null) {
            numberOfStatements += node.types.length;
            if (javaParserSupport.verboseLevel > 0)
                System.out.println("node.types.length = " + node.types.length);
        }

        if (node.imports != null) {
            numberOfStatements += node.imports.length;
            if (javaParserSupport.verboseLevel > 0)
                System.out.println("node.imports.length = " + node.imports.length);
        }

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("numberOfStatements = " + numberOfStatements);

        JavaParser.cactionCompilationUnitDeclarationEnd(numberOfStatements, javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (CompilationUnitDeclaration,CompilationUnitScope)");
    }


    public boolean enter(CompoundAssignment node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (CompoundAssignment,BlockScope)");

        JavaParser.cactionCompoundAssignment(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (CompoundAssignment,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(CompoundAssignment node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of exit (CompoundAssignment,BlockScope)");

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of exit (CompoundAssignment,BlockScope): operator_kind" + node.toString());

        int operator_kind = node.operator;

        JavaParser.cactionCompoundAssignmentEnd(operator_kind, javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (CompoundAssignment,BlockScope)");
    }


    public boolean enter(ConditionalExpression node,BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (ConditionalExpression,BlockScope)");

        JavaParser.cactionConditionalExpression(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (ConditionalExpression,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(ConditionalExpression node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of exit (ConditionalExpression,BlockScope)");

        JavaParser.cactionConditionalExpressionEnd(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (ConditionalExpression,BlockScope)");
    }


    public boolean enter(ConstructorDeclaration node, ClassScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (ConstructorDeclaration,ClassScope)");

        assert(! node.isDefaultConstructor());
        
        // char [] name = node.selector;
        // System.out.println("Inside of enter (ConstructorDeclaration,ClassScope) method name = " + node.selector);
        String name = new String(node.selector);
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (ConstructorDeclaration,ClassScope) method name = " + name);

        //
        //
        //
        if (node.constructorCall != null) {
            MethodHeaderDelimiters.put(node.constructorCall, node);
        }
        else if (node.statements != null && node.statements.length > 0) {
            MethodHeaderDelimiters.put(node.statements[0], node);
        }
        
        // argument types
        if (node.typeParameters != null) {
            for (int i = 0, typeArgumentsLength = node.typeParameters.length; i < typeArgumentsLength; i++) {
                System.out.println("     --- constructor typeParameters = " + node.typeParameters[i]);
            }
            // TODO: What TO DO?
        }
        else {
            // For a function defined in the input program, the typeParameters array is empty, but the ECJ
            // specific AST traversal will visit the type parameters. Not clear why this is organized like this.
            // System.out.println("     --- method typeParameters (empty) = " + node.typeParameters);
        }

        JavaParser.cactionConstructorDeclaration(name, javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (ConstructorDeclaration,ClassScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(ConstructorDeclaration node, ClassScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of exit (ConstructorDeclaration,ClassScope)");

        if (! node.isDefaultConstructor()) {        
            // DQ (7/31/2011): Added more precise handling of statements to be collected from the statement stack.
            int numberOfStatements = 0;
            if (node.statements != null) {
                numberOfStatements = node.statements.length;
                if (javaParserSupport.verboseLevel > 0)
                    System.out.println("Inside of exit (ConstructorDeclaration,ClassScope): numberOfStatements = " + numberOfStatements);
            }

            // DQ (7/31/2011): I don't know if there is just one of these (super()) or if there could be many.
            if (node.constructorCall != null) {
                numberOfStatements++;
                // System.out.println("Inside of exit (ConstructorDeclaration,ClassScope): increment the numberOfStatements = " + numberOfStatements);
            }
          
            if (node.constructorCall == null && (node.statements == null || node.statements.length == 0)) {
                if (javaParserSupport.verboseLevel > 1)
                    System.out.println("    Side-visiting Constructor Declaration Header for " + node.getClass().getName());
                javaParserSupport.processConstructorDeclarationHeader(node, javaParserSupport.createJavaToken(node));
            }

            JavaParser.cactionConstructorDeclarationEnd(numberOfStatements, javaParserSupport.createJavaToken(node));
        }
    }


    public boolean enter(ContinueStatement node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (ContinueStatement,BlockScope)");

        JavaParser.cactionContinueStatement((node.label == null ? "" : new String(node.label)), javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (ContinueStatement,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(ContinueStatement node, BlockScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (ContinueStatement,BlockScope)");
    }


    public boolean enter(DoStatement node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (DoStatement,BlockScope)");

        JavaParser.cactionDoStatement(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (DoStatement,BlockScope)");

        return true; // do nothing by  node, keep traversing
    }

    public void exit(DoStatement node, BlockScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of exit (DoStatement,BlockScope)");

        JavaParser.cactionDoStatementEnd(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (DoStatement,BlockScope)");
    }


    public boolean enter(DoubleLiteral node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (DoubleLiteral,BlockScope)");

        JavaParser.cactionDoubleLiteral(node.constant.doubleValue(), new String(node.source()), javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (DoubleLiteral,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(DoubleLiteral node, BlockScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (DoubleLiteral,BlockScope)");
    }


    public boolean enter(EmptyStatement node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (EmptyStatement,BlockScope)");

        JavaParser.cactionEmptyStatement(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (EmptyStatement,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(EmptyStatement node, BlockScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (EmptyStatement,BlockScope)");

        JavaParser.cactionEmptyStatementEnd(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (EmptyStatement,BlockScope)");
    }


    public boolean enter(EqualExpression node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (EqualExpression,BlockScope)");

        JavaParser.cactionEqualExpression(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (EqualExpression,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(EqualExpression node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of exit (EqualExpression,BlockScope)");

        int operator_kind = (node.bits & ASTNode.OperatorMASK) >> ASTNode.OperatorSHIFT; // EQUAL_EQUAL or NOT_EQUAL

        JavaParser.cactionEqualExpressionEnd(operator_kind, javaParserSupport.createJavaToken(node));
    }


    public boolean enter(ExplicitConstructorCall node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (ExplicitConstructorCall,BlockScope)");

        JavaParser.cactionExplicitConstructorCall(javaParserSupport.createJavaToken(node));
          
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (ExplicitConstructorCall,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(ExplicitConstructorCall node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of exit (ExplicitConstructorCall,BlockScope)");

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("In visit (ExplicitConstructorCall,BlockScope): node.accessMode = " + node.accessMode);

        //
        // Push the types of the parameters of the method that was matched for this call so that the
        // translator can retrieve the exact method in question.
        //
        if (node.binding instanceof ParameterizedMethodBinding && ((ParameterizedMethodBinding) node.binding).hasSubstitutedParameters()) {
            ParameterizedMethodBinding parameterized_method_binding = (ParameterizedMethodBinding) node.binding;
            Constructor constructor = javaParserSupport.getRawConstructor(parameterized_method_binding);
            assert(constructor != null);
            Class argument_types[] = constructor.getParameterTypes();
            for (int i = 0; i < argument_types.length; i++) {
                javaParserSupport.generateAndPushType(argument_types[i]);
            }
        }
        else {
            MethodBinding constructor_binding = node.binding;
            TypeBinding argument_types[] = constructor_binding.parameters;
            for (int i = 0; i < node.binding.parameters.length; i++) {
                javaParserSupport.generateAndPushType(argument_types[i], javaParserSupport.createJavaToken(node));
            }
        }

        JavaParser.cactionExplicitConstructorCallEnd(node.isImplicitSuper(),
                                                     node.isSuperAccess(),
                                                     node.qualification != null,
                                                     node.typeArguments == null ? 0 : node.typeArguments.length,
                                                     node.arguments == null ? 0 : node.arguments.length,
                                                     javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (ExplicitConstructorCall,BlockScope)");
    }


    public boolean enter(ExtendedStringLiteral node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (ExtendedStringLiteral,BlockScope)");

        JavaParser.cactionExtendedStringLiteral(node.constant.stringValue(), javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (ExtendedStringLiteral,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(ExtendedStringLiteral node, BlockScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (ExtendedStringLiteral,BlockScope)");
    }


    public boolean enter(FalseLiteral node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (FalseLiteral,BlockScope)");

        JavaParser.cactionFalseLiteral(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (FalseLiteral,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(FalseLiteral node, BlockScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (FalseLiteral,BlockScope)");
    }


    public boolean enter(FieldDeclaration node, MethodScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (FieldDeclaration,BlockScope)");

        // DO NOTHING !!!
          
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (FieldDeclaration,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(FieldDeclaration node, MethodScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of exit (FieldDeclaration,BlockScope)");

        boolean isPrivate   = (node.binding != null && node.binding.isPrivate());
        boolean isProtected = (node.binding != null && node.binding.isProtected());
        boolean isPublic    = (node.binding != null && node.binding.isPublic());

        boolean isVolatile  = (node.binding != null && node.binding.isVolatile());
        boolean isSynthetic = (node.binding != null && node.binding.isSynthetic());
        boolean isStatic    = (node.binding != null && node.binding.isStatic());
        boolean isTransient = (node.binding != null && node.binding.isTransient());

        boolean isFinal = node.binding.isFinal();

        boolean hasInitializer = (node.initialization != null) ? true : false;

        String name = new String(node.name);

        JavaParser.cactionFieldDeclarationEnd(name,hasInitializer,isFinal,isPrivate,isProtected,isPublic,isVolatile,isSynthetic,isStatic,isTransient, javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (FieldDeclaration,BlockScope)");
    }


    public boolean enter(FieldReference node, BlockScope scope) {
        // System.out.println("Sorry, not implemented in support for FieldReference(BlockScope): xxx");
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (FieldReference,BlockScope)");

        String fieldRefName = new String(node.token);
               
        JavaParser.cactionFieldReference(fieldRefName, javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (FieldReference,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(FieldReference node, BlockScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (FieldReference,BlockScope)");

        String fieldRefName = new String(node.token);
          
        JavaParser.cactionFieldReferenceEnd(fieldRefName, javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (FieldReference,BlockScope)");
    }


    public boolean enter(FieldReference node, ClassScope scope) {
        // System.out.println("Sorry, not implemented in support for FieldReference(ClassScope): xxx");
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (FieldReference,ClassScope)");


        String fieldRefName = new String(node.token);

        JavaParser.cactionFieldReference(fieldRefName, javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (FieldReference,ClassScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(FieldReference node, ClassScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (FieldReference,ClassScope)");

        String fieldRefName = new String(node.token);
          
        JavaParser.cactionFieldReferenceEnd(fieldRefName, javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (FieldReference,BlockScope)");
    }


    public boolean enter(FloatLiteral node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (FloatLiteral,BlockScope)");

        JavaParser.cactionFloatLiteral(node.constant.floatValue(), new String(node.source()), javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (FloatLiteral,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(FloatLiteral node, BlockScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (FloatLiteral,BlockScope)");
    }


    public boolean enter(ForeachStatement node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
               System.out.println("Inside of enter (ForeachStatement,BlockScope)");

        JavaParser.cactionForeachStatement(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (ForeachStatement,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(ForeachStatement node, BlockScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (ForeachStatement,BlockScope)");

        JavaParser.cactionForeachStatementEnd(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (ForeachStatement,BlockScope)");
    }


    public boolean enter(ForStatement node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (ForStatement,BlockScope)");

        JavaParser.cactionForStatement(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (ForStatement,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(ForStatement node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of exit (ForStatement,BlockScope)");

        JavaParser.cactionForStatementEnd(node.initializations == null ? 0 : node.initializations.length,
                                          node.condition != null,
                                          node.increments == null ? 0 : node.increments.length,
                                          javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (ForStatement,BlockScope)");
    }


    public boolean enter(IfStatement node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (IfStatement,BlockScope)");

        JavaParser.cactionIfStatement(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (IfStatement,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(IfStatement node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of exit (IfStatement,BlockScope)");

        JavaParser.cactionIfStatementEnd(node.elseStatement != null, javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (IfStatement,BlockScope)");
    }

    public boolean enter(ImportReference node, CompilationUnitScope scope) {
        if (javaParserSupport.verboseLevel > 1)
            System.out.println("Inside of enter (ImportReference,CompilationUnitScope)");

        //
        // An Import statement may refer to a package name (optionally followed by ".*"), a type name, which
        // in the case of a static import may optionally followed by ".*" or a name suffix (the suffix
        // represents a field or a method name contained in the type in question). 
        //
        Class cls = null;
        String name_suffix = "";

        //
        // Construct the import reference name.  While we are at it, look for a contained class, if any, in that name.
        //
        StringBuffer import_reference = new StringBuffer();
        for (int i = 0, tokenArrayLength = node.tokens.length; i < tokenArrayLength; i++) {
            String token_string = new String(node.tokens[i]);

            if (i > 0) { // Not the first simple name?  Add a "." seperator to the QualifiedName 
                import_reference.append('.');
            }
            import_reference.append(token_string);

            //
            // Now, look to see if the name we've constructed so far is the name of a class.
            // Once we've found the longest prefix of the import name that is a class, we append
            // the remaining suffix to the name_suffix variable.
            //
            Class c = javaParserSupport.preprocessClass(import_reference.toString());
            if (c != null) { // a class?
                cls = c; // save the latest class we've found.
            }
            else if (cls != null){ // already found a class, so this name is a suffix.
                name_suffix += token_string;
            }
        }

        boolean containsWildcard = ((node.bits & node.OnDemand) != 0);
        String importReferenceWithoutWildcard = import_reference.toString();
        if (containsWildcard) {
            import_reference.append(".*");
        }

        if (javaParserSupport.verboseLevel > 1)
            System.out.println("importReference (string) = " + import_reference.toString());

        String package_name,
               type_name;

        if (cls == null) { // No class found?  ... Then assume this is the name of a package.
            package_name = importReferenceWithoutWildcard;
            type_name = "";

            JavaParser.cactionPushPackage(package_name, javaParserSupport.createJavaToken(node));
            JavaParser.cactionPopPackage();
        }
        else {
            String canonical_name = cls.getCanonicalName(),
                   simple_name = cls.getSimpleName();
            package_name = javaParserSupport.getMainPackageName(cls, 0);
            type_name = canonical_name.substring(package_name.length() == 0 ? 0 : package_name.length() + 1);
        }

        JavaParser.cactionImportReference(node.isStatic(), package_name, type_name, name_suffix, containsWildcard, javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (ImportReference,CompilationUnitScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(ImportReference node, CompilationUnitScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (ImportReference,CompilationUnitScope)");
    }


    public boolean enter(Initializer node, MethodScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (Initializer,MethodScope)");

        JavaParser.cactionInitializer(node.isStatic(), javaParserSupport.initializerName.get(node), javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (Initializer,MethodScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(Initializer node, MethodScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (Initializer,MethodScope)");

        JavaParser.cactionInitializerEnd(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (Initializer, MethodScope)");
    }


    public boolean enter( InstanceOfExpression node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (InstanceOfExpression,BlockScope)");

        JavaParser.cactionInstanceOfExpression(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (InstanceOfExpression,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(InstanceOfExpression node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of exit (InstanceOfExpression,BlockScope)");

        JavaParser.cactionInstanceOfExpressionEnd(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (InstanceOfExpression,BlockScope)");
    }


    public boolean enter(IntLiteral node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (IntLiteral,BlockScope) value = " + node.toString());

        JavaParser.cactionIntLiteral(node.constant.intValue(), new String(node.source()), javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (IntLiteral,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(IntLiteral node, BlockScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (IntLiteral,BlockScope)");
    }


    //**********************************************************
    //*                                                        *
    //*    Start of JavaDoc Nodes.                             *
    //*                                                        *
    //**********************************************************
/*
    public boolean enter(Javadoc node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (Javadoc,BlockScope)");

        JavaParser.cactionJavadoc(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (Javadoc,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(Javadoc node, BlockScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (Javadoc,BlockScope)");
    }


    public boolean enter(Javadoc node, ClassScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (Javadoc,ClassScope)");

        JavaParser.cactionJavadocClassScope(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (Javadoc,ClassScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(Javadoc node, ClassScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (Javadoc,ClassScope)");
    }


    public boolean enter(JavadocAllocationExpression node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (JavadocAllocationExpression,BlockScope)");

        JavaParser.cactionJavadocAllocationExpression(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (JavadocAllocationExpression,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(JavadocAllocationExpression node, BlockScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (JavadocAllocationExpression,BlockScope)");
    }


    public boolean enter(JavadocAllocationExpression node, ClassScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (JavadocAllocationExpression,ClassScope)");

        JavaParser.cactionJavadocAllocationExpressionClassScope(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (JavadocAllocationExpression,ClassScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(JavadocAllocationExpression node, ClassScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (JavadocAllocationExpression,ClassScope)");
    }


    public boolean enter(JavadocArgumentExpression node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter JavadocArgumentExpression(,BlockScope)");

        JavaParser.cactionJavadocArgumentExpression(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (JavadocArgumentExpression,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(JavadocArgumentExpression node, BlockScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (JavadocArgumentExpression,BlockScope)");
    }


    public boolean enter(JavadocArgumentExpression node, ClassScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (JavadocArgumentExpression,ClassScope)");

        JavaParser.cactionJavadocArgumentExpressionClassScope(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (JavadocArgumentExpression,ClassScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(JavadocArgumentExpression node, ClassScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (JavadocArgumentExpression,ClassScope)");
    }


    public boolean enter(JavadocArrayQualifiedTypeReference node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (JavadocArrayQualifiedTypeReference,BlockScope)");

        JavaParser.cactionJavadocArrayQualifiedTypeReference(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (JavadocArrayQualifiedTypeReference,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(JavadocArrayQualifiedTypeReference node, BlockScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (JavadocArrayQualifiedTypeReference,BlockScope)");
    }


    public boolean enter(JavadocArrayQualifiedTypeReference node, ClassScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (JavadocArrayQualifiedTypeReference,ClassScope)");

        JavaParser.cactionJavadocArrayQualifiedTypeReferenceClassScope(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (JavadocArrayQualifiedTypeReference,ClassScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(JavadocArrayQualifiedTypeReference node, ClassScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (JavadocArrayQualifiedTypeReference,ClassScope)");
    }


    public boolean enter(JavadocArraySingleTypeReference node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (JavadocArraySingleTypeReference,BlockScope)");

        JavaParser.cactionJavadocArraySingleTypeReference(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (JavadocArraySingleTypeReference,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(JavadocArraySingleTypeReference node, BlockScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (JavadocArraySingleTypeReference,BlockScope)");
    }


    public boolean enter(JavadocArraySingleTypeReference node, ClassScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (JavadocArraySingleTypeReference,ClassScope)");

        JavaParser.cactionJavadocArraySingleTypeReferenceClassScope(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (JavadocArraySingleTypeReference,ClassScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(JavadocArraySingleTypeReference node, ClassScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (JavadocArraySingleTypeReference,ClassScope)");
    }


    public boolean enter(JavadocFieldReference node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (JavadocFieldReference,BlockScope)");

        JavaParser.cactionJavadocFieldReference(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (JavadocFieldReference,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(JavadocFieldReference node, BlockScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (JavadocFieldReference,BlockScope)");
    }


    public boolean enter(JavadocFieldReference node, ClassScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (JavadocFieldReference,ClassScope)");

        JavaParser.cactionJavadocFieldReferenceClassScope(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (JavadocFieldReference,ClassScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(JavadocFieldReference node, ClassScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (JavadocFieldReference,ClassScope)");
    }


    public boolean enter(JavadocImplicitTypeReference node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (JavadocImplicitTypeReference,BlockScope)");

        JavaParser.cactionJavadocImplicitTypeReference(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (JavadocImplicitTypeReference,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(JavadocImplicitTypeReference node, BlockScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (JavadocImplicitTypeReference,BlockScope)");
    }


    public boolean enter(JavadocImplicitTypeReference node, ClassScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (JavadocImplicitTypeReference,ClassScope)");

        JavaParser.cactionJavadocImplicitTypeReferenceClassScope(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (JavadocImplicitTypeReference,ClassScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(JavadocImplicitTypeReference node, ClassScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (JavadocImplicitTypeReference,ClassScope)");
    }


    public boolean enter(JavadocMessageSend node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (JavadocMessageSend,BlockScope)");

        JavaParser.cactionJavadocMessageSend(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (JavadocMessageSend,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(JavadocMessageSend node, BlockScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (JavadocMessageSend,BlockScope)");
    }


    public boolean enter(JavadocMessageSend node, ClassScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (JavadocMessageSend,ClassScope)");

        JavaParser.cactionJavadocMessageSendClassScope(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (JavadocMessageSend,ClassScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(JavadocMessageSend node, ClassScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (JavadocMessageSend,ClassScope)");
    }


    public boolean enter(JavadocQualifiedTypeReference node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (JavadocQualifiedTypeReference,BlockScope)");

        JavaParser.cactionJavadocQualifiedTypeReference(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (JavadocQualifiedTypeReference,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(JavadocQualifiedTypeReference node, BlockScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (JavadocQualifiedTypeReference,BlockScope)");
    }


    public boolean enter(JavadocQualifiedTypeReference node, ClassScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (JavadocQualifiedTypeReference,ClassScope)");

        JavaParser.cactionJavadocQualifiedTypeReferenceClassScope(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (JavadocQualifiedTypeReference,ClassScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(JavadocQualifiedTypeReference node, ClassScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (JavadocQualifiedTypeReference,ClassScope)");
    }


    public boolean enter(JavadocReturnStatement node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (JavadocReturnStatement,BlockScope)");

        JavaParser.cactionJavadocReturnStatement(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (JavadocReturnStatement,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(JavadocReturnStatement node, BlockScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (JavadocReturnStatement,BlockScope)");
    }


    public boolean enter(JavadocReturnStatement node, ClassScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (JavadocReturnStatement,ClassScope)");

        JavaParser.cactionJavadocReturnStatementClassScope(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (JavadocReturnStatement,ClassScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(JavadocReturnStatement node, ClassScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (JavadocReturnStatement,ClassScope)");
    }


    public boolean enter(JavadocSingleNameReference node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (JavadocSingleNameReference,BlockScope)");

        JavaParser.cactionJavadocSingleNameReference(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (JavadocSingleNameReference,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(JavadocSingleNameReference node, BlockScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (JavadocSingleNameReference,BlockScope)");
    }


    public boolean enter(JavadocSingleNameReference node, ClassScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (JavadocSingleNameReference,ClassScope)");

        JavaParser.cactionJavadocSingleNameReferenceClassScope(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (JavadocSingleNameReference,ClassScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(JavadocSingleNameReference node, ClassScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (JavadocSingleNameReference,ClassScope)");
    }


    public boolean enter(JavadocSingleTypeReference node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (JavadocSingleTypeReference,BlockScope)");

        JavaParser.cactionJavadocSingleTypeReference(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (JavadocSingleTypeReference,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(JavadocSingleTypeReference node, BlockScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (JavadocSingleTypeReference,BlockScope)");
    }


    public boolean enter(JavadocSingleTypeReference node, ClassScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (JavadocSingleTypeReference,ClassScope)");

        JavaParser.cactionJavadocSingleTypeReferenceClassScope(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (JavadocSingleTypeReference,ClassScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(JavadocSingleTypeReference node, ClassScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (JavadocSingleTypeReference,ClassScope)");
    }
*/

    //**********************************************************
    //*                                                        *
    //*    End of JavaDoc Nodes.                               *
    //*                                                        *
    //**********************************************************

    public boolean enter(LabeledStatement node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (LabeledStatement,BlockScope)");

        JavaParser.cactionLabeledStatement(new String(node.label), javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (LabeledStatement,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(LabeledStatement node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (LabeledStatement,BlockScope)");

        JavaParser.cactionLabeledStatementEnd(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (LabeledStatement,BlockScope)");
    }


    public boolean enter(LocalDeclaration node, BlockScope scope) {
        // LocalDeclarations is derived from AbstractVariableDeclaration

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (LocalDeclaration,BlockScope)");

        // Do Nothing 

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (LocalDeclaration,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(LocalDeclaration node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (LocalDeclaration,BlockScope)");

        String name = new String(node.name);

        boolean isFinal = node.binding.isFinal();

        // Build the variable declaration using the type from the astJavaTypeStack.
        // Note that this may have to handle an array of names or be even more complex in the future.
        JavaParser.cactionLocalDeclarationEnd(name, node.initialization != null, isFinal, javaParserSupport.createJavaToken(node));
    }


    public boolean enter(LongLiteral node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (LongLiteral,BlockScope)");

        JavaParser.cactionLongLiteral(node.constant.longValue(), new String(node.source()), javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (LongLiteral,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(LongLiteral node, BlockScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (LongLiteral,BlockScope)");
    }


    public boolean enter(MarkerAnnotation node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (MarkerAnnotation,BlockScope)");

        JavaParser.cactionMarkerAnnotation(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (MarkerAnnotation,BlockScope)");

        return true;
    }

    public void exit(MarkerAnnotation node, BlockScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (MarkerAnnotation,BlockScope)");
    }


    public boolean enter(MemberValuePair node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (MemberValuePair,BlockScope)");

        JavaParser.cactionMemberValuePair(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (MemberValuePair,BlockScope)");

        return true;
    }

    public void exit(MemberValuePair node, BlockScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (MemberValuePair,BlockScope)");
    }


    public boolean enter(MessageSend node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (MessageSend,BlockScope)");

        String method_name                 = new String(node.selector);
        String associatedClassVariableName = node.receiver.toString();

        if (javaParserSupport.verboseLevel > 0) {
            System.out.println("MessageSend node = " + node);

            System.out.println("     --- function call name = " + method_name);

            System.out.println("     --- function call from class name (binding)            = " + node.binding);
            System.out.println("     --- function call from class name (receiver)           = " + node.receiver);
            System.out.println("     --- function call from class name (associatedClassVar) = " + associatedClassVariableName);
            System.out.println("     --- function call from class name (associatedClass)    = " + node.actualReceiverType.debugName());
        }

        // 
        // Make sure this class is available
        //
        javaParserSupport.preprocessClass(node.actualReceiverType);

        JavaParser.cactionMessageSend(javaParserSupport.getPackageName(node.actualReceiverType),
                                      javaParserSupport.getTypeName(node.actualReceiverType),
                                      method_name,
                                      javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (MessageSend,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(MessageSend node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of exit (MessageSend,BlockScope)");

// TODO: Remove this !!!        
/*
        String package_name = javaParserSupport.getPackageName(node.actualReceiverType),
               type_name = javaParserSupport.getTypeName(node.actualReceiverType),
               method_name = new String(node.binding.selector);

System.out.println("Type " + node.actualReceiverType.debugName() + (node.actualReceiverType instanceof BinaryTypeBinding ? " is " : (" is not (" + node.actualReceiverType.getClass().getCanonicalName() + ")")) + " a binary type");
if (node.actualReceiverType instanceof ParameterizedTypeBinding) {
ParameterizedTypeBinding ptype_binding = (ParameterizedTypeBinding) node.actualReceiverType;
System.out.println("The erasure type is (" + (ptype_binding.erasure().getClass().getCanonicalName() + ") ") + ptype_binding.erasure().debugName());
System.out.println("The generic type is (" + (ptype_binding.genericType().getClass().getCanonicalName() + ") ") + ptype_binding.genericType().debugName());
MethodBinding mbinding[] = ptype_binding.getMethods(node.binding.selector, node.binding.parameters.length);
System.out.println("There are " + mbinding.length + " methods with the name " + method_name + " with " + node.binding.parameters.length + " arguments in type " + node.actualReceiverType.debugName());
for (int i = 0; i < mbinding.length; i++) {
    TypeBinding arguments[] = mbinding[0].parameters;
    if (arguments.length > 0) {
        System.out.println("Processing method " + method_name + ":");
        for (int k = 0; k < arguments.length; k++) {
            String arg_package_name = new String(arguments[k].qualifiedPackageName()),
                   arg_type_name = new String(arguments[k].qualifiedSourceName());
            if (arguments[k].isArrayType()) {
                System.out.println("Can't process Array type parameters yet!");
                System.exit(1);
            }
            System.out.println("    argument " + k + ": " + arguments[k].getClass().getCanonicalName() + ": " + arguments[k].debugName());
        }
    }
}
}

        if (node.binding instanceof ParameterizedMethodBinding) {
            ParameterizedMethodBinding parameterized_method_binding = (ParameterizedMethodBinding) node.binding;
            if (parameterized_method_binding.hasSubstitutedParameters()) {
                TypeBinding arguments[] = parameterized_method_binding.original().parameters; // node.binding.parameters;
                Class parameterTypes[] = new Class[arguments.length];
                if (arguments.length > 0) {
                    System.out.println("Processing method " + method_name + ":");
                    for (int i = 0; i < arguments.length; i++) {
                        String arg_package_name = new String(arguments[i].qualifiedPackageName()),
                               arg_type_name = new String(arguments[i].qualifiedSourceName());
                        if (arguments[i].isArrayType()) {
                            System.out.println("Can't process Array type parameters yet!");
                            System.exit(1);
                        }
                        parameterTypes[i] = javaParserSupport.findClass(arg_package_name, arg_type_name);
                        assert(parameterTypes[i] != null);
                        System.out.println("    argument " + i + ": " + arguments[i].getClass().getCanonicalName() + ": " + arguments[i].debugName());
                    }
                }
                Class<?> cls = javaParserSupport.findClass(package_name, type_name);
                assert(cls != null);
                try {
                    Method method = cls.getDeclaredMethod(method_name, parameterTypes);
                }
                catch (NoSuchMethodException e) {
                    System.out.println("***Can't find method " + method_name);
                }
                Method methods[] = cls.getDeclaredMethods();
                for (int i = 0; i < methods.length; i++) {
                    Method method = methods[i];
                    Type[] types = method.getGenericParameterTypes();
                    assert(types != null);
                    if (types.length == arguments.length && method_name.equals(method.getName())) {
                        int j = 0;
                        for (; j < types.length; j++) {
                            assert(types[j] != null);
                            if (types[j] instanceof TypeVariable){
                                TypeVariable<?> type = (TypeVariable<?>) types[j];
                                if (! type.getName().equals(arguments[j].debugName()))
                                    break;
                            }
                            else if (types[j] instanceof Class){
                                Class class_arg = (Class) types[j];
                                if (class_arg != javaParserSupport.findClass(arguments[j]))
                                    break;
                            }
                            else {
                                System.out.print("Don't know what to do with type " + types[j].getClass().getCanonicalName());
                                break;
                            }
                        }
                        if (j == types.length) {
                            // Accept Method as a match !!!
                            System.out.println("Found a match for method " + method_name + " with " + types.length + " arguments");
                        }
                    }
                }
                
                System.exit(1);
//                MethodBinding method_binding = parameterized_method_binding.original();
//                TypeBinding arguments[] = method_binding.parameters;
//                if (arguments.length > 0) {
//                    System.out.print(arguments[0].getClass().getCanonicalName() + ": " + arguments[0].debugName());
//                    for (int i = 1; i < arguments.length; i++) {
//                        System.out.print(", " + arguments[i].getClass().getCanonicalName() + ": " + arguments[i].debugName());
//                    }
//                }
            }
else System.out.println("Method " + method_name + " has no substituted parameters");
        }
*/
        //
        // Push the types of the parameters of the method that was matched for this call so that the
        // translator can retrieve the exact method in question.
        //
        if (node.binding instanceof ParameterizedMethodBinding && ((ParameterizedMethodBinding) node.binding).hasSubstitutedParameters()) {
            ParameterizedMethodBinding parameterized_method_binding = (ParameterizedMethodBinding) node.binding;
            Method method = javaParserSupport.getRawMethod(parameterized_method_binding);
            assert(method != null);
            Class argument_types[] = method.getParameterTypes();
            for (int i = 0; i < argument_types.length; i++) {
                javaParserSupport.generateAndPushType(argument_types[i]);
            }
        }
        else {
            MethodBinding method_binding = node.binding;
            TypeBinding argument_types[] = method_binding.parameters;
            for (int i = 0; i < argument_types.length; i++) {
                javaParserSupport.generateAndPushType(argument_types[i], javaParserSupport.createJavaToken(node));
            }
        }
        
        String package_name = javaParserSupport.getPackageName(node.actualReceiverType),
               type_name = javaParserSupport.getTypeName(node.actualReceiverType),
               method_name = new String(node.binding.selector);

        int num_dimensions = 0;
        if (node.actualReceiverType instanceof ArrayBinding) { // if the type is an Array type
            ArrayBinding array_binding = (ArrayBinding) node.actualReceiverType;
            num_dimensions = array_binding.dimensions;
            TypeBinding base_binding = array_binding.leafComponentType;
            assert(! (base_binding instanceof ArrayBinding));
            type_name = javaParserSupport.getTypeName(base_binding);
        }

        JavaParser.cactionMessageSendEnd(node.binding.isStatic(),
                                         package_name,
                                         type_name,
                                         num_dimensions,
                                         method_name,
                                         node.typeArguments == null ? 0 : node.typeArguments.length,
                                         node.arguments == null ? 0 : node.arguments.length,
                                         javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (MessageSend,BlockScope)");
    }


    public boolean enter(MethodDeclaration node, ClassScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (MethodDeclaration,ClassScope)");

        //
        //
        //
        if (node.statements != null && node.statements.length > 0) {
            MethodHeaderDelimiters.put(node.statements[0], node);
        }

        String name = new String(node.selector);
        if (javaParserSupport.verboseLevel > 0) {
            System.out.println("Inside of enter (MethodDeclaration,ClassScope) method name = " + name);

            // Return type
            System.out.println("     --- method returnType = " + node.returnType);

            // argument types
            if (node.typeParameters != null) {
                for (int i = 0, typeArgumentsLength = node.typeParameters.length; i < typeArgumentsLength; i++) {
                    System.out.println("     --- method typeParameters = " + node.typeParameters[i]);
                }
            }
            else {
                // For a function defined in the input program, the typeParameters array is empty, but the ECJ
                // specific AST traversal will visit the type parameters. Not clear why this is organized like this.
                System.out.println("     --- method typeParameters (empty) = " + node.typeParameters);
            }

            if (node.statements != null) {
                for (int i = 0, statementListLength = node.statements.length; i < statementListLength; i++) {
                    System.out.println("     --- method statements[" + i + "] = " + node.statements[i]);
                }
            }
        }

        if (javaParserSupport.verboseLevel > 2)
            System.out.println("Process the return type = " + node.returnType);

        if (javaParserSupport.verboseLevel > 2)
            System.out.println("DONE: Process the return type = " + node.returnType);

        // Setup the function modifiers
        boolean isAbstract = node.isAbstract();
        boolean isNative   = node.isNative();
        boolean isStatic   = node.isStatic();

        boolean isFinal    = node.binding.isFinal();

        boolean isPrivate = (node.binding != null && node.binding.isPrivate()) ? true : false;

        // These is no simple function for theses cases.
        boolean isSynchronized = ((node.modifiers & ClassFileConstants.AccSynchronized) != 0) ? true : false;
        boolean isPublic       = ((node.modifiers & ClassFileConstants.AccPublic)       != 0) ? true : false;
        boolean isProtected    = ((node.modifiers & ClassFileConstants.AccProtected)    != 0) ? true : false;

        boolean isStrictfp     = node.binding.isStrictfp();

        // These are always false for member functions.
        boolean isVolatile     = false;
        boolean isTransient    = false;

        if (javaParserSupport.verboseLevel > 2) {
            System.out.println("In visit (MethodDeclaration,ClassScope): isPrivate      = " + (isPrivate      ? "true" : "false"));
            System.out.println("In visit (MethodDeclaration,ClassScope): isSynchronized = " + (isSynchronized ? "true" : "false"));
            System.out.println("In visit (MethodDeclaration,ClassScope): isPublic       = " + (isPublic       ? "true" : "false"));
            System.out.println("In visit (MethodDeclaration,ClassScope): isProtected    = " + (isProtected    ? "true" : "false"));
            System.out.println("In visit (MethodDeclaration,ClassScope): isStrictfp     = " + (isStrictfp     ? "true" : "false"));
        }

        // We can build this here but we can't put the symbol into the symbol tabel until 
        // we have gathered the function parameter types so that the correct function type 
        // can be computed.
        JavaParser.cactionMethodDeclaration(name, javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (MethodDeclaration,ClassScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(MethodDeclaration node, ClassScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (MethodDeclaration,ClassScope)");

        if (node.statements == null || node.statements.length == 0) {
            if (javaParserSupport.verboseLevel > 1)
                System.out.println("    Side-visiting Method Declaration Header for " + node.getClass().getName());
            javaParserSupport.processMethodDeclarationHeader(node, javaParserSupport.createJavaToken(node));
        }

        JavaParser.cactionMethodDeclarationEnd(node.statements == null ? 0 : node.statements.length,
                                               javaParserSupport.createJavaToken(node));
    }

    
    public boolean enter( StringLiteralConcatenation node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (StringLiteralConcatenation,BlockScope)");

        JavaParser.cactionStringLiteralConcatenation(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (StringLiteralConcatenation,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(StringLiteralConcatenation node, BlockScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (StringLiteralConcatenation,BlockScope)");
    }


    public boolean enter(NormalAnnotation node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (NormalAnnotation,BlockScope)");

        JavaParser.cactionNormalAnnotation(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (NormalAnnotation,BlockScope)");

        return true;
    }

    public void exit(NormalAnnotation node, BlockScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (NormalAnnotation,BlockScope)");
    }


    public boolean enter(NullLiteral node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (NullLiteral,BlockScope)");

        JavaParser.cactionNullLiteral(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (NullLiteral,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(NullLiteral node, BlockScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (NullLiteral,BlockScope)");

    }


    public boolean enter(OR_OR_Expression node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (OR_OR_Expression,BlockScope)");

        JavaParser.cactionORORExpression(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (OR_OR_Expression,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(OR_OR_Expression node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of exit (OR_OR_Expression,BlockScope)");

        JavaParser.cactionORORExpressionEnd(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (OR_OR_Expression,BlockScope)");
    }


    public boolean enter(ParameterizedQualifiedTypeReference node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (ParameterizedQualifiedTypeReference,BlockScope)");

        JavaParser.cactionParameterizedTypeReference(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (ParameterizedQualifiedTypeReference,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(ParameterizedQualifiedTypeReference node, BlockScope scope) {
// TODO: remove this
/*
        StringBuffer strbuf = new StringBuffer();
        for (int i = 0; i < node.tokens.length; i++) {
            strbuf.append(node.tokens[i]);
            if (i + 1 < node.tokens.length)
                strbuf.append(".");
        }
        String qualifiedTypeName = new String(strbuf);
*/
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("At top of exit (ParameterizedQualifiedTypeReference,BlockScope) name = " + node.resolvedType.debugName());
        
        if (node.resolvedType.isClass() || node.resolvedType.isInterface()) { 
            if (javaParserSupport.verboseLevel > 0)
                System.out.println("(01) The parameterized qualified type referenced is bound to type " + node.resolvedType.debugName());
            javaParserSupport.preprocessClass(node.resolvedType);
        }

        String package_name = javaParserSupport.getPackageName(node.resolvedType);
        String  type_name = javaParserSupport.getTypeName(node.resolvedType);

        //
        // TODO: Do this right !!!  This is a temporary patch !!!
        //
        // PC:  I have no idea why the number of type arguments is not precise here!!!
        // What is the purpose of these null elements in this array !!!???
        //
        int num_type_arguments = 0;
        for (int i = 0; i < (node.typeArguments == null ? 0 : node.typeArguments.length); i++) {
            if (node.typeArguments[i] != null) 
                num_type_arguments++;
        }

        JavaParser.cactionParameterizedTypeReferenceEnd(package_name,
                                                        type_name,
                                                        num_type_arguments,
                                                        node.dimensions(),
                                                        javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (ParameterizedQualifiedTypeReference, BlockScope)");
    }


    public boolean enter(ParameterizedQualifiedTypeReference node, ClassScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (ParameterizedQualifiedTypeReference,ClassScope)");

        JavaParser.cactionParameterizedTypeReference(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (ParameterizedQualifiedTypeReference,ClassScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(ParameterizedQualifiedTypeReference node, ClassScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (ParameterizedQualifiedTypeReference,ClassScope)");

        if (node.resolvedType.isClass() || node.resolvedType.isInterface()) { 
            if (javaParserSupport.verboseLevel > 0)
                System.out.println("(01) The parameterized qualified type referenced is bound to type " + node.resolvedType.debugName());
            javaParserSupport.preprocessClass(node.resolvedType);
        }

        String package_name = javaParserSupport.getPackageName(node.resolvedType);
        String  type_name = javaParserSupport.getTypeName(node.resolvedType);
        
        //
        // TODO: Do this right !!!  This is a temporary patch !!!
        //
        // PC:  I have no idea why the number of type arguments is not precise here!!!
        // What is the purpose of these null elements in this array !!!???
        //
        int num_type_arguments = 0;
        for (int i = 0; i < (node.typeArguments == null ? 0 : node.typeArguments.length); i++) {
            if (node.typeArguments[i] != null) 
                num_type_arguments++;
        }

        JavaParser.cactionParameterizedTypeReferenceEnd(package_name,
                                                        type_name,
                                                        num_type_arguments,
                                                        node.dimensions(),
                                                        javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (ParameterizedQualifiedTypeReference,BlockScope)");
    }


    public boolean enter(ParameterizedSingleTypeReference node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (ParameterizedSingleTypeReference,BlockScope)");

        JavaParser.cactionParameterizedTypeReference(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (ParameterizedSingleTypeReference,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(ParameterizedSingleTypeReference node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("At top of exit (ParameterizedSingleTypeReference,BlockScope)");
/*
        String name = new String(node.token);

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("At top of exit (ParameterizedSingleTypeReference,BlockScope) name = " + name);

        // We need to find the qualified name for the associated type name (it should be unique).
        // This has to be handled on the Java side...

        String qualifiedTypeName = node.resolvedType.debugName();

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("At top of exit (ParameterizedSingleTypeReference,BlockScope) qualifiedTypeName = " + qualifiedTypeName);

        JavaParser.cactionParameterizedSingleTypeReferenceEnd(qualifiedTypeName,
                                                              node.typeArguments == null ? 0 : node.typeArguments.length,
                                                              javaParserSupport.createJavaToken(node));

        // TODO: if this type is an array, take care of the dimensions !!!
*/
        if (node.resolvedType.isClass() || node.resolvedType.isInterface()) { 
            if (javaParserSupport.verboseLevel > 0)
                System.out.println("(01) The parameterized single type referenced is bound to type " + node.resolvedType.debugName());
            javaParserSupport.preprocessClass(node.resolvedType);
        }

        String package_name = javaParserSupport.getPackageName(node.resolvedType);
        String  type_name = javaParserSupport.getTypeName(node.resolvedType);
        
        //
        // TODO: Do this right !!!  This is a temporary patch !!!
        //
        // PC:  I have no idea why the number of type arguments is not precise here!!!
        // What is the purpose of these null elements in this array !!!???
        //
        int num_type_arguments = 0;
        for (int i = 0; i < (node.typeArguments == null ? 0 : node.typeArguments.length); i++) {
            if (node.typeArguments[i] != null) 
                num_type_arguments++;
        }

        JavaParser.cactionParameterizedTypeReferenceEnd(package_name,
                                                        type_name,
                                                        num_type_arguments,
                                                        node.dimensions(),
                                                        javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (ParameterizedSingleTypeReference,BlockScope)");
    }


    public boolean enter(ParameterizedSingleTypeReference node, ClassScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (ParameterizedSingleTypeReference,ClassScope)");

        JavaParser.cactionParameterizedTypeReference(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (ParameterizedSingleTypeReference,ClassScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(ParameterizedSingleTypeReference node, ClassScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (ParameterizedSingleTypeReference,ClassScope)");

        if (node.resolvedType.isClass() || node.resolvedType.isInterface()) { 
            if (javaParserSupport.verboseLevel > 0)
                System.out.println("(01) The parameterized single type referenced is bound to type " + node.resolvedType.debugName());
            javaParserSupport.preprocessClass(node.resolvedType);
        }

        String package_name = javaParserSupport.getPackageName(node.resolvedType);
        String  type_name = javaParserSupport.getTypeName(node.resolvedType);
        
        //
        // TODO: Do this right !!!  This is a temporary patch !!!
        //
        // PC:  I have no idea why the number of type arguments is not precise here!!!
        // What is the purpose of these null elements in this array !!!???
        //
        int num_type_arguments = 0;
        for (int i = 0; i < (node.typeArguments == null ? 0 : node.typeArguments.length); i++) {
            if (node.typeArguments[i] != null) 
                num_type_arguments++;
        }

        JavaParser.cactionParameterizedTypeReferenceEnd(package_name,
                                                        type_name,
                                                        num_type_arguments,
                                                        node.dimensions(),
                                                        javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (ParameterizedSingleTypeReference,ClassScope)");
    }


    public boolean enter(PostfixExpression node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (PostfixExpression,BlockScope)");

        JavaParser.cactionPostfixExpression(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (PostfixExpression,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(PostfixExpression node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of exit (PostfixExpression,BlockScope)");

        int operator_kind = node.operator;

        JavaParser.cactionPostfixExpressionEnd(operator_kind, javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (PostfixExpression,BlockScope)");
    }


    public boolean enter(PrefixExpression node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (PrefixExpression,BlockScope)");

        JavaParser.cactionPrefixExpression(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (PrefixExpression,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(PrefixExpression node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of exit (PrefixExpression,BlockScope)");

        int operator_kind = node.operator;

        JavaParser.cactionPrefixExpressionEnd(operator_kind, javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (PrefixExpression,BlockScope)");
    }


    public boolean enter(QualifiedAllocationExpression node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (QualifiedAllocationExpression,BlockScope)");

        JavaParser.cactionQualifiedAllocationExpression(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (QualifiedAllocationExpression,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(QualifiedAllocationExpression node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (QualifiedAllocationExpression,BlockScope)");

        if (javaParserSupport.verboseLevel > 0) 
            System.out.println("The Allocation type is bound to type " + node.type.resolvedType.debugName());

        javaParserSupport.preprocessClass(node.type.resolvedType);
        
        JavaParser.cactionQualifiedAllocationExpressionEnd(node.type.toString(),
                                                           node.enclosingInstance != null,
                                                           node.arguments == null ? 0 : node.arguments.length,
                                                           node.anonymousType != null,
                                                           javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (QualifiedAllocationExpression,BlockScope)");
    }


    public boolean enter(QualifiedNameReference node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (QualifiedNameReference, BlockScope)");

        javaParserSupport.processQualifiedNameReference(node);

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (QualifiedNameReference, BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(QualifiedNameReference node, BlockScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (QualifiedNameReference,BlockScope)");
    }


    public boolean enter(QualifiedNameReference node, ClassScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (QualifiedNameReference, ClassScope)");

        javaParserSupport.processQualifiedNameReference(node);

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (QualifiedNameReference, ClassScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(QualifiedNameReference node, ClassScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (QualifiedNameReference,ClassScope)");
    }


    public boolean enter(QualifiedSuperReference node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (QualifiedSuperReference,BlockScope)");

        JavaParser.cactionQualifiedSuperReference(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (QualifiedSuperReference,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(QualifiedSuperReference node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of exit(QualifiedSuperReference,BlockScope)");

        JavaParser.cactionQualifiedSuperReferenceEnd(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (QualifiedSuperReference,BlockScope)");
    }


    public boolean enter(QualifiedSuperReference node, ClassScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (QualifiedSuperReference,ClassScope)");

        JavaParser.cactionQualifiedSuperReferenceClassScope(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (QualifiedSuperReference,ClassScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(QualifiedSuperReference node, ClassScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of exit(QualifiedSuperReference,ClassScope)");

        JavaParser.cactionQualifiedSuperReferenceClassScopeEnd(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (QualifiedSuperReference,ClassScope)");
    }


    public boolean enter(QualifiedThisReference node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (QualifiedThisReference,BlockScope)");

        JavaParser.cactionQualifiedThisReference(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (QualifiedThisReference,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(QualifiedThisReference node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of exit(QualifiedThisReference,BlockScope)");

        JavaParser.cactionQualifiedThisReferenceEnd(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (QualifiedThisReference,BlockScope)");
    }


    public boolean enter(QualifiedThisReference node, ClassScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (QualifiedThisReference,ClassScope)");

        JavaParser.cactionQualifiedThisReferenceClassScope(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (QualifiedThisReference,ClassScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(QualifiedThisReference node, ClassScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of exit(QualifiedThisReference,ClassScope)");

        JavaParser.cactionQualifiedThisReferenceClassScopeEnd(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (QualifiedThisReference,ClassScope)");
    }


    public boolean enter(QualifiedTypeReference node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (QualifiedTypeReference,BlockScope)");

        if (node.resolvedType.isClass() || node.resolvedType.isInterface()) {
            if (javaParserSupport.verboseLevel > 0)
                System.out.println("The qualified type referenced is bound to type " + node.resolvedType.debugName());        
            javaParserSupport.preprocessClass(node.resolvedType);
        }

        String package_name = javaParserSupport.getPackageName(node.resolvedType);
        String  type_name = javaParserSupport.getTypeName(node.resolvedType);

        JavaParser.cactionTypeReference(package_name, type_name, node.resolvedType instanceof TypeVariableBinding, javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (QualifiedTypeReference,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(QualifiedTypeReference node, BlockScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (QualifiedTypeReference,BlockScope)");
    }

 
    public boolean enter(QualifiedTypeReference node, ClassScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (QualifiedTypeReference,ClassScope)");

        if (node.resolvedType.isClass() || node.resolvedType.isInterface()) {
            if (javaParserSupport.verboseLevel > 0)
                System.out.println("The qualified type referenced is bound to type " + node.resolvedType.debugName());        
            javaParserSupport.preprocessClass(node.resolvedType);
        }

        String package_name = javaParserSupport.getPackageName(node.resolvedType);
        String  type_name = javaParserSupport.getTypeName(node.resolvedType);
        
        JavaParser.cactionTypeReference(package_name, type_name, node.resolvedType instanceof TypeVariableBinding, javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (QualifiedTypeReference,ClassScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(QualifiedTypeReference node, ClassScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (QualifiedTypeReference,ClassScope)");
    }


    public boolean enter(ReturnStatement node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (ReturnStatement,BlockScope)");

        JavaParser.cactionReturnStatement(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (ReturnStatement,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(ReturnStatement node, BlockScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (ReturnStatement,BlockScope)");

        JavaParser.cactionReturnStatementEnd(node.expression != null, javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (ReturnStatement,BlockScope)");
    }


    public boolean enter(SingleMemberAnnotation node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (SingleMemberAnnotation,BlockScope)");

        JavaParser.cactionSingleMemberAnnotation(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (SingleMemberAnnotation,BlockScope)");

        return true;
    }

    public void exit(SingleMemberAnnotation node, BlockScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (SingleMemberAnnotation,BlockScope)");
    }


    public boolean enter(SingleNameReference node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (SingleNameReference,BlockScope)");

        enterSingleNameReference(node);

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (SingleNameReference,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(SingleNameReference node, BlockScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (SingleNameReference,BlockScope)");
    }


    public boolean enter(SingleNameReference node, ClassScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (SingleNameReference,ClassScope)");

        enterSingleNameReference(node);

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (SingleNameReference,ClassScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(SingleNameReference node, ClassScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (SingleNameReference,ClassScope)");
    }


    public boolean enter(SingleTypeReference node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (SingleTypeReference,BlockScope)");

        if (node.resolvedType.isClass() || node.resolvedType.isInterface()) { 
            if (javaParserSupport.verboseLevel > 0)
                System.out.println("(1) The single type referenced is bound to type " + node.resolvedType.debugName());
            javaParserSupport.preprocessClass(node.resolvedType);
        }

        String package_name = javaParserSupport.getPackageName(node.resolvedType);
        String  type_name = javaParserSupport.getTypeName(node.resolvedType);
        
        JavaParser.cactionTypeReference(package_name, type_name, node.resolvedType instanceof TypeVariableBinding, javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (SingleTypeReference,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(SingleTypeReference node, BlockScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (SingleTypeReference,BlockScope)");
    }


    public boolean enter(SingleTypeReference node, ClassScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (SingleTypeReference,BlockScope)");

        if (node.resolvedType.isClass() || node.resolvedType.isInterface()) {
            if (javaParserSupport.verboseLevel > 0)
                System.out.println("(2) The single type referenced is bound to type " + node.resolvedType.debugName());        
            javaParserSupport.preprocessClass(node.resolvedType);
        }

        String package_name = javaParserSupport.getPackageName(node.resolvedType);
        String  type_name = javaParserSupport.getTypeName(node.resolvedType);
        
        JavaParser.cactionTypeReference(package_name, type_name, node.resolvedType instanceof TypeVariableBinding, javaParserSupport.createJavaToken(node));
          
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (SingleTypeReference,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(SingleTypeReference node, ClassScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (SingleTypeReference,ClassScope)");
    }


    public boolean enter(StringLiteral node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (StringLiteral,BlockScope)");

        // System.out.println("Inside of enter (StringLiteral,BlockScope): node = " + node);
        String literal = new String(node.source());

        JavaParser.cactionStringLiteral(literal, javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (StringLiteral,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(StringLiteral node, BlockScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (StringLiteral,BlockScope)");
    }


    public boolean enter(SuperReference node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (SuperReference,BlockScope)");

        JavaParser.cactionSuperReference(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (SuperReference,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(SuperReference node, BlockScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (SuperReference,BlockScope)");
    }


    public boolean enter(SwitchStatement node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (SwitchStatement,BlockScope)");

        JavaParser.cactionSwitchStatement(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (SwitchStatement,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(SwitchStatement node, BlockScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (SwitchStatement,BlockScope)");

        JavaParser.cactionSwitchStatementEnd(node.caseCount, node.defaultCase != null, javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (SwitchStatement,BlockScope)");
    }


    public boolean enter(SynchronizedStatement node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (SynchronizedStatement,BlockScope)");

        JavaParser.cactionSynchronizedStatement(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (SynchronizedStatement,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(SynchronizedStatement node, BlockScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (SynchronizedStatement,BlockScope)");

        JavaParser.cactionSynchronizedStatementEnd(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (SynchronizedStatement,BlockScope)");
    }


    public boolean enter(ThisReference node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (ThisReference,BlockScope)");

        JavaParser.cactionThisReference(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (ThisReference,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(ThisReference node, BlockScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (ThisReference,BlockScope)");
    }


    public boolean enter(ThisReference node, ClassScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (ThisReference,ClassScope)");

        JavaParser.cactionThisReferenceClassScope(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (ThisReference,ClassScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(ThisReference node, ClassScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (ThisReference,ClassScope)");
    }


    public boolean enter(ThrowStatement node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (ThrowStatement,BlockScope)");

        JavaParser.cactionThrowStatement(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (ThrowStatement,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(ThrowStatement node, BlockScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (ThrowStatement,BlockScope)");

        JavaParser.cactionThrowStatementEnd(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (ThrowStatement,BlockScope)");
    }


    public boolean enter(TrueLiteral node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (TrueLiteral,BlockScope)");

        JavaParser.cactionTrueLiteral(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (TrueLiteral,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(TrueLiteral node, BlockScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (TrueLiteral,BlockScope)");
    }


    public boolean enter(TryStatement node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (TryStatement,BlockScope)");

        //
        // charles4 (09/26/2011): Keep track of the set of Arguments and the set of Blocks that 
        // make up the catch blocks. 
        //
        if (node.catchArguments != null) {
            for (int i = 0; i < node.catchBlocks.length; i++) {
                catchArguments.add(node.catchArguments[i]);
                catchBlocks.add(node.catchBlocks[i]);
            }
        }
          
        JavaParser.cactionTryStatement(node.catchArguments == null ? 0 : node.catchBlocks.length, node.finallyBlock != null, javaParserSupport.createJavaToken(node));
          
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (TryStatement,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(TryStatement node, BlockScope scope) {
        // do nothing  by default
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("exit TryStatement -- BlockScope");

        JavaParser.cactionTryStatementEnd(node.catchArguments == null ? 0 : node.catchBlocks.length, node.finallyBlock != null, javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (TryStatement,BlockScope)");
    }

    //
    // Local and Anonymous types are processed here.
    //
    public boolean enter(TypeDeclaration node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("visit TypeDeclaration -- BlockScope");

        assert(node.binding instanceof LocalTypeBinding); 

        //
        // First, preprocess the symbol table information of the type in question.
        //
        JavaParserSupport.LocalOrAnonymousType special_type = javaParserSupport.localOrAnonymousType.get(node);

        Class cls = special_type.cls;
        if (node.binding.isAnonymousType()) {
            JavaParser.cactionPushPackage(special_type.package_name, javaParserSupport.createJavaToken(node));
            javaParserSupport.insertClasses(cls);
            javaParserSupport.traverseClass(cls);
            JavaParser.cactionPopPackage();
        }
        else {
            javaParserSupport.insertClasses(cls);
            javaParserSupport.traverseClass(cls);
        }

        enterTypeDeclaration(node);
        
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter(TypeDeclaration,BlockScope)");

        return false; // The traversal is done in enterTypeDeclatation(node) in the user-specified order of the class members
    }

    public void exit(TypeDeclaration node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("exit TypeDeclaration -- BlockScope");

        exitTypeDeclaration(node);

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (TypeDeclaration,BlockScope)");
    }


    public boolean enter(TypeDeclaration node, ClassScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("visit TypeDeclaration -- ClassScope");

        enterTypeDeclaration(node);

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter(TypeDeclaration,ClassScope)");

        return false; // The traversal is done in enterTypeDeclatation(node) in the user-specified order of the class members
    }

    public void exit(TypeDeclaration node, ClassScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("exit TypeDeclaration -- ClassScope");

        exitTypeDeclaration(node);

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (TypeDeclaration,ClassScope)");
    }


    public boolean enter(TypeDeclaration node, CompilationUnitScope scope) {
        // System.out.println("visit TypeDeclaration -- CompilationUnitScope");
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter(TypeDeclaration, CompilationUnitScope)");

        enterTypeDeclaration(node);

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter(TypeDeclaration, CompilationUnitScope)");

        return false; // The traversal is done in enterTypeDeclatation(node) in the user-specified order of the class members
    }

    public void exit(TypeDeclaration node, CompilationUnitScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of exit (TypeDeclaration,CompilationUnitScope)");

        exitTypeDeclaration(node);

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (TypeDeclaration,CompilationUnitScope)");
    }


    public boolean enter(TypeParameter node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (TypeParameter,BlockScope)");

        JavaParser.cactionTypeParameter(node.binding.debugName(), javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (TypeParameter,BlockScope)");

        return true;
    }

    public void exit(TypeParameter node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of exit (TypeParameter,BlockScope)");

        JavaParser.cactionTypeParameterEnd(node.binding.debugName(), node.binding.boundsCount(), javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (TypeParameter,BlockScope)");
    }


    public boolean enter(TypeParameter node, ClassScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (TypeParameter,ClassScope)");

        JavaParser.cactionTypeParameter(node.binding.debugName(), javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (TypeParameter,ClassScope)");

        return true;
    }

    public void exit(TypeParameter node, ClassScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside exit (TypeParameter,ClassScope)");

        JavaParser.cactionTypeParameterEnd(node.binding.debugName(), node.binding.boundsCount(), javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (TypeParameter,ClassScope)");
    }

    public boolean enter(UnaryExpression node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (UnaryExpression,BlockScope)");

        JavaParser.cactionUnaryExpression(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (UnaryExpression,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(UnaryExpression node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of exit (UnaryExpression,BlockScope)");

        // Not clear what the valueRequired filed means.
        int operator_kind = (node.bits & ASTNode.OperatorMASK) >> ASTNode.OperatorSHIFT;

        JavaParser.cactionUnaryExpressionEnd(operator_kind, javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (UnaryExpression,BlockScope)");
    }


    public boolean enter(WhileStatement node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (WhileStatement,BlockScope)");

        JavaParser.cactionWhileStatement(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(WhileStatement node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of exit (WhileStatement,BlockScope)");

        JavaParser.cactionWhileStatementEnd(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving exit (,BlockScope)");
    }


    public boolean enter(Wildcard node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (Wildcard,BlockScope)");

        JavaParser.cactionWildcard(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (Wildcard,BlockScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(Wildcard node, BlockScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of exit (Wildcard,BlockScope)");
    }


    public boolean enter(Wildcard node, ClassScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of enter (Wildcard,ClassScope)");

        JavaParser.cactionWildcardClassScope(javaParserSupport.createJavaToken(node));

        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Leaving enter (Wildcard,ClassScope)");

        return true; // do nothing by node, keep traversing
    }

    public void exit(Wildcard node, ClassScope scope) {
        if (javaParserSupport.verboseLevel > 0)
            System.out.println("Inside of exit (Wildcard,ClassScope)");
    }
}
