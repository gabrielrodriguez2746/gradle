/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.tasks.compile.daemon;

import com.google.common.collect.Iterables;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.tasks.compile.BaseForkOptionsConverter;
import org.gradle.api.internal.tasks.compile.GroovyJavaJointCompileSpec;
import org.gradle.api.tasks.compile.ForkOptions;
import org.gradle.api.tasks.compile.GroovyForkOptions;
import org.gradle.internal.jvm.GroovyJpmsWorkarounds;
import org.gradle.internal.jvm.inspection.JvmVersionDetector;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.process.JavaForkOptions;
import org.gradle.process.internal.JavaForkOptionsFactory;
import org.gradle.workers.internal.DaemonForkOptions;
import org.gradle.workers.internal.DaemonForkOptionsBuilder;
import org.gradle.workers.internal.KeepAliveMode;
import org.gradle.workers.internal.WorkerFactory;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;

public class DaemonGroovyCompiler extends AbstractDaemonCompiler<GroovyJavaJointCompileSpec> {
    private final static Iterable<String> SHARED_PACKAGES = Arrays.asList("groovy", "org.codehaus.groovy", "groovyjarjarantlr", "groovyjarjarasm", "groovyjarjarcommonscli", "org.apache.tools.ant", "com.sun.tools.javac");
    private final ClassPathRegistry classPathRegistry;
    private final JavaForkOptionsFactory forkOptionsFactory;
    private final File daemonWorkingDir;
    private final JvmVersionDetector jvmVersionDetector;

    public DaemonGroovyCompiler(File daemonWorkingDir, Compiler<GroovyJavaJointCompileSpec> delegate, ClassPathRegistry classPathRegistry, WorkerFactory workerFactory, JavaForkOptionsFactory forkOptionsFactory, JvmVersionDetector jvmVersionDetector) {
        super(delegate, workerFactory);
        this.classPathRegistry = classPathRegistry;
        this.forkOptionsFactory = forkOptionsFactory;
        this.daemonWorkingDir = daemonWorkingDir;
        this.jvmVersionDetector = jvmVersionDetector;
    }

    @Override
    protected DaemonForkOptions toDaemonForkOptions(GroovyJavaJointCompileSpec spec) {
        ForkOptions javaOptions = spec.getCompileOptions().getForkOptions();
        GroovyForkOptions groovyOptions = spec.getGroovyCompileOptions().getForkOptions();
        // Ant is optional dependency of groovy(-all) module but mandatory dependency of Groovy compiler;
        // that's why we add it here. The following assumes that any Groovy compiler version supported by Gradle
        // is compatible with Gradle's current Ant version.
        Collection<File> antFiles = classPathRegistry.getClassPath("ANT").getAsFiles();
        // TODO We should infer a minimal classpath from delegate instead
        Collection<File> languageGroovyFiles = classPathRegistry.getClassPath("LANGUAGE-GROOVY").getAsFiles();
        Iterable<File> classpath = Iterables.concat(spec.getGroovyClasspath(), antFiles, languageGroovyFiles);

        JavaForkOptions javaForkOptions = new BaseForkOptionsConverter(forkOptionsFactory).transform(mergeForkOptions(javaOptions, groovyOptions));
        javaForkOptions.setWorkingDir(daemonWorkingDir);
        if (jvmVersionDetector.getJavaVersion(javaForkOptions.getExecutable()).isJava9Compatible()) {
            javaForkOptions.jvmArgs(GroovyJpmsWorkarounds.SUPPRESS_COMMON_GROOVY_WARNINGS);
        }

        return new DaemonForkOptionsBuilder(forkOptionsFactory)
            .javaForkOptions(javaForkOptions)
            .classpath(classpath)
            .sharedPackages(SHARED_PACKAGES)
            .keepAliveMode(KeepAliveMode.SESSION)
            .withoutGradleApi()
            .build();
    }
}
