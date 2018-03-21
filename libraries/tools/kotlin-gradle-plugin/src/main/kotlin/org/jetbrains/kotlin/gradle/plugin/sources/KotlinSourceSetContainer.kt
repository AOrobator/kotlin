/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.sources

import org.gradle.api.Project
import org.gradle.api.internal.AbstractNamedDomainObjectContainer
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.tasks.DefaultSourceSetOutput
import org.gradle.api.internal.tasks.TaskResolver
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.internal.reflect.Instantiator
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetProvider
import org.jetbrains.kotlin.gradle.plugin.addConvention
import org.jetbrains.kotlin.gradle.plugin.source.KotlinSourceSet
import java.io.File

abstract class KotlinSourceSetContainer<T : KotlinSourceSet> internal constructor(
    itemClass: Class<T>,
    instantiator: Instantiator,
    protected val fileResolver: FileResolver,
    protected val project: Project
) : AbstractNamedDomainObjectContainer<T>(itemClass, instantiator), KotlinSourceSetProvider {
    protected open fun defaultSourceRoots(sourceSetName: String): List<File> =
        listOf(project.file("src/$sourceSetName/kotlin"))

    protected open fun setUpSourceSetDefaults(sourceSet: T) {
        with(sourceSet) {
            sourceSet.kotlin.srcDirs(defaultSourceRoots(sourceSet.name))
        }
    }

    protected abstract fun doCreateSourceSet(name: String): T

    final override fun doCreate(name: String): T {
        val result = doCreateSourceSet(name)
        setUpSourceSetDefaults(result)
        return result
    }

    final override fun provideSourceSet(displayName: String): KotlinSourceSet =
        maybeCreate(displayName)
}

class KotlinJavaSourceSetContainer internal constructor(
    instantiator: Instantiator,
    project: Project,
    fileResolver: FileResolver
) : KotlinSourceSetContainer<KotlinJavaSourceSet>(KotlinJavaSourceSet::class.java, instantiator, fileResolver, project) {

    override fun doCreateSourceSet(name: String): KotlinJavaSourceSet {
        findByName(name)?.let { return it }

        val javaSourceSet = javaSourceSetContainer.maybeCreate(name)

        val result = KotlinJavaSourceSet(name, fileResolver, javaSourceSet)

        javaSourceSet.addConvention("kotlin", result)

        return result
    }

    private val javaSourceSetContainer: SourceSetContainer
        get() = project.convention.getPlugin(JavaPluginConvention::class.java).sourceSets
}

class KotlinOnlySourceSetContainer(
    project: Project,
    fileResolver: FileResolver,
    instantiator: Instantiator,
    private val taskResolver: TaskResolver,
    private val platformName: String = ""
) : KotlinSourceSetContainer<KotlinOnlySourceSet>(KotlinOnlySourceSet::class.java, instantiator, fileResolver, project) {

    override fun doCreateSourceSet(name: String): KotlinOnlySourceSet {
        val newSourceSetOutput = instantiator.newInstance(
            DefaultSourceSetOutput::class.java, name, fileResolver, taskResolver
        )

        return KotlinOnlySourceSet(name, fileResolver, newSourceSetOutput, project, platformName)
    }
}