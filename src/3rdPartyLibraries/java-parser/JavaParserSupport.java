import org.eclipse.jdt.internal.compiler.batch.*;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;

import java.io.*;
import java.text.*;
import java.util.*;

import org.eclipse.jdt.core.compiler.*;
import org.eclipse.jdt.core.compiler.batch.*;
import org.eclipse.jdt.internal.compiler.*;
import org.eclipse.jdt.internal.compiler.Compiler;
import org.eclipse.jdt.internal.compiler.env.*;
import org.eclipse.jdt.internal.compiler.impl.*;
import org.eclipse.jdt.internal.compiler.ast.*;
import org.eclipse.jdt.internal.compiler.lookup.*;
import org.eclipse.jdt.internal.compiler.parser.*;
import org.eclipse.jdt.internal.compiler.problem.*;
import org.eclipse.jdt.internal.compiler.util.*;

// DQ (10/30/2010): Added support for reflection to get methods in implicitly included objects.
import java.lang.reflect.*;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

// DQ (11/1/2010): This improved design separates out the parsing support, from the ECJ AST traversal, and the parser.
class JavaParserSupport {
    public int verboseLevel = 0;

    //
    // Keep track of the position factory for the unit being processed.
    //
    private JavaSourcePositionInformationFactory posFactory = null;

    public JavaToken createJavaToken(ASTNode node) {
        JavaSourcePositionInformation pos = this.posFactory.createPosInfo(node);
        // For now we return dummy text
        return new JavaToken("Dummy JavaToken (see createJavaToken)", pos);
    }

// TODO: Remove this -It's not needed    
//    public JavaToken createJavaToken(AbstractMethodDeclaration node) {
//        // System.out.println("Create JAVA TOKEN FOR METHOD BODY"); 
//        JavaSourcePositionInformation pos = getPosInfoFactory().createPosInfo(node);
//        // For now we return dummy text
//        return new JavaToken("Dummy JavaToken (see createJavaToken)", pos);
//    }


    //
    // Create a loader for finding classes.
    //
    public ClassLoader pathLoader = null;

    //
    // Create a map to keep track of user-defined type declarations - This 
    // is a map from a Class file to the TypdeDeclaration that produced it.
    //
    public HashMap<Class, TypeDeclaration> userTypeTable = null;
 
    //
    // Create a symbolTable map to keep track of packages and types that have
    // already been encountered. This is a map from a package name to a map
    // that maps a type name to its corresponding class.
    //
    public HashMap<String, HashMap<String, Class>> symbolTable = null;

    //
    // Create a map from user-defined types into an array list of the class members
    // of the type in question sorted by the order in which they were specified.
    //
    public HashMap<TypeDeclaration, ASTNode[]> orderedClassMembers;

    //
    // Map each initializer into a unique name.
    //
    public HashMap<Initializer, String> initializerName;
        
    public JavaParserSupport(String classpath, int input_verbose_level) {
        // Set the verbose level for ROSE specific processing on the Java specific ECJ/ROSE translation.
        this.verboseLevel = input_verbose_level;

        // Reinitialize the type and symbol table for this compilation unit.
        this.userTypeTable = new HashMap<Class, TypeDeclaration>();
        this.symbolTable = new HashMap<String, HashMap<String, Class>>();
        this.orderedClassMembers = new HashMap<TypeDeclaration, ASTNode[]>();
        this.initializerName = new HashMap<Initializer, String>();

        //
        // Now process the classpath 
        //
        ArrayList<File> files = new ArrayList<File>();
        while(classpath.length() > 0) {
            int index = classpath.indexOf(':');
            if (index == -1) {
                files.add(new File(classpath));
                classpath = "";
            }
            else {
                String filename = classpath.substring(0, index);
                files.add(new File(filename));
                classpath = classpath.substring(index + 1);
            }
        }

        // Always add the current directory, ".", to the classpath
//        files.add(new File("."));

        //
        // Now create a new class loader with the classpath.
        //
        try {
            // Convert File to a URL
            URL[] urls = new URL[files.size() + 1];
            for (int i = 0; i < files.size(); i++) {
                urls[i] = files.get(i).toURI().toURL();
            }

            // Create a new class loader with the directories
            this.pathLoader = new URLClassLoader(urls, ClassLoader.getSystemClassLoader());
        } catch (MalformedURLException e) {
            System.err.println("(3) Error in processClasspath: " + e.getMessage()); 
            System.exit(1);
        }

/*        
        class LocalClassLoader extends URLClassLoader {  
            public LocalClassLoader(URL[] urls) {  
                super(urls);  
            }  
              
            public void addURL(URL url) {  
                super.addURL(url);  
            }  
           
        }

        //
        // Now process the classpath 
        //
        URLClassLoader loader = (URLClassLoader) ClassLoader.getSystemClassLoader();  
        ArrayList<URL> urls = new ArrayList<URL>();
        try {
            while(classpath.length() > 0) {
                String filename;
                int index = classpath.indexOf(':');
                if (index == -1) {
                    filename = classpath;
                    classpath = "";
                }
                else {
                    filename = classpath.substring(0, index);
                    classpath = classpath.substring(index + 1);
                }

                File file = new File(filename);
                if (file.isDirectory()) {
System.out.println("Adding url directory " + file.getAbsolutePath() + " to the class loader");                                
                    urls.add(file.toURI().toURL());
                }
                else { // it must be a Jar file!
System.out.println("Adding url jar file " + file.getAbsolutePath() + " to the class loader");
                    urls.add(new URL("file:" + file.getAbsolutePath()));  // urls.add(new URL("jar", "","file:" + file.getAbsolutePath()+"!/"));    
                }
            }

            // Always add the current directory, ".", to the classpath
            urls.add(new File(".").toURI().toURL());
        }
        catch (MalformedURLException e) {
            e.printStackTrace();
            System.err.println("(3) Error in processClasspath: " + e.getMessage()); 
            System.exit(1);
        }

        //
        // Now create a new class loader with the directories from the classpath.
        //
        this.pathLoader = new URLClassLoader(urls.toArray(new URL[urls.size()]));
*/        
    }

    public boolean typeExists(String package_name, String type_name) {
        return (symbolTable.containsKey(package_name) ? symbolTable.get(package_name).containsKey(type_name) : false); 
    }

    public boolean typeExists(String type_name) {
        return typeExists("", type_name); 
    }

    public void insertType(String package_name, String type_name) {
        assert (! (symbolTable.containsKey(package_name) && symbolTable.get(package_name).containsKey(type_name))); 
        if (! symbolTable.containsKey(package_name)) {
            symbolTable.put(package_name, new HashMap<String, Class>());
        }
        symbolTable.get(package_name).put(type_name, null); 
    }
    
    public void insertType(String package_name, Class type) {
        String type_name = new String(type.getSimpleName());
        assert (! (symbolTable.containsKey(package_name) && symbolTable.get(package_name).containsKey(type_name))); 
        if (! symbolTable.containsKey(package_name)) {
            symbolTable.put(package_name, new HashMap<String, Class>());
        }
        symbolTable.get(package_name).put(type_name, type); 
    }

/*
    public static void insertType(TypeDeclaration type) {
        insertType("", type); 
    }
    

    public static void processType(String package_name, TypeDeclaration type) {
        String type_name = new String(type.name);
        if (! typeExists(package_name, type_name)) {
            insertType(package_name, type);
    
            cactionPushPackage(package_name);
            traverseType(type);
            cactionPopPackage();
        }
    }
*/

    Class getClassForName(String typename) {
        Class cls = null;
        try {
            cls = Class.forName(typename, true, pathLoader);
        }
        catch (Throwable e) { // Note that this should have been:  catch (ClassNotFoundException e) ...
            //
            // As stated above, the forName function throws the exception ClassNotFoundException. Thus, we
            // should really have specified the following catch statement for it:
            //
            //         catch (ClassNotFoundException e) {
            //
            // However, in many instances, the function forName() throws a NullPointerException (why???) which
            // causes the program to crash.  Since we are interested only in knowing whether or not the typename
            // in question is associated with a class that is retrievable by the loader, we simply try to catch
            // any Throwable exception.
            //
            if (verboseLevel > 0) {
                System.out.println("Class " + typename + " was not found");
                System.out.println("(1) Caught error in JavaParserSupport (Parser failed)");
                System.err.println(e);
            }
        }

        return cls;
    }


    /**
     * Quicksort the content of this array in the range low..high.
     *
     * NOTE that the reason why I wrote this function instead of invoking Collection.sort(...)
     * on it is because Collection.sort() invokes Array.sort() which returns a sorted list of
     * clones of the array elements instead of the originals in sorted order.  Since we have
     * several maps that are defined on these nodes, we need to sort them directly and not
     * their clones!!!
     *
     * Low - low index of range to sort.
     * high - high index of range to sort.
     */
    private static void quicksort(ASTNode array[], int low, int high) {
        if (low >= high)
            return;

        ASTNode pivot = array[low];
        int i = low;
        for (int j = low + 1; j <= high; j++) {
            if (array[j].sourceStart < pivot.sourceStart) {
                array[i] = array[j];
                i++;
                array[j] = array[i];
            }
        }
        array[i] = pivot;

        quicksort(array, low, i - 1);
        quicksort(array, i + 1, high);
    }

    /**
     * 
     */
    void identifyUserDefinedTypes(Class cls, TypeDeclaration node) {
        userTypeTable.put(cls, node);   // keep track of user-defined TypeDeclarations

        //
        // First, sort the class members based on the order in which they were specified.
        //
        int num_member_types = (node.memberTypes != null ? node.memberTypes.length : 0),
            num_fields = (node.fields != null ? node.fields.length : 0),
            num_methods = (node.methods != null ? node.methods.length : 0);
        ASTNode node_list[] = new ASTNode[num_member_types + num_fields + num_methods];
        int index = 0;
        for (int i = 0; i < num_member_types; i++) {
            node_list[index++] = node.memberTypes[i];
        }
        for (int i = 0; i < num_fields; i++) {
            node_list[index++] = node.fields[i];
        }
        for (int i = 0; i < num_methods; i++) {
            AbstractMethodDeclaration method = (AbstractMethodDeclaration) node.methods[i];
            node_list[index++] = method;
        }

        quicksort(node_list, 0, node_list.length - 1);
        orderedClassMembers.put(node, node_list);

        //
        // If this type contains inner classes, process them. 
        //
        for (int i = 0; i < num_member_types; i++) { // for each inner type of this type ...
            String typename = new String(node.memberTypes[i].name);
            Class inner[] = cls.getDeclaredClasses();
            int k;
            for (k = 0; k < inner.length; k++) { // ... look for its matching counterpart.
                if (inner[k].getSimpleName().equals(typename)) {
                    identifyUserDefinedTypes(inner[k], node.memberTypes[i]);
                    break;
                }
            }
            assert(k < inner.length);
        }
    }
    
    /**
     * 
     * 
     */
    void identifyUserDefinedTypes(String prefix, TypeDeclaration node) {
        String typename = prefix + (prefix.length() > 0 ? "." : "") + new String(node.name);
// TODO: REMOVE THIS
//System.out.println("Preprocessing type " + typename);
//
//if (node.superclass != null){
//String name = new String(node.superclass.resolvedType.getPackage().readableName()),
//sname = node.superclass.resolvedType.debugName().substring(name.length() + 1);
//
//System.out.println("** The package is: " + name + ";  The class name is " + sname + "; The super class is " + name + "." + sname);
//}

        try {
            Class cls = getClassForName(typename);
            if (cls == null) throw new ClassNotFoundException(typename);
            String canonical_name = cls.getCanonicalName(),
                   class_name = cls.getName(),
                   simple_name = cls.getSimpleName(),
                   class_package = (simple_name.length() < canonical_name.length()
                                          ? canonical_name.substring(0, canonical_name.length() - simple_name.length() -1)
                                          : "");
// TODO: REMOVE THIS
//System.out.println("(1) The canonical name is: " + canonical_name +
//                   "; The prefix is: " + prefix +
//                   "; The typename is: " + typename +
//                   "; The class package is: " + class_package +
//                   ";  The class name is " + class_name +
//                   ";  The simple class name is " + simple_name);

            assert(cls.getEnclosingClass() == null); // not an inner class
//            insertType(class_package, cls); // keep track of top-level classes that have been seen
            identifyUserDefinedTypes(cls, node);
        }
        catch (ClassNotFoundException e) {
            e.printStackTrace();
            System.out.println("(2) Caught error in JavaParserSupport (Parser failed) - " + typename);
            System.err.println(e);

            System.exit(1);
        }
    }

    /**
     * @param unit
     * 
     */
    public void preprocess(CompilationUnitDeclaration unit) {
        this.posFactory = new JavaSourcePositionInformationFactory(unit);

        //
        // Make sure that Object is processed first!
        //
        if (! typeExists("java.lang", "Object")) {
            preprocessClass("java.lang.Object");
// TODO: REMOVE THIS
/*            
            insertType("java.lang", Object.class);
System.out.println("Inserting type " + "java.lang.Object");
            JavaParser.cactionGenerateType("java.lang.Object", 0);
            JavaParser.cactionTypeReference("java.lang.Object", new JavaToken("Dummy JavaToken (see createJavaToken)", new JavaSourcePositionInformation(0)));
            JavaParser.cactionProcessObject();
*/
            JavaParser.cactionSetupStringAndClassTypes();
        }
/*
        //
        //
        //
        
        //
        //
        //
        if (unit.imports != null) {
            for (int k = 0; k < unit.imports.length; k++) {
                ImportReference node = unit.imports[k];

                // DQ (9/11/2011): Static analysis tools suggest using StringBuffer instead of String.
                // String importReference = "";
                StringBuffer package_buffer = new StringBuffer();
                for (int i = 0, tokenArrayLength = node.tokens.length; i < tokenArrayLength; i++) {
                    String tokenString = new String(node.tokens[i]);
                    if (i > 0) {
                        package_buffer.append('.');
                    }
                    package_buffer.append(tokenString);
                }

                boolean containsWildcard = ((node.bits & node.OnDemand) != 0);
// TODO: REMOVE THIS
//System.out.println("The import statement " + package_buffer + (containsWildcard ? " contains wildcard" : " does not contain wilcards"));
                String importReferenceWithoutWildcard = package_buffer.toString();
                if (containsWildcard) {
                    package_buffer.append(".*");
                }

                if (verboseLevel > 1)
                    System.out.println("importReference (string) = " + package_buffer.toString());

                // DQ (8/22/2011): Read the referenced class or set of classes defined by the import statement.
                if (! containsWildcard) { // Build support for a real class
                    preprocessClass(importReferenceWithoutWildcard);
//                    JavaParserSupport.buildImplicitClassSupport(importReferenceWithoutWildcard);
                }
                //
                // TODO: REMOVE THIS!!! shpuld not be needed,
                /*
                else { // just prime the system for an "import ... .*;"
                    //              JavaParser.cactionLookupClassType(importReferenceWithoutWildcard);
                    JavaParser.cactionBuildImplicitClassSupportStart(importReferenceWithoutWildcard);
                    JavaParser.cactionBuildImplicitClassSupportEnd(0, importReferenceWithoutWildcard);
                }
                */
        /*
            }
        }
*/

        //
        //
        //
        String package_name = "";
        if (unit.currentPackage != null) {
            ImportReference importReference = unit.currentPackage;
            StringBuffer package_name_buffer = new StringBuffer();
            for (int i = 0, tokenArrayLength = importReference.tokens.length; i < tokenArrayLength; i++) {
                String tokenString = new String(importReference.tokens[i]);
                if (i > 0) {
                    package_name_buffer.append('.');
                }
                package_name_buffer.append(tokenString);
            }
            package_name = package_name_buffer.toString();
        }

        /*
        if (unit.imports != null) {
            for (int k = 0; k < unit.imports.length; k++) {
                ImportReference node = unit.imports[k];
                Class cls = null;
                String name_suffix = "";

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
                    Class c = preprocessClass(import_reference.toString());
                    if (c != null) { // a class?
                        cls = c; // save the latest class we've found.
                    }
                    else if (cls != null){ // already found a class, so this name is a suffix.
                        name_suffix += token_string;
                    }
                }
        
            }
        }
        */
        
        //
        //
        //
        if (unit.types == null) { // No units!?  ... then at least process the package.
            JavaParser.cactionPushPackage(package_name);
            JavaParser.cactionPopPackage();
        }
        else {
            for (int i = 0; i < unit.types.length; i++) {
                TypeDeclaration node = unit.types[i];
                identifyUserDefinedTypes(package_name, node);
            }
            
            for (Class cls : userTypeTable.keySet()) {
                if (cls.getEnclosingClass() == null) { // a top-level class?
// TODO: REMOVE THIS
//
//                    insertClasses(cls);
//                    traverseClass(cls);
                    preprocessClass(cls);
                }
            }
/*
            for (int i = 0; i < unit.types.length; i++) {
                TypeDeclaration node = unit.types[i];
                String typename = package_name + (package_name.length() > 0 ? "." : "") + new String(node.name);
System.out.println("Preprocessing type " + typename);

String name = new String(node.superclass.resolvedType.getPackage().readableName()),
sname = node.superclass.resolvedType.debugName().substring(name.length() + 1);

System.out.println("** The package is: " + name + ";  The class name is " + sname + "; The super class is " + name + "." + sname);

// Replace this simple function call by the code below it because we don't know how to process packages for
// source code yet in ROSE... The problem is that ROSE is file-centric...
//
//                preprocessClass(typename);
//
                try {
                    Class cls = getClassForName(typename);
                    if (cls == null) throw new ClassNotFoundException(typename);                    
                    String canonical_name = cls.getCanonicalName(),
                           class_name = cls.getName(),
                           simple_name = cls.getSimpleName(),
                           class_package = (simple_name.length() < canonical_name.length()
                                                ? canonical_name.substring(0, canonical_name.length() - simple_name.length() -1)
                                                : "");
System.out.println("(1) The canonical name is: " + canonical_name +
                   "; The package is: " + package_name + ";  The class name is " + class_name + ";  The simple class name is " + simple_name);

                    assert(cls.getEnclosingClass() == null);
                    insertType(class_package, cls);
System.out.println("Inserting type " + canonical_name);
                    insertClasses(cls);
                    traverseClass(cls);
                }
                catch (ClassNotFoundException e) {
                    System.out.println("Caught error in JavaParserSupport (Parser failed)");
                    System.err.println(e);

                    System.exit(1);
                 }
             }
             */
        }    
    }
    
    // DQ (8/24/2011): This is the support for information on the Java side that 
    // we would know on the C++ side, but require on the Java side to support the 
    // introspection (which requires fully qualified names).
    // We use a hashmap to store fully qualified names associated with class names.
    // This is used to translate class names used in type references into fully
    // qualified names where they are implicit classes and we require introspection 
    // to support reading them to translate their member data dn function into JNI
    // calls that will force the ROSE AST to be built.
    // private static HashMap<String,String> hashmapOfQualifiedNamesOfClasses;
//    public static HashMap<String,String> hashmapOfQualifiedNamesOfClasses;

/*
    // Initialization function, but be called before we can use member functions in this class.
    public static void initialize(CompilationResult x, int input_verboseLevel) {
        // This has to be set first (required to support source position translation).
        rose_compilationResult = x;

        // DQ (8/24/2011): Added hashmap to support mapping of unqualified class names to qualified class names.
        hashmapOfQualifiedNamesOfClasses = new HashMap<String,String>();

        // Set the verbose level (passed in from ROSE's "-rose:verbose n")
        verboseLevel = input_verboseLevel;
    }

    public static void sourcePosition(ASTNode node) {
        // The source positon (line and comun numbers) can be computed within ECJ. 
        // This is an example of how to do it.

        // We need the CompilationResult which is stored in the CompilationUnit (as I recall).
        assert rose_compilationResult != null : "rose_compilationResult not initialized";

        int startingSourcePosition = node.sourceStart();
        int endingSourcePosition   = node.sourceEnd();

        if (verboseLevel > 2)
            System.out.println("In JavaParserSupport::sourcePosition(ASTNode): start = " + startingSourcePosition + " end = " + endingSourcePosition);

        // Example of how to compute the starting line number and column position of any AST node.
        int problemStartPosition = startingSourcePosition;
        int[] lineEnds;
        int lineNumber = problemStartPosition >= 0
                             ? Util.getLineNumber(problemStartPosition, lineEnds = rose_compilationResult.getLineSeparatorPositions(), 0, lineEnds.length-1)
                             : 0;
        int columnNumber = problemStartPosition >= 0
                               ? Util.searchColumnNumber(rose_compilationResult.getLineSeparatorPositions(), lineNumber, problemStartPosition)
                               : 0;

        if (verboseLevel > 2)
            System.out.println("In JavaParserSupport::sourcePosition(ASTNode): lineNumber = " + lineNumber + " columnNumber = " + columnNumber);

        // Example of how to compute the ending line number and column position of any AST node.
        int problemEndPosition = endingSourcePosition;
        int lineNumber_end = problemEndPosition >= 0
                                 ? Util.getLineNumber(problemEndPosition, lineEnds = rose_compilationResult.getLineSeparatorPositions(), 0, lineEnds.length-1)
                                 : 0;
        int columnNumber_end = problemEndPosition >= 0
                                   ? Util.searchColumnNumber(rose_compilationResult.getLineSeparatorPositions(), lineNumber, problemEndPosition)
                                   : 0;

        if (verboseLevel > 2)
            System.out.println("In JavaParserSupport::sourcePosition(ASTNode): lineNumber_end = " + lineNumber_end + " columnNumber_end = " + columnNumber_end);
    }
*/

    public void processConstructorDeclarationHeader(ConstructorDeclaration constructor, JavaToken jToken) {
        assert(! constructor.isDefaultConstructor());
    
        String name = new String(constructor.selector);
        boolean is_native = constructor.isNative();
        boolean is_private = (constructor.binding != null) && (! constructor.binding.isPrivate());
        JavaParser.cactionConstructorDeclarationHeader(name,
                                                       is_native,
                                                       is_private,
                                                       constructor.typeParameters == null ? 0 : constructor.typeParameters.length, 
                                                       constructor.arguments == null ? 0 : constructor.arguments.length,
                                                       constructor.thrownExceptions == null ? 0 : constructor.thrownExceptions.length,
                                                       jToken
                                                      );
         
         
    }


    /**
     * 
     * @param method
     * @param jToken
     */
    public void processMethodDeclarationHeader(MethodDeclaration method, JavaToken jToken) {
        String name = new String(method.selector);

        // Setup the function modifiers
        boolean is_abstract = method.isAbstract();
        boolean is_native   = method.isNative();
        boolean is_static   = method.isStatic();
        boolean is_final    = method.binding != null && method.binding.isFinal();
        boolean is_private  = method.binding != null && method.binding.isPrivate();

        // These is no simple function for theses cases.
        boolean is_synchronized = ((method.modifiers & ClassFileConstants.AccSynchronized) != 0);
        boolean is_public       = ((method.modifiers & ClassFileConstants.AccPublic)       != 0);
        boolean is_protected    = ((method.modifiers & ClassFileConstants.AccProtected)    != 0);

        boolean is_strictfp     = method.binding != null && method.binding.isStrictfp();

        // These are always false for member functions.
        boolean is_volatile     = false;
        boolean is_transient    = false;
        
        JavaParser.cactionMethodDeclarationHeader(name,
                                                  is_abstract,
                                                  is_native,
                                                  is_static,
                                                  is_final,
                                                  is_synchronized,
                                                  is_public,
                                                  is_protected,
                                                  is_private,
                                                  is_strictfp, 
                                                  method.typeParameters == null ? 0 : method.typeParameters.length,
                                                  method.arguments == null ? 0 : method.arguments.length,
                                                  method.thrownExceptions == null ? 0 : method.thrownExceptions.length,
                                                  jToken
                                                 );
    }


    /**
     * 
     * @param node
     * @param jToken
     */
    void processQualifiedNameReference (QualifiedNameReference node, JavaToken jToken ) {
//System.out.println("The binding (" + node.binding.getClass().getCanonicalName() + 
//           ") is " + new String(node.binding.readableName()));
//System.out.println("The receiver binding is " + new String(node.actualReceiverType.readableName()));
        int type_prefix_length = node.indexOfFirstFieldBinding - 1;
        TypeBinding type_binding = null;
        if (node.binding instanceof FieldBinding) {
            type_binding = ((FieldBinding) node.binding).declaringClass;
            assert(type_binding == node.actualReceiverType);
        }
        else if (node.binding instanceof VariableBinding) {
            assert(type_prefix_length == 0);
        }
        else {
            assert(node.binding instanceof TypeBinding);
            type_binding = (TypeBinding) node.binding;
            type_prefix_length++; // The field in question is a type.
        }

        StringBuffer strbuf = new StringBuffer();
//System.out.println("First field index = " + node.indexOfFirstFieldBinding);
        for (int i = 0; i < type_prefix_length; i++) {
            strbuf.append(node.tokens[i]);
            if (i + 1 < type_prefix_length)
                strbuf.append(".");
        }

        String type_prefix = new String(strbuf);
//System.out.println("type_prefix = " + type_prefix);

        if (type_prefix.length() > 0) {
            Class cls = preprocessClass(type_binding);
            assert(cls != null);

            JavaParser.cactionTypeReference(getPackageName(type_binding), getTypeName(type_binding), jToken);

            for (int i = type_prefix_length; i < node.tokens.length; i++) {
                String field = new String(node.tokens[i]);
//System.out.println("Processing the sub field name: " + field);
                JavaParser.cactionFieldReferenceEnd(field, jToken);
            }
        }
        else {
            int field_index = type_prefix_length;
            String first_field = new String(node.tokens[field_index++]);
            assert(node.binding instanceof VariableBinding);
//System.out.println("Processing the first field name: " + first_field);

        JavaParser.cactionSingleNameReference("", "", first_field, jToken);

            strbuf = new StringBuffer();
            for (int i = field_index; i < node.tokens.length; i++) {
                String field = new String(node.tokens[i]);
//System.out.println("Processing the sub field name: " + field);

                JavaParser.cactionFieldReferenceEnd(field, jToken);
            
                strbuf.append(field);
                if (i + 1 < node.tokens.length)
                    strbuf.append(".");
            }
            String name = new String(strbuf);
//System.out.println("The rest of the name is " + name);
        }
    }
    
    
    /**
     * 
     * @param typeClass
     */
    /*
    private static void processType(Class typeClass) {
        // This function processes all the references to types found in data members, function 
        // return types, function argument types, etc.  With each type it is included into a set
        // (if it is not a primitive type) and then an SgClassType is generated in the ROSE AST
        // so that all references to this type can be supported.  Note that for trivial input 
        // codes, most of the referenced classes are implicit classes; reflection is used to 
        // traversal all of these and recursively build all types.  This is part of what is required 
        // to support a consistant AST in ROSE.

        // More inforamtion on what is in the Class (and other reflection classes) can be found at:
        //      http://download.oracle.com/javase/6/docs/api/java/lang/Class.html

        String nestedClassName = typeClass.getName();

        if (verboseLevel > 1)
            System.out.println("In processType(): type = " + typeClass);

        if (verboseLevel > 1) {
            // This code is part of an interogation of the data in the field and needs to be hidden yet available to support debugging.
            // ******************************************************************************
            // System.out.println("type = " + typeClass);

            if (verboseLevel > 5) {
                System.out.println("fld.getType().isAnnotation()                 = " + typeClass.isAnnotation());
                // System.out.println("fld.getType().isAnnotationPresent(Class<? extends Annotation> annotationClass) = " + fld.getType().isAnnotationPresent(fld.getType()));
                System.out.println("fld.getType().isAnonymousClass()             = " + typeClass.isAnonymousClass());
                System.out.println("fld.getType().isArray()                      = " + typeClass.isArray());
                // Not clear what class to use as a test input for isAssignableFrom(Class<?> cls) function...
                System.out.println("fld.getType().isAssignableFrom(Class<?> cls) = " + typeClass.isAssignableFrom(typeClass));
                System.out.println("fld.getType().isEnum()                       = " + typeClass.isEnum());
                System.out.println("fld.getType().isInstance(Object obj)         = " + typeClass.isInstance(typeClass));
                System.out.println("fld.getType().isInterface()                  = " + typeClass.isInterface());
                System.out.println("fld.getType().isLocalClass()                 = " + typeClass.isLocalClass());
                System.out.println("fld.getType().isMemberClass()                = " + typeClass.isMemberClass());
                System.out.println("fld.getType().isPrimitive()                  = " + typeClass.isPrimitive());
                System.out.println("fld.getType().isSynthetic()                  = " + typeClass.isSynthetic());
                System.out.println("-----");
                // ******************************************************************************
            }
        }

        // We don't have to support Java primitive types as classes in the AST (I think).
        if (! typeClass.isPrimitive()) {
            // Check if this is a type (class) that has already been handled.
            if (setOfClasses.contains(typeClass) == false) {
                // Investigate any new type.
                if (typeClass.isArray() == true) {
                    // DQ (4/3/2011): Added support for extracting the base type of an array and recursively processing the base type.

                    if (verboseLevel > 1)
                        System.out.println("Process the base type of the array of type ... base type = " + typeClass.getComponentType());

                    processType(typeClass.getComponentType());

                    // System.out.println("Exiting as a test...");
                    // System.exit(1);
                }
                else {
                    // This is not an array type and not a primitive type (so it should be a class, I think).
                    if (verboseLevel > 1)
                        System.out.println("Recursive call to buildImplicitClassSupport() to build type = " + nestedClassName);

                    // Add this to the set of classes that we have seen... so that we will not try to process it more than once...
                    setOfClasses.add(typeClass);

                    // String unqualifiedClassName = "X" + nestedClassName;
                    int startOfUnqualifiedClassName = nestedClassName.lastIndexOf(".");

                    if (verboseLevel > 1)
                        System.out.println("startOfUnqualifiedClassName = " + startOfUnqualifiedClassName);

                    String unqualifiedClassName = nestedClassName.substring(startOfUnqualifiedClassName+1);

                    // Add a map from the class name to its fully qualified name (used in type lookup).
                    if (verboseLevel > 1)
                        System.out.println("############# Set entry in hashmapOfQualifiedNamesOfClasses: unqualifiedClassName = " + unqualifiedClassName + " nestedClassName = " + nestedClassName);

                    hashmapOfQualifiedNamesOfClasses.put(unqualifiedClassName,nestedClassName);

                    // Control the level of recursion so that we can debug this...it seems that
                    // this is typically as high as 47 to process the implicitly included classes.
                    if (implicitClassCounter < implicitClassCounterBound) {
                        // DQ (11/2/2010): comment out this recursive call for now.
                        buildImplicitClassSupport(nestedClassName);
                    }
                    else {
                        if (verboseLevel > 5)
                            System.out.println("WARNING: Exceeded recursion level " + implicitClassCounter + " nestedClassName = " + nestedClassName);
                    }
                }
            }
            else {
                if (verboseLevel > 4)
                    System.out.println("This class has been seen previously: nestedClassName = " + nestedClassName);
                // setOfClasses.add(typeClass);
            }
        }
        else {
            // We might actually do have to include these since they are classes in Java... 
            // What member functions are there on primitive types?
            if (verboseLevel > 4)
                System.out.println("This class is a primitive type (sorry not implemented): type name = " + nestedClassName);
        }
    }
*/

    /**
     * 
     */
    public String qualifiedName(char [][]tokens) {
        StringBuffer name = new StringBuffer();
        for (int i = 0, tokenArrayLength = tokens.length; i < tokenArrayLength; i++) {
            String tokenString = new String(tokens[i]);

            if (i > 0) {
                name.append('.');
            }

            name.append(tokenString);
        }

        return name.toString();
    }
    
    /**
     * 
     * @param binding
     * @return
     */
    public String getPackageName(TypeBinding binding) {
        return (binding.getPackage() != null ? new String(binding.getPackage().readableName()) : "");
    }
    
    /**
     * 
     * @param binding
     * @return
     */
    public String getTypeName(TypeBinding binding) {
        String debug_name = binding.debugName(),
               type_name;
    
        if (binding.isRawType()) {
            assert(debug_name.endsWith("#RAW"));
//System.out.println("Found raw type " + debug_name);    
            type_name = debug_name.substring(0, debug_name.length() - 4);
//System.out.println("Converting it to " + type_name);    
        }
        else {
            String package_name = getPackageName(binding);
            type_name = debug_name.substring(package_name.length() == 0 ? 0 : package_name.length() + 1);
//System.out.println("Found a regular type " + type_name);                
        }

        if (binding.isBoundParameterizedType()) {
        /*
            int parameter_index = type_name.indexOf('<');
            if (parameter_index != -1) {
System.out.print("Clean up " + type_name);        
                type_name = type_name.substring(0, parameter_index);
System.out.println(" to obtain " + type_name);                
            }
        */
            System.out.println("*** Currenbtly, the translator only supports the 1.4 version of Java - Specify the options: -rose:java:source 1.4 -rose:java:target 1.4");
            System.exit(1);
        }

        return type_name;
    }
    

    /**
     * 
     * @param binding
     * @return
     */
    public String getFullyQualifiedTypeName(TypeBinding binding) {
        String package_name = getPackageName(binding),
               type_name = getTypeName(binding);
        return (package_name.length() == 0 ? type_name : package_name + "." + type_name);
    }

    //
    // If we have an an inner class, get its outermost enclosing parent.
    //
    public String getMainPackageName(Class base_class, int num) {
        while (base_class.getDeclaringClass() != null) {
            base_class = base_class.getDeclaringClass();
        }
assert(! base_class.isSynthetic());        
        String canonical_name = base_class.getCanonicalName(),
               class_name = base_class.getName(),
               simple_name = base_class.getSimpleName(),
               package_name = (simple_name.length() < canonical_name.length()
                                     ? canonical_name.substring(0, canonical_name.length() - simple_name.length() -1)
                                     : "");
// TODO: REMOVE THIS
//System.out.println("(2, " + num + ") The canonical name is: " + canonical_name + "; The package is: " + package_name + ";  The class name is " + class_name + ";  The simple class name is " + simple_name);
        return package_name;
    }

    public Class preprocessClass(TypeBinding binding) {
        while (binding.enclosingType() != null) {
            binding = binding.enclosingType();
        }
        return preprocessClass(getFullyQualifiedTypeName(binding));
    }
    
    /**
     * 
     */
    public Class preprocessClass(String typename) {
        Class cls = getClassForName(typename);
        if (cls != null) {
            preprocessClass(cls);
        }
        return cls;
    }
/*
    public static String cleanTypeName(String typename) {
        if (typename.endsWith("#RAW")) {
System.out.println("Found raw type " + typename);    
            typename = typename.substring(0, typename.length() - 4);
System.out.println("Converting it to " + typename);    
        }
        return typename;
    }
*/
    /**
     * 
     * @param cls
     */
    public void preprocessClass(Class cls) {
        //
        // If the class in question is an array, get the ultimate base class.
        //
        Class base_class = cls;
        while (base_class.isArray()) {
            base_class = base_class.getComponentType();
        }

        //
        // If we have an an inner class, get its outermost enclosing parent.
        //
        while (base_class.getDeclaringClass() != null) {
            base_class = base_class.getDeclaringClass();
        }

        //
        // If the candidate is not a primitive and has not yet been processed, then process it.
        //
        if (! base_class.isPrimitive()) {
            String canonical_name = base_class.getCanonicalName(),
                   class_name = base_class.getName(),
                   simple_name = base_class.getSimpleName(),
                   package_name = getMainPackageName(base_class, 1); 

// TODO: REMOVE THIS
// System.out.println("(2) The canonical name is: " + canonical_name + "; The package is: " + package_name + ";  The class name is " + class_name + ";  The simple class name is " + simple_name);

           if (! typeExists(package_name, simple_name)) {
                insertType(package_name, base_class);
// TODO: REMOVE THIS
//System.out.println("Inserting type " + canonical_name);
                //
                // TODO:  For now, we make an exception with user-specified classes and do not insert them
                // into the package that they are declared in. From Rose's point of view, these classes will
                // be declared in their respective SgGlobal environment.
                //
                assert(symbolTable.get(package_name).get(simple_name) != null);

                JavaParser.cactionPushPackage(package_name);
// TODO: REMOVE THIS
//System.out.println("Pushing package " + package_name);                
                insertClasses(base_class);
                traverseClass(base_class);
                JavaParser.cactionPopPackage();
            }
// TODO: REMOVE THIS
//else {
//System.out.println("The type " + canonical_name  + " has already been processed");
//}
        }
    }


    /**
     * 
     * @param cls
     * 
     * Note that it is very important that a given class and all its inner classes be inserted
     * in the translator prior to traversing the classes to process the members of a given class.
     * This is necessary because Java allows forward references.  For example, a field or method
     * may refer to an inner class which has not yet been processed as its type.
     * 
     */
    private void insertClasses(Class cls) {
        TypeDeclaration node = userTypeTable.get(cls);
        JavaToken location = (node != null
                                    ? createJavaToken(node)
                                    : new JavaToken("Dummy JavaToken (see createJavaToken)", new JavaSourcePositionInformation(0)));
        
        JavaParser.cactionInsertClassStart(cls.getSimpleName(), location);
        Class innerClasslist[] = cls.getDeclaredClasses();
        for (int i = 0; i < innerClasslist.length; i++) {
            Class inner_class = innerClasslist[i];
            insertClasses(inner_class);
        }
        JavaParser.cactionInsertClassEnd(cls.getSimpleName(), location);
    }


    /**
     * 
     * @param cls
     */
    private void traverseClass(Class cls) {
// TODO: REMOVE THIS
//System.out.println("Starting with class " + cls.getCanonicalName());
        String class_name = cls.getSimpleName();

        TypeDeclaration node = userTypeTable.get(cls); // is this a user-defined type?

        JavaToken location = (node != null
                                    ? createJavaToken(node)
                                    : new JavaToken("Dummy JavaToken (see createJavaToken)", new JavaSourcePositionInformation(0)));

        JavaParser.cactionBuildClassSupportStart(class_name, cls.isInterface(), location);

        if (cls.getCanonicalName().equals("java.lang.Object")) { // If we are processing Object, signal this to the translator.
            JavaParser.cactionSetupObject();
        }

        if (verboseLevel > 2)
            System.out.println("After call to cactionBuildClassSupportStart");

        //
        // Get the fields, constructors, and methods used in this class.
        // Note that try ... catch is required for using the reflection support in Java.
        //
        Class super_class = cls.getSuperclass();
        Class interfacelist[] = cls.getInterfaces();

        Method methlist[] = null;
        try {
            methlist = cls.getDeclaredMethods();
        }
        catch (Throwable e) {
            e.printStackTrace();
            System.exit(1);
        }

        Field fieldlist[] = null;
        try {
            fieldlist = cls.getDeclaredFields();
        }
        catch (NoClassDefFoundError e) {
            e.printStackTrace();
            String signature = e.getMessage();
            signature = signature.substring(1, signature.length() - 1).replace('/', '.');
System.out.println("The message is " + e.getMessage());
System.out.println("The cleaned-up message is " + signature);
Class c = preprocessClass(signature);
System.out.println("The pathLoader associated with " + signature + " is " + (c.getClassLoader() == pathLoader ? "" : "not") + " the same as the Rose-Java pathLoader"); 
System.out.println("The Rose-Java pathLoader" + (c == null ? " did not find " : " found ") + " type " + signature);
System.out.println("The Rose-Java pathLoader is " + (cls.getClassLoader() == c.getClassLoader() /* pathLoader */ ? "" : "not") +
                   " the same loader as the loader associated with the type " + cls.getCanonicalName());
System.out.println("The loader associated with the type " + cls.getCanonicalName() + " is " + 
                   (cls.getClassLoader() == ClassLoader.getSystemClassLoader() ? "" : "not") + " the same loader as the system class loader");
try {
c = cls.getClassLoader().loadClass(signature);
System.out.println("The main clas Loader found type " + signature);
}
catch(ClassNotFoundException ee) { 
System.out.println("The main class Loader did not find type " + signature);
}
System.out.println();
System.out.println("*** Class Loader Error - Stopping !!!");
            System.exit(1);
        }

        Constructor ctorlist[] = null;
        try {
            ctorlist = cls.getDeclaredConstructors();
        }
        catch (Throwable e) {
            e.printStackTrace();
            System.exit(1);
        }

        Class innerClasslist[] = null;
        try {
            innerClasslist = cls.getDeclaredClasses();
        }
        catch (Throwable e) {
            e.printStackTrace();
            System.exit(1);
        }

        // process the super class
        if (super_class != null) {
            if (verboseLevel > 2) {
                System.out.println("Super Class name = " + super_class.getName());
            }

            preprocessClass(super_class);
            generateAndPushType(super_class);
//            catch (NoClassDefFoundError eee) { 
        }

        // Process the interfaces.
        for (int i = 0; i < interfacelist.length; i++) {
            if (verboseLevel > 2) {
                System.out.println("interface name = " + interfacelist[i].getName());
            }
            preprocessClass(interfacelist[i]);
            generateAndPushType(interfacelist[i]);
        }

        JavaParser.cactionBuildClassExtendsAndImplementsSupport((super_class != null), interfacelist.length, location);

        //
        // Process the inner classes. Note that the inner classes must be processed first in case
        // one of these types are used for a field or a method. (e.g., See java.net.InetAddress)
        //
        for (int i = 0; i < innerClasslist.length; i++) {
            Class inner_class = innerClasslist[i];
// TODO: REMOVE THIS
//System.out.println("About to process inner class: " + inner_class.getCanonicalName() + " with class name " + inner_class.getName() + " and simple name " + inner_class.getSimpleName() + (inner_class.isSynthetic() ? " (Synthetic)" : ""));
            if (! inner_class.isSynthetic()) {
                traverseClass(inner_class);
            }
        }

        //
        // Preprocess the types in field declarations
        //
        for (int i = 0; i < fieldlist.length; i++) {
            Field fld = fieldlist[i];
            if (fld.isSynthetic()) // skip synthetic fields
                continue;
            Class type = fld.getType();
            preprocessClass(type);
        }
        
        //
        // Process the constructor parameter types.
        //
        for (int i = 0; i < ctorlist.length; i++) {
            Constructor ct = ctorlist[i];
            if (ct.isSynthetic()) // skip synthetic constructors
                continue;
            Class pvec[] = ct.getParameterTypes();
            for (int j = 0; j < pvec.length; j++) {
                preprocessClass(pvec[j]);
            }
        }

        //
        // Process the method parameter types.
        //
        for (int i = 0; i < methlist.length; i++) {
            Method m = methlist[i];
            if (m.isSynthetic()) // skip synthetic methods
                continue;
            Class pvec[] = m.getParameterTypes();

            // Process the return type (add a class if this is not already in the ROSE AST).
            preprocessClass(m.getReturnType());

            for (int j = 0; j < pvec.length; j++) {
                preprocessClass(pvec[j]);
            }
        }        
    
        if (node != null) { // If this class is associated with a user-defined type, process the original source.
            //
            // Now, traverse the class members in the order in which they were specified.
            //
            ASTNode node_list[] = orderedClassMembers.get(node);
            assert(node_list != null);
            for (int k = 0; k < node_list.length; k++) {
                ASTNode class_member = node_list[k];
                if (class_member instanceof TypeDeclaration) {
                    TypeDeclaration inner_class = (TypeDeclaration) class_member;
                    // Inner classes already processed above.
                }
                else if (class_member instanceof FieldDeclaration) {
                    FieldDeclaration field = (FieldDeclaration) class_member;
                    JavaToken field_location = createJavaToken(field);

                    if (field instanceof Initializer) {
                        Initializer initializer = (Initializer) field;
                        String name = k + "block";
                        initializerName.put(initializer, name);
                        JavaParser.cactionBuildInitializerSupport(initializer.isStatic(), name, field_location);
                    }
                    else {
                        generateAndPushType(field.binding.type, field_location);

                        if (verboseLevel > 2)
                            System.out.println("Build the data member (field) for name = " + new String(field.name));

                        JavaParser.cactionBuildFieldSupport(new String(field.name), field_location);
                    }
                }
                else if (class_member instanceof AbstractMethodDeclaration) {
                    AbstractMethodDeclaration method = (AbstractMethodDeclaration) class_member;
                    if (method.isClinit() /*|| method.isDefaultConstructor()*/)
                        continue;
                    JavaToken method_location = createJavaToken(method);

    // TODO: REMOVE THIS
    //System.out.println("Processing a user-defined method " + new String(method.selector));
                    MethodBinding method_binding = method.binding;

                    if (method.isConstructor()) {
    // TODO: REMOVE THIS
    //System.out.println(new String(method.selector) + " is a constructor");
                        JavaParser.cactionTypeReference("", "void", method_location);
                    }
                    else {
                        generateAndPushType(method_binding.returnType, method_location);
                    }

                    Argument args[] = method.arguments;
                    TypeVariableBinding type_bindings[] = method_binding.typeVariables;
                    
                    if (args != null) {
    // TODO: REMOVE THIS
    //System.out.println("This method has " + args.length + " arguments");
//                        assert(args.length == type_bindings.length);
                        for (int j = 0; j < args.length; j++) {
    // TODO: REMOVE THIS
    //System.out.println("Processing argument " + j + " of method " + new String(method.selector));
                            Argument arg = args[j];
                            JavaToken arg_location = createJavaToken(arg);
                            generateAndPushType(arg.type.resolvedType, arg_location);
                            JavaParser.cactionArgumentEnd(new String(args[j].name), false /* not a Catch argument */, arg_location);
                        }
                    }

                    //
                    // TODO: process Throws list ... not relevant for now because the translator does not handle them yet.
                    //
                    String method_name = new String(method.selector);
                    JavaParser.cactionBuildMethodSupport(method_name,
                                                         method.isConstructor(),
                                                         method.isAbstract(),
                                                         method.isNative(),
                                                         args == null ? 0 : args.length,
                                                         createJavaToken(method));
                }
                else assert(false);
            }
        }
        else {
            //
            // Preprocess the fields in this class.
            //
            for (int i = 0; i < fieldlist.length; i++) {
                Field fld = fieldlist[i];
                if (fld.isSynthetic()) // skip synthetic fields
                    continue;

                if (verboseLevel > 0) {
                    // This code is part of an interogation of the data in the field and needs to be hidden yet available to support debugging.
                    // ******************************************************************************
                    System.out.println("data member (field) name = " + fld.getName());

                    System.out.println("decl class  = " + fld.getDeclaringClass());
                    System.out.println("type = " + fld.getType());
                    System.out.println("genericType = " + fld.getGenericType());
                    int mod = fld.getModifiers();
                    System.out.println("modifiers   = " + Modifier.toString(mod));

                    System.out.println("fld.isEnumConstant() = " + fld.isEnumConstant());

                    // I think that "synthetic" means compler generated.
                    System.out.println("fld.isSynthetic()    = " + fld.isSynthetic());
                    System.out.println("-----");
                    // ******************************************************************************
                }

                generateAndPushType(fld.getType());

                if (verboseLevel > 2)
                    System.out.println("Build the data member (field) for name = " + fld.getName());
// TODO: REMOVE THIS
//if (fld.getName().indexOf('$') != -1) System.out.println("*Field " + fld.getName() + " in class " + cls.getCanonicalName());
                JavaParser.cactionBuildFieldSupport(fld.getName(), location);

                if (verboseLevel > 2)
                    System.out.println("DONE: Building the data member (field) for name = " + fld.getName());
            }

            // A traversal over the constructors will have to look at all types of constructor arguments 
            // and trigger a recursive call to buildImplicitClassSupport() for any new types.
            for (int i = 0; i < ctorlist.length; i++) {
                Constructor ct = ctorlist[i];
                if (ct.isSynthetic()) // skip synthetic constructors
                     continue;

                Class pvec[] = ct.getParameterTypes();

                JavaParser.cactionTypeReference("", "void", location);

                for (int j = 0; j < pvec.length; j++) {
                    generateAndPushType(pvec[j]);
                    JavaParser.cactionArgumentEnd(ct.getName() + j, false /* not a Catch argument */, location);
                }

                //
                // TODO: process Throws list ... not relevant for now because the translator does not handle them yet.
                //
            
                JavaParser.cactionBuildMethodSupport(class_name /* ct.getName() */,
                                                     true, /* a constructor */
                                                     Modifier.isAbstract(ct.getModifiers()),
                                                     Modifier.isNative(ct.getModifiers()),
                                                     pvec == null ? 0 : pvec.length,
                                                     location);
            }
            
            for (int i = 0; i < methlist.length; i++) {
                Method m = methlist[i];

                Class pvec[] = m.getParameterTypes();

                generateAndPushType(m.getReturnType());

                // System.out.println("method name = " + m.getName());
                for (int j = 0; j < pvec.length; j++) {
                    generateAndPushType(pvec[j]);
                    JavaParser.cactionArgumentEnd(m.getName() + j, false /* not a Catch argument */, location);
                }

                //
                // TODO: process Throws list ... not relevant for now because the translator does not handle them yet.
                //
                

// TODO: REMOVE THIS
//System.out.println("*Method " + m.getName() + " in class " + cls.getCanonicalName() + " is " + (Modifier.isAbstract(m.getModifiers()) ? "" : "not ") + "abstract");
                JavaParser.cactionBuildMethodSupport(m.getName().replace('$', '_'),
                                                     false, /* NOT a constructor! */
                                                     Modifier.isAbstract(m.getModifiers()),
                                                     Modifier.isNative(m.getModifiers()),
                                                     pvec == null ? 0 : pvec.length,
                                                     location);
            }
        }

        // This wraps up the details of processing all of the child classes (such as forming SgAliasSymbols for them in the global scope).
        JavaParser.cactionBuildClassSupportEnd(class_name, location);
// TODO: REMOVE THIS
//System.out.println("Done with class " + cls.getCanonicalName());
    }

  
    /*
    public static void buildImplicitClassSupport(String className) {
        // DQ (12/15/2010): Implicit class support seems to be unavailable via Java reflection...(working on solution to this).

        // There is a lot of information that we need about any implicitly included class.
        // Information about the introspection support is at: http://download.oracle.com/javase/1.4.2/docs/api/java/lang/Class.html
        // Additional information required should include:
        //    1) Class hierarchy.
        //    2) Interfaces
        //    3) package information
        //    4) modifiers (for this class)
        //    5) ProtectionDomain
        //    6) Resources (URLs?)
        //    7) Signers
        //    8) Superclass (part of the class hiearchy)
        //    9) Array information (is the class an array of some base type)
        //   10) See member function of the "Class" class for introspection for more details...

        // List of pakages needed to be include for initial work:
        //    1) java.lang
        //    2) java.io
        //    3) java.util

        // We can't use reflection to get the classes in a package (amazing but true).
        // so for the default packages we have to build a list of the classes that we will include.
        // So we have a list of classes that we will include for each package
        // (http://en.wikipedia.org/wiki/Java_package):
        //    1) java.lang (http://download.oracle.com/javase/6/docs/api/java/lang/package-summary.html)
        //       a. System 
        //    2) java.io  (http://download.oracle.com/javase/6/docs/api/java/io/package-summary.html)
        //       a. InputStream
        //       b. OutputStream
        //    3) java.util

        // Better (best) yet would be that we load what we need as we see it within the compilation.
        // so ever reference class and in that class the classes used as types for every field and method.
        // This will be a large list, but it should terminate, and be a minimal set of types (classes)
        // required to represent the problem :-).

        // See: http://www.java2s.com/Tutorial/Java/0125__Reflection/Catalog0125__Reflection.htm
        // for example of how to handle reflection details.
        // See also: http://www.java2s.com/Tutorial/Java/CatalogJava.htm

//        if (verboseLevel > 2)
            System.out.println("In buildImplicitClassSupport("+className+"): implicitClassCounter = " + implicitClassCounter);

        // Get the fields, constructors, and methods used in this class.
        // Note that try ... catch is required for using the reflection support in Java.
        try {
            // Class cls = Class.forName("java.lang.String");
            // Class cls = Class.forName("java.lang."+node.receiver.toString());

            if (verboseLevel > 1)
                System.out.println("Generate the implicit Java class for className = " + className + " implicitClassCounter = " + implicitClassCounter);

            // Note that "java.lang" does not appear to be a class (so is that root of all implicitly included classes?).
            // Class cls = Class.forName("java.lang");
            // Class cls = Class.forName("java.io.InputStream");
            Class cls = Class.forName(className);
            String canonical_name = cls.getCanonicalName(),
                   simple_name = cls.getSimpleName(),
                   package_name = (simple_name.length() < canonical_name.length()
                                         ? canonical_name.substring(0, canonical_name.length() - simple_name.length() -1)
                                         : "");
System.out.println("The canonical name is: " + cls.getCanonicalName() +
                   "; The package is: " + package_name + ";  The class name is " + simple_name);

            if (verboseLevel > 2)
                System.out.println("Generate the interface list for class " + className);

            // Generate the list if interfaces
            // Class interfaceList[] = cls.getGenericInterfaces();
            Class interfaceList[] = cls.getInterfaces();

            if (verboseLevel > 2)
                System.out.println("Generate the method list for class " + className);

            Method methlist[] = cls.getDeclaredMethods();

            if (verboseLevel > 2)
                System.out.println("Calling JavaParser.cactionBuildImplicitClassSupportStart() for className = " + className);

            // Replace any names like "java.lang.System" with "java_lang_System".
            JavaParser.cactionBuildImplicitClassSupportStart(className);

            // String modifiedClassName = className.replace('.','_');
            // JavaParser.cactionBuildImplicitClassSupportStart(modifiedClassName);

            if (verboseLevel > 2)
                System.out.println("After call to cactionBuildImplicitClassSupportStart");

            // This will get all fields (including private fields), getFields() will not include private fields.
            Field fieldlist[] = cls.getDeclaredFields();

            // This is a way to limit the number of fields to be traversed and thus control the complexity of the implicitly defined class structure.
            int numberOfFields = fieldlist.length;

            int dataMemberCounter = 0;
            for (int i = 0; i < numberOfFields; i++) {
                Field fld = fieldlist[i];

                if (verboseLevel > 0) {
                    // This code is part of an interogation of the data in the field and needs to be hidden yet available to support debugging.
                    // ******************************************************************************
                    System.out.println("data member (field) name = " + fld.getName());

                    System.out.println("decl class  = " + fld.getDeclaringClass());
                    System.out.println("type = " + fld.getType());
                    System.out.println("genericType = " + fld.getGenericType());
                    int mod = fld.getModifiers();
                    System.out.println("modifiers   = " + Modifier.toString(mod));

                    System.out.println("fld.isEnumConstant() = " + fld.isEnumConstant());

                    // I think that "synthetic" means compler generated.
                    System.out.println("fld.isSynthetic()    = " + fld.isSynthetic());
                    System.out.println("-----");
                    // ******************************************************************************
                }

                // Error: This appears to have "class " prepended to the generated string...causing problems below. 
                // String nestedClassName = fld.getType().toString();
                // System.out.println("nestedClassName = " + nestedClassName);

                // How do I do this in Java???
                // if (map.find(nestedClassName) == map.end())

                // Get the class associated with the field (all types in Java are a class, so this is only strange relative to C++).
                Class typeClass = fld.getType();

                // DQ (9/9/2011): Bug fix this is not referenced.
                // Type genericType = fld.getGenericType();

                // Note that if we use "nestedClassName = fld.getType().toString();" nestedClassName has the
                // name "class " as a prefix and this causes an error, so use "typeClass.getName()" instead.
                String nestedClassName = typeClass.getName();

                // Replace any names like "java.lang.System" with "java_lang_System".
                // nestedClassName = nestedClassName.replace('.','_');

                // Need to test for: isPrimitive(), isArray(), isInterface(), isAssignableFrom(), isInstance()
                // More documentation at: http://download.oracle.com/javase/1.4.2/docs/api/java/lang/Class.html

                // We can't output the full field in processType() if the type is an array (so this is just debug support).
                if (verboseLevel > 2) {
                    if (typeClass.isArray() == true) {
                        // DQ (3/21/2011): If this is an array of some type then we have to query the base type and for now I will skip this.
                        System.out.println("Skipping case of array of type for now (sorry not implemented)... data field = " + fld);
                    }
                }

                // Refactored this work so it could be called elsewhere.
                processType(typeClass);

                // System.out.println("Exiting after returning from recursive call...");
                // System.exit(1);

                if (verboseLevel > 2)
                    System.out.println("Build the implicit type for the data member (field) of type = " + nestedClassName);

                // Note that i == dataMemberCounter
                if (dataMemberCounter < dataMemberCounterBound) {
                    // System.out.println("#############################################################################################");
                    // System.out.println("This call to JavaParserSupport.<() appears to be a problem: nestedClassName = " + nestedClassName);
                    JavaParserSupport.generateAndPushType(typeClass);
                    // System.out.println("DONE: This call to JavaParserSupport.generateType() appears to be a problem: nestedClassName = " + nestedClassName);

                    if (verboseLevel > 2)
                        System.out.println("Build the data member (field) for name = " + fld.getName());

                    // System.out.println("Exiting after call to JavaParserSupport.generateType(typeClass) implicitClassCounter = " + implicitClassCounter);
                    // System.exit(1);

                    // This function assumes that a type has been placed onto the astJavaTypeStack.
                    JavaParser.cactionBuildImplicitFieldSupport(fld.getName());
                }
                else {
                    if (verboseLevel > 2)
                        System.out.println("WARNING: Exceeded data member (field) handling iteration count " + i + " className = " + className);
                }

                if (verboseLevel > 2)
                    System.out.println("DONE: Building the data member (field) for name = " + fld.getName());

                if (implicitClassCounter > 5 && false) {
                    System.out.println("Exiting as a test implicitClassCounter = " + implicitClassCounter);
                    System.exit(1);
                }

                dataMemberCounter++;
            }

            // A traversal over the constructors will have to look at all types of constructor arguments 
            // and trigger a recursive call to buildImplicitClassSupport() for any new types.
            Constructor ctorlist[] = cls.getDeclaredConstructors();
            int constructorMethodCounter = 0;
            for (int i = 0; i < ctorlist.length; i++) {
                Constructor ct = ctorlist[i];
                Class pvec[] = ct.getParameterTypes();

                // Note that I am ignoring the constructor parameter types at the moment.
                if (verboseLevel > 2) {
                    System.out.println("constructor name = " + ct.getName());
                    for (int j = 0; j < pvec.length; j++) {
                         System.out.println("   constructor parameter type = " + pvec[j]);
                    }
                }

                // System.out.println("constructor name = " + ct.getName());
                for (int j = 0; j < pvec.length; j++) {
                    if (verboseLevel > 2)
                        System.out.println("   constructor parameter type = " + pvec[j]);

                    // Process the paramter type (add a class if this is not already in the ROSE AST).
                    processType(pvec[j]);
                }

                // Simplify the generated AST by skipping the construction of all the member functions in each class.
                // We might only want to build those member functions that are referenced in the input program (as an option).
                // JavaParser.cactionBuildImplicitMethodSupport(ct.getName());

                if (constructorMethodCounter < constructorMethodCounterBound) {
                    // Note that we only want to build types for those function that we want to build.
                    // This mechanism is one way to simplify the generated AST for debugging (restricting 
                    // the number of functions built).

                    if (verboseLevel > 2)
                        System.out.println("Push void as a return type for now (ignored because this is a constructor)");

                    // Push a type to serve as the return type which will be ignored for the case of a constructor
                    // (this allows us to reuse the general member function support).
                    JavaParser.cactionTypeReference("void", new JavaToken("Dummy JavaToken (see createJavaToken)", new JavaSourcePositionInformation(0)));

                    if (verboseLevel > 2)
                        System.out.println("DONE: Push void as a return type for now (ignored because this is a constructor)");

                    // System.out.println("constructor name = " + ct.getName());
                    for (int j = 0; j < pvec.length; j++) {
                        // If we push all the types onto the stack then we have to build every constructor.
                        if (verboseLevel > 2)
                            System.out.println("This call to JavaParserSupport.generateType() pushes a type onto the astJavaTypeStack (constructor): type = " + pvec[j].getName());
                        JavaParserSupport.generateAndPushType(pvec[j]);
                        JavaParser.cactionArgumentEnd(ct.getName() + j, false, // not a Catch argument
                                                      new JavaToken("Dummy JavaToken (see createJavaToken)", new JavaSourcePositionInformation(0)));
                        if (verboseLevel > 2)
                            System.out.println("DONE: This call to JavaParserSupport.generateType() pushes a type onto the astJavaTypeStack (constructor): type = " + pvec[j].getName());
                    }

                    JavaParser.cactionBuildImplicitMethodSupport(ct.getName(), pvec == null ? 0 : pvec.length);
                }
                else {
                    if (verboseLevel > 2)
                        System.out.println("WARNING: Exceeded constructor method handling iteration count " + constructorMethodCounter + " className = " + className);
                }

                constructorMethodCounter++;
            }

            // A traversal over the methods will have to look at all types of method return types and arguments 
            // and trigger a recursive call to buildImplicitClassSupport() for any new types.
            // System.out.println("(skipped method handling) Number of methods = " + methlist.length);
            int methodCounter = 0;
            for (int i = 0; i < methlist.length; i++) {
                Method m = methlist[i];

                Class pvec[] = m.getParameterTypes();

                // Note that I am ignoring the constructor parameter types at the moment.
                if (verboseLevel > 5) {
                    System.out.println("method name = " + m.getName());
                    System.out.println("   method return type = " + m.getReturnType());
                    for (int j = 0; j < pvec.length; j++) {
                        System.out.println("   method parameter type = " + pvec[j]);
                    }
                }

                // Process the return type (add a class if this is not already in the ROSE AST).
                processType(m.getReturnType());

                // System.out.println("method name = " + m.getName());
                for (int j = 0; j < pvec.length; j++) {
                    if (verboseLevel > 4)
                        System.out.println("   method return type = " + m.getReturnType());

                    if (verboseLevel > 4)
                        System.out.println("   method parameter type = " + pvec[j]);

                    // Process the paramter type (add a class if this is not already in the ROSE AST).
                    processType(pvec[j]);
                }

                // Simplify the generated AST by skipping the construction of all the member functions in each class.
                // We might only want to build those member functions that are referenced in the input program (as an option).

                if (methodCounter < methodCounterBound) {
                    // DQ (4/10/11): Fix this to use the proper return type now (pushed onto stack last and interpreted at the return type).
                    // Push a type to serve as the return type which will be ignored for the case of a method
                    // (this allows us to reuse the general member function support).
                    // System.out.println("Testing with made up return type");
                    // JavaParser.cactionGenerateType("void");
                    JavaParserSupport.generateAndPushType(m.getReturnType());

                    // System.out.println("method name = " + m.getName());
                    for (int j = 0; j < pvec.length; j++) {
                        // If we push all the types onto the stack then we have to build every method.
                        if (verboseLevel > 2)
                            System.out.println("This call to JavaParserSupport.generateType() pushes a type onto the astJavaTypeStack (method): type = " + pvec[j].getName());
                        JavaParserSupport.generateAndPushType(pvec[j]);
                        JavaParser.cactionArgumentEnd(m.getName(), false, // not a Catch argument
                                                      new JavaToken("Dummy JavaToken (see createJavaToken)", new JavaSourcePositionInformation(0)));
                        if (verboseLevel > 2)
                            System.out.println("DONE: This call to JavaParserSupport.generateType() pushes a type onto the astJavaTypeStack (method): type = " + pvec[j].getName());
                    }

                    JavaParser.cactionBuildImplicitMethodSupport(m.getName(), pvec == null ? 0 : pvec.length);
                }
                else {
                    if (verboseLevel > 4)
                        System.out.println("WARNING: Exceeded method handling iteration count " + methodCounter + " className = " + className);
                }

                methodCounter++;
            }

            // Process the interfaces.
            int interfaceCounter = 0;
            for (int i = 0; i < interfaceList.length; i++) {
                if (verboseLevel > 2) {
                    System.out.println("interface name = " + interfaceList[i].getName());
                }

                if (interfaceCounter < interfaceCounterBound) {
                    // Process the interface type (add a class if this is not already in the ROSE AST).
                    processType(interfaceList[i]);
                }

                interfaceCounter++;
            }

            // Compute the total number of statements that we will have be poped from the stack to complete the class definition for ROSE.
            int numberOfStatements = methodCounter + constructorMethodCounter + dataMemberCounter + interfaceCounter;

            if (verboseLevel > 1)
                System.out.println("Implicit class support: numberOfStatements = " + numberOfStatements + " for className = " + className);

            // This wraps up the details of processing all of the child classes (such as forming SgAliasSymbols for them in the global scope).
            JavaParser.cactionBuildImplicitClassSupportEnd(numberOfStatements,className);
        }

        // try ... catch is required for using the reflection support in Java.
        catch (Throwable e) {
            System.out.println("Caught error in JavaParserSupport (Parser failed)");
            System.err.println(e);

            // I think we could also rethrough using: "throw e;"

            // Make sure we exit on any error so it is caught quickly.
            System.exit(1);
        }
    }
*/

   public boolean isPrimitiveType(TypeBinding typeBinding) {
        switch (typeBinding.id) {
            case TypeIds.T_void:
            case TypeIds.T_boolean:
            case TypeIds.T_byte:
            case TypeIds.T_char:
            case TypeIds.T_short:
            case TypeIds.T_double:
            case TypeIds.T_float:
            case TypeIds.T_int:
            case TypeIds.T_long:
            //    case TypeIds.T_JavaLangObject:
            //    case TypeIds.T_JavaLangString:
                return true;

            default:
                return false;
        }
    }


   /*
    public static void generateType(TypeReference node) {
        // This function traverses the type and calls JNI functions to 
        // at the end of the function define a type built in the ROSE 
        // AST and left of the top of the astJavaTypeStack.
        // This is designed as a recursive function.
        if (verboseLevel > 0)
            System.out.println("Inside of generateType(TypeReference)");

        assert(node != null);

        if (verboseLevel > 1) {
            System.out.println("Inside of generateType(TypeReference) TypeReference node                               = " + node);
            System.out.println("Inside of generateType(TypeReference) TypeReference node.implicitConversion            = " + node.implicitConversion);

            // DQ (9/3/2011): This causes too much output.
            // System.out.println("Inside of generateType(TypeReference) TypeReference node.resolvedType                  = " + node.resolvedType);

            System.out.println("Inside of generateType(TypeReference) TypeReference node.resolvedType.isArrayType()    = " + node.resolvedType.isArrayType());
            System.out.println("Inside of generateType(TypeReference) TypeReference node.resolvedType.isGenericType()  = " + node.resolvedType.isGenericType());
            System.out.println("Inside of generateType(TypeReference) TypeReference node.resolvedType.isClass()        = " + node.resolvedType.isClass());
            System.out.println("Inside of generateType(TypeReference) TypeReference isPrimitiveType(node.resolvedType) = " + isPrimitiveType(node.resolvedType));

            System.out.println("Inside of generateType(TypeReference) TypeReference node.getTypeName()                 = " + node.getTypeName());
            System.out.println("Inside of generateType(TypeReference) TypeReference node.resolvedType.isClass()        = " + (node.resolvedType.isClass() ? "true" : "false"));
        }

        if (node.resolvedType.isArrayType() == true) {
            // TypeBinding baseType = ((ArrayBinding) node.resolvedType).leafComponentType;
            ArrayBinding arrayType = (ArrayBinding) node.resolvedType;
            if (verboseLevel > 1)
                System.out.println("Inside of generateType(TypeReference) ArrayBinding dimensions = " + arrayType.dimensions);
            TypeBinding baseType = arrayType.leafComponentType;
            if (baseType.canBeInstantiated()) {
                preprocessClass(baseType.debugName());
            }
// TODO: Remove this!
/*
else {
System.out.println("The array base type " + baseType.debugName() + " cannot be instantiated");
}
*/
/*
            // This outputs the declartion for the whole class.
            // System.out.println("Inside of generateType(TypeReference) ArrayBinding baseType   = " + baseType);
            if (verboseLevel > 1) {
                System.out.println("Inside of generateType(TypeReference) ArrayBinding baseType (debugName) = " + baseType.debugName());
                System.out.println("Inside of generateType(TypeReference) recursive call to generateType()");
            }
// charles4: 02/24/2012 12:57AM   -- Replace this call by the one below it.               
//               generateType(baseType);
            String package_name = new String(baseType.getPackage().readableName()),
                   simple_name = baseType.debugName().substring(package_name.length() == 0 ? 0 : package_name.length() + 1);

// TODO: REMOVE THIS
//System.out.println("**+ The package is: " + package_name + ";  The class name is " + simple_name);

            JavaParser.cactionGenerateType(package_name,
                                           baseType.debugName().substring(package_name.length() == 0 ? 0 : package_name.length() + 1),
                                           arrayType.dimensions());
        }
        else {
            // NOTE: It would be more elegant to not depend upon the debugName() function.
            String name = node.resolvedType.debugName();
            if (node.resolvedType.canBeInstantiated()) {
                preprocessClass(node.resolvedType.debugName());
/*
            if (verboseLevel > 1)
                System.out.println("Inside of generateType(TypeReference): NOT an array type so build SgIntType -- TypeReference node = " + name);

            // DQ (8/20/2011): Moved to be after buildImplicitClassSupport().
            // JavaParser.cactionGenerateType(name);

            if (verboseLevel > 1)
                System.out.println("After building the class we have to build the data members and member functions (built type name " + name + " by default) in generateType(TypeReference)");

            // System.out.println("Calling processType() to recursively build the class structure with member declarations.");
            // This does not work...
            // processType(node.resolvedType);

            // DQ (8/20/2011): Need a better way to handle detecting if this is an implicit class...
            // Maybe we could detect if it is a supported type in the global type map.

            // DQ (8/22/2011): The reason why we need this is that the import statement allows for the names to be used unqualified.
            // Once we implement proper support for the import statement then we will be able to search the symbol tables for any type
            // names that we can't identify because they lack name qualification!!!

            // If this is a generic type then the "<name>" has to be separated so we can use only the base name of the class (the raw type name).
            String rawTypeName = name;

            int firstAngleBracket = rawTypeName.indexOf("<",0);
            int lastAngleBracket = rawTypeName.lastIndexOf(">",rawTypeName.length()-1);

            // System.out.println("In generateType(TypeReference): firstAngleBracket = " + firstAngleBracket + " lastAngleBracket = " + lastAngleBracket);
            if (firstAngleBracket > 0 && firstAngleBracket < lastAngleBracket) {
                    rawTypeName = rawTypeName.substring(0,firstAngleBracket);

                    name = rawTypeName;
            }
*/
  /*
                // DQ (8/20/2011): Moved to be after buildImplicitClassSupport().
                String package_name = new String(node.resolvedType.getPackage().readableName()),
                       simple_name = node.resolvedType.debugName().substring(package_name.length() == 0 ? 0 : package_name.length() + 1);

// TODO: REMOVE THIS
//System.out.println("**- The package is: " + package_name + ";  The class name is " + simple_name);

                JavaParser.cactionGenerateType(package_name, simple_name, 0);

            // System.out.println("Exiting as a test (built type name " + name + " by default) in generateType(TypeReference)");
            // System.exit(1);
            }
// TODO: Remove this!
/*
else {
System.out.println("The type " + node.resolvedType.debugName() + " cannot be instantiated");
}
*/
   /*
        }
// TODO: REMOVE THIS
//System.out.println("returning !!!");
    }
    */


    public void generateAndPushType(Class cls) {
        TypeDeclaration node = userTypeTable.get(cls);
        JavaToken location = (node != null
                                    ? createJavaToken(node)
                                    : new JavaToken("Dummy JavaToken (see createJavaToken)", new JavaSourcePositionInformation(0)));
 
        // This function is used to build types that are classes (implicit classes 
        // that already exist (have been built) and thus just need be found and a 
        // reference put onto the astJavaTypeStack).
        if (verboseLevel > 0)
            System.out.println("Inside of generateAndPushType(Class) for class = " + cls);

        if (! cls.isPrimitive()) {
            // Investigate any new type.
            if (cls.isArray() == true) {
                // DQ (3/21/2011): If this is an array of some type then we have to query the base type and for now I will skip this.
                // System.out.println("Skipping case of array of type for now (sorry not implemented)... " + node.getComponentType());

                // Build an integer type instead of an array of the proper type (temporary fix so that I can focus on proper class support).
                // JavaParser.cactionGenerateType("int");
                int num_dimensions = 0;
                Class n = cls;
                while (n.isArray()) {
                     num_dimensions++;
                     n = n.getComponentType();
                }

                String canonical_name = n.getCanonicalName(),
                       class_name = n.getName(),
                       simple_name = n.getSimpleName(),
                       package_name = getMainPackageName(n, 2),
                       type_name = canonical_name.substring(package_name.length() == 0 ? 0 : package_name.length() + 1);
// TODO: REMOVE THIS
//System.out.println("(3) The canonical name is: " + node.getCanonicalName() +
//         "; The package is: " + package_name + ";  The class name is " + class_name + ";  The simple class name is " + simple_name + ";  The type name is " + type_name);
//
//                JavaParser.cactionGenerateType(package_name, type_name, num_dimensions);

                JavaParser.cactionArrayTypeReference(package_name, type_name, num_dimensions, location);
            }
            else {
                // Note that "toString()" inserts "class" into the generated name of the type (so use "getName()").
                String className = cls.getName();

                // If this is a class type (e.g. in the C++ sense) then we want to build a proper SgClassType IR node.
                // System.out.println("Build a proper class for this type = " + node);
                // System.out.println("Build a proper class for this type = " + className);

                // We know that this name should be interpreted as a proper class so we need to call a specific JNI function to cause it to be generated on the C++ side.
                // JavaParser.cactionGenerateType(className);
                String canonical_name = cls.getCanonicalName(),
                       class_name = cls.getName(),
                       simple_name = cls.getSimpleName(),
                       package_name = getMainPackageName(cls, 3),
                       type_name = canonical_name.substring(package_name.length() == 0 ? 0 : package_name.length() + 1);
// TODO: REMOVE THIS
//System.out.println("(4) The canonical name is: " + node.getCanonicalName() +
//         "; The package is: " + package_name  + ";  The class name is " + class_name + ";  The simple class name is " + simple_name + ";  The type name is " + type_name);
//
//                JavaParser.cactionGenerateType(package_name, type_name, 0);

                JavaParser.cactionTypeReference(package_name, type_name, location);
                // System.out.println("Exiting as a test in generateType(Class) (case of proper class)");
                // System.exit(1);
            }
        }
        else {
            if (verboseLevel > 0)
                System.out.println("Build a primitive type: int ");

            String type_name = cls.getName();

// TODO: REMOVE THIS
//            JavaParser.cactionGenerateType("" /* primitive type has no package */ , type_name, 0);

            JavaParser.cactionTypeReference("", type_name, location);
        }

        if (verboseLevel > 0)
            System.out.println("Leaving generateAndPushType(Class) (case of proper class)");
    }


    /**
     * 
     * @param type_binding
     */
    public void generateAndPushType(TypeBinding type_binding, JavaToken location) {
        if (type_binding instanceof ArrayBinding) {
            ArrayBinding arrayType = (ArrayBinding) type_binding;
            TypeBinding baseType = arrayType.leafComponentType;

            String package_name = getPackageName(baseType),
                   type_name = getTypeName(baseType);

// TODO: REMOVE THIS
//System.out.println("**- The package is: " + package_name + ";  The class name is " + type_name);
     
            JavaParser.cactionArrayTypeReference(package_name, type_name, arrayType.dimensions(), location);
        }
        else {
            String package_name = getPackageName(type_binding),
                   type_name = getTypeName(type_binding);

// TODO: REMOVE THIS
//System.out.println("**- The package is: " + package_name + ";  The class name is " + type_name);

            JavaParser.cactionTypeReference(package_name, type_name, location);
        }
    }


// -------------------------------------------------------------------------------------------
    
    public void translate(CompilationUnitDeclaration unit) {
        // Debugging support...
        if (verboseLevel > 0)
            System.out.println("Start parsing");

        try {
            // Make a copy of the compiation unit so that we can compute source code positions.
            // rose_compilationResult = unit.compilationResult;
//TODO: REMOVE THIS !?
//        JavaParserSupport.initialize(unit.compilationResult,verboseLevel);

            // Example of how to call the traversal using a better design that isolates out the traversal of the ECJ AST from the parser.
            // "final" is required because the traverse function requires the visitor to be final.
            // final ecjASTVisitor visitor = new ecjASTVisitor(this);
System.out.println("Translating unit " + new String(unit.getFileName()));
            this.posFactory = new JavaSourcePositionInformationFactory(unit);
            ecjASTVisitor ecjVisitor = new ecjASTVisitor(unit, this);
            unit.traverse(ecjVisitor, unit.scope);
        }
        catch (Throwable e) {
            e.printStackTrace();
            System.out.println("Caught error in JavaParser (Parser failed)");
            System.err.println(e);

            // Make sure we exit as quickly as possible to simplify debugging.
            System.exit(1);

            // Make sure we exit on any error so it is caught quickly.
            // System.exit(1);

            // throw e;
            return;
        }

        // Debugging support...
        if (verboseLevel > 0)
            System.out.println("Done parsing");
    }
}
