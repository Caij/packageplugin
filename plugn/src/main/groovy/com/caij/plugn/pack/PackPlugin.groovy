package com.caij.plugn.pack

import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task

/**
 * Created by zhangshaowen on 17/6/16.
 */
class PackPlugin implements Plugin<Project> {
    private static final String TAG = "PackPlugin"

    @Override
    void apply(Project project) {
        project.extensions.create("pack", PackExtension)
        PackExtension packExtension = project.pack
        if (!project.plugins.hasPlugin('com.android.application')) {
            throw new GradleException('Systrace Plugin, Android Application plugin required')
        }

        project.afterEvaluate {
            def android = project.extensions.android

            android.applicationVariants.all { variant ->
                def variantName = variant.name.capitalize()
                createTask(project, variantName, packExtension.isResGuard)
            }

            android.buildTypes.all { buildType ->
                def buildTypeName = buildType.name.capitalize()
                createTask(project, buildTypeName, packExtension.isResGuard)
            }

            android.productFlavors.all { flavor ->
                def flavorName = flavor.name.capitalize()
                createTask(project, flavorName, packExtension.isResGuard)
            }
        }
    }

    private static void createTask(Project project, variantName, boolean isResGuard) {
        def buildTaskName
        def taskName = "pack${variantName}"
        def task = project.tasks.findByPath(taskName)
        if (task == null) {
            task = project.task(taskName, type: PackBackTask)
            if (isResGuard) {
                buildTaskName = "resguard${variantName}"
            } else {
                buildTaskName = "assemble${variantName}"
            }

            task.dependsOn buildTaskName

            def buildTask = project.tasks.findByName(buildTaskName)
            buildTask.dependsOn "clean"
        }
    }



}
