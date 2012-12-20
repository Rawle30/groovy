/*
 * Copyright 2003-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codehaus.groovy.classgen.asm.sc

import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer
import groovy.transform.CompileStatic
import org.codehaus.groovy.control.CompilationUnit
import java.security.CodeSource
import org.codehaus.groovy.control.customizers.CompilationCustomizer
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.ast.ClassNode
import org.objectweb.asm.ClassReader
import org.objectweb.asm.util.CheckClassAdapter
import org.codehaus.groovy.control.customizers.ImportCustomizer
import org.codehaus.groovy.tools.GroovyClass

/**
 * A mixin class which can be used to transform a static type checking test case into a
 * static compilation test case.
 *
 * On the beginning of a test method, it initializes a property which is available
 * to the developper for additional tests:
 * <ul>
 *     <li>astTrees: a map which has for key the names of classes generated in assertScript and an array of length 2
 *     as a value, for which the first element is the generated AST tree of the class, and the second element is
 *     the result of the ASM check class adapter string, which can be used to verify generated bytecode.</li>
 * </ul>
 *
 * @author Cedric Champeau
 */
class StaticCompilationTestSupport {
    Map<String, Object[]> astTrees
    CustomCompilationUnit compilationUnit

    void extraSetup() {
        astTrees = [:]
        def mixed = metaClass.owner
        mixed.config = new CompilerConfiguration()
        def imports = new ImportCustomizer()
        imports.addImports(
                'groovy.transform.ASTTest', 'org.codehaus.groovy.transform.stc.StaticTypesMarker',
                'org.codehaus.groovy.ast.ClassHelper'
        )
        imports.addStaticStars('org.codehaus.groovy.control.CompilePhase')
        imports.addStaticStars('org.codehaus.groovy.transform.stc.StaticTypesMarker')
        imports.addStaticStars('org.codehaus.groovy.ast.ClassHelper')
        mixed.config.addCompilationCustomizers(imports,new ASTTransformationCustomizer(CompileStatic), new ASTTreeCollector())
        mixed.configure()
        mixed.shell = new GroovyShell(mixed.config)
        // trick because GroovyShell doesn't allow to provide our own GroovyClassLoader
        // to be fixed when this will be possible
        mixed.shell.loader = new GroovyClassLoader(this.class.classLoader, mixed.config) {
            @Override
            protected CompilationUnit createCompilationUnit(final CompilerConfiguration config, final CodeSource source) {
                def cu = new CustomCompilationUnit(config, source, this)
                setCompilationUnit(cu)
                return cu
            }
        }
    }

    private class CustomCompilationUnit extends CompilationUnit {
        CustomCompilationUnit(final CompilerConfiguration configuration, final CodeSource security, final GroovyClassLoader loader) {
            super(configuration, security, loader)
        }

    }

    private class ASTTreeCollector extends CompilationCustomizer {

        ASTTreeCollector() {
            super(CompilePhase.CLASS_GENERATION)
        }

        @Override
        void call(final org.codehaus.groovy.control.SourceUnit source, final org.codehaus.groovy.classgen.GeneratorContext context, final ClassNode classNode) {
            def unit = getCompilationUnit()
            if (!unit) return
            List<GroovyClass> classes = unit.generatedClasses
            classes.each { GroovyClass groovyClass ->
                StringWriter stringWriter = new StringWriter()
                try {
                    ClassReader cr = new ClassReader(groovyClass.bytes)
                    CheckClassAdapter.verify(cr, source.getClassLoader(), true, new PrintWriter(stringWriter))
                } catch (Throwable e)  {
                    // not a problem
                    e.printStackTrace(new PrintWriter(stringWriter))
                }
                getAstTrees()[groovyClass.name] = [classNode, stringWriter.toString()] as Object[]
            }
        }
    }

}
