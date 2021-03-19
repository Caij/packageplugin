package com.caij.plugn.pack;


class BuildInfo {
    def file
    def signConfig
    def packageName
    def buildType
    def flavors
    def taskName
    int minSDKVersion
    int targetSDKVersion
    def versionName
    def versionCode


    BuildInfo(file, sign, packageName, buildType, flavors, taskName, minSDKVersion, targetSDKVersion,
              def versionName, def versionCode) {
        this.file = file
        this.signConfig = sign
        this.packageName = packageName
        this.buildType = buildType
        this.flavors = flavors
        this.taskName = taskName
        this.minSDKVersion = minSDKVersion
        this.targetSDKVersion = targetSDKVersion
        this.versionName = versionName
        this.versionCode = versionCode
    }

    @Override
    String toString() {
        """| file = ${file}
       | packageName = ${packageName}
       | buildType = ${buildType}
       | flavors = ${flavors}
       | taskname = ${taskName}
       | minSDKVersion = ${minSDKVersion}
       | targetSDKVersion = ${targetSDKVersion}
    """.stripMargin()
    }
}