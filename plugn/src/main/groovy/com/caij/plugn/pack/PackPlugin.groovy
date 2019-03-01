package com.caij.plugn.pack

import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskAction

/**
 * Created by zhangshaowen on 17/6/16.
 */
class PackPlugin implements Plugin<Project> {
    private static final String TAG = "PackPlugin"

    @Override
    void apply(Project project) {
        project.extensions.create("pack", PackExtension)

        if (!project.plugins.hasPlugin('com.android.application')) {
            throw new GradleException('Systrace Plugin, Android Application plugin required')
        }

        project.afterEvaluate {
            def android = project.extensions.android

            android.applicationVariants.all { variant ->
                def variantName = variant.name.capitalize()
                createTask(project, variantName)
            }

            android.buildTypes.all { buildType ->
                def buildTypeName = buildType.name.capitalize()
                createTask(project, buildTypeName)
            }

            android.productFlavors.all { flavor ->
                def flavorName = flavor.name.capitalize()
                createTask(project, flavorName)
            }
        }
    }

    private static void createTask(Project project, variantName) {
        def taskName = "pack${variantName}"
        if (project.tasks.findByPath(taskName) == null) {
            def task = project.task(taskName, type: PackTasj)
            task.dependsOn "resguard${variantName}"
        }
    }



}
