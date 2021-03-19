package com.caij.plugn.pack

import com.google.gson.Gson
import com.meituan.android.walle.ChannelWriter
import com.meituan.android.walle.WalleConfig
import org.apache.commons.io.FileUtils
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.TaskAction

/**
 * The configuration properties.
 *
 * @author Sim Sun (sunsj1231@gmail.com)
 */
class PackBackTask extends DefaultTask {
    def buildConfigs = []
    PackExtension packExtension;
    def android

    PackBackTask() {
        group = 'pack'
        android = project.extensions.android
        packExtension = project.pack
        android.applicationVariants.all { variant ->
            variant.outputs.each { output ->
                // remove "resguard"
                String variantName = this.name["pack".length()..-1]
                if (variantName.equalsIgnoreCase(variant.buildType.name as String) || isTargetFlavor(variantName,
                        variant.productFlavors, variant.buildType.name)) {

                    def outputFile = null
                    try {
                        if (variant.metaClass.respondsTo(variant, "getPackageApplicationProvider")) {
                            outputFile = new File(variant.packageApplicationProvider.get().outputDirectory, output.outputFileName)
                        }
                    } catch (Exception ignore) {
                        // no-op
                    } finally {
                        outputFile = outputFile ?: output.outputFile
                    }

                    def variantInfo
                    if (variant.variantData.hasProperty("variantConfiguration")) {
                        variantInfo = variant.variantData.variantConfiguration
                    } else {
                        variantInfo = variant.variantData.variantDslInfo
                    }



                    def applicationId = variantInfo.applicationId instanceof Property
                            ? variantInfo.applicationId.get()
                            : variantInfo.applicationId

                    buildConfigs << new BuildInfo(
                            outputFile,
                            variantInfo.signingConfig,
                            applicationId,
                            variant.buildType.name,
                            variant.productFlavors,
                            variantName,
                            variant.mergedFlavor.minSdkVersion.apiLevel,
                            variant.mergedFlavor.targetSdkVersion.apiLevel,
                            variant.versionName,
                            variant.versionCode,
                            variant.mappingFile
                    )
                }
            }
        }
        if (!project.plugins.hasPlugin('com.android.application')) {
            throw new GradleException('generateARGApk: Android Application plugin required')
        }
    }

    static isTargetFlavor(variantName, flavors, buildType) {
        if (flavors.size() > 0) {
            String flavor = flavors.get(0).name
            return variantName.equalsIgnoreCase(flavor) || variantName.equalsIgnoreCase([flavor, buildType].join(""))
        }
        return false
    }

    @TaskAction
    run() {
        buildConfigs.each { config ->
            if (config.file == null || !config.file.exists()) {
                throw new IOException("Original APK not existed")
            }

            String apkBasename = config.file.getName()
            apkBasename = apkBasename.substring(0, apkBasename.indexOf(".apk"));

            println(config.toString())

            String flavorName = config.flavors[0].name
            String buildType = config.buildType;

            String suf = flavorName + "-" + buildType + "-" + config.versionName + "-" + config.versionCode;
            String outPutPatent;
            if (packExtension.outFileDir == null || packExtension.outFileDir == "") {
                outPutPatent = project.projectDir.getAbsolutePath()
            } else {
                outPutPatent = packExtension.outFileDir;
            }
            File outPutDir = new File(outPutPatent, suf)

            File backDir = new File(outPutDir, "/backup-" + suf)

            File sourceApkFileBack = new File(backDir, apkBasename + ".apk")
            copyFileUsingStream(config.file, sourceApkFileBack)

            File mappingFile = config.mappingFile
            File mappingFileBack = new File(backDir, "mapping.txt")
            if (mappingFile.exists()) {
                copyFileUsingStream(mappingFile, mappingFileBack)
            }

            File resultFile = sourceApkFileBack;

            String apkName = resultFile.getName();
            apkName = apkName.substring(0, apkName.indexOf(".apk"));

            //resource guard
            if (packExtension.isResGuard) {
                String resMappingFileName = "resource_mapping_" + apkName + ".txt"
                File resMappingFile = new File(config.file.getParentFile(), "/AndResGuard_" + apkName + "/" + resMappingFileName)
                println("res mapping file " + resMappingFile.exists())
            }

            //walle
            if (packExtension.isWalle) {
                File channelFileDir = new File(outPutDir, "channel")
                if (packExtension.channelConfigFile != null && packExtension.channelConfigFile.length() > 0) {
                    generateChannelApkByConfigFile(new File(packExtension.channelConfigFile), resultFile, channelFileDir, )
                } else if (packExtension.channelFile != null && packExtension.channelFile.length() > 0){
                    generateChannelApkByChannelFile(new File(packExtension.channelFile), resultFile, channelFileDir)
                }

            }
        }
    }

    def generateChannelApkByChannelFile(File channelFile, File apkFile, File channelOutputFolder) {
        getChannelListFromFile(channelFile).each { channel -> generateChannelApk(apkFile, channelOutputFolder, channel, null, null) }
    }

    private static final String DOT_APK = ".apk";

    def generateChannelApk(File apkFile, File channelOutputFolder, channel, extraInfo, alias) {
        def channelName = alias == null ? channel : alias

        String fileName = apkFile.getName();
        if (fileName.endsWith(DOT_APK)) {
            fileName = fileName.substring(0, fileName.lastIndexOf(DOT_APK));
        }

        String apkFileName = "${fileName}-${channelName}${DOT_APK}";

        File channelApkFile = new File(apkFileName, channelOutputFolder);
        FileUtils.copyFile(apkFile, channelApkFile);
        ChannelWriter.put(channelApkFile, channel, extraInfo)
    }

    static def getChannelListFromFile(File channelFile) {
        def channelList = []
        channelFile.eachLine { line ->
            def lineTrim = line.trim()
            if (lineTrim.length() != 0 && !lineTrim.startsWith("#")) {
                def channel = line.split("#").first().trim()
                if (channel.length() != 0)
                    channelList.add(channel)
            }
        }
        return channelList
    }


    def generateChannelApkByConfigFile(File configFile, File apkFile, File channelOutputFolder) {
        WalleConfig config = new Gson().fromJson(new InputStreamReader(new FileInputStream(configFile), "UTF-8"), WalleConfig.class)
        def defaultExtraInfo = config.getDefaultExtraInfo()
        config.getChannelInfoList().each { channelInfo ->
            def extraInfo = channelInfo.extraInfo
            if (!channelInfo.excludeDefaultExtraInfo) {
                switch (config.defaultExtraInfoStrategy) {
                    case WalleConfig.STRATEGY_IF_NONE:
                        if (extraInfo == null) {
                            extraInfo = defaultExtraInfo
                        }
                        break;
                    case WalleConfig.STRATEGY_ALWAYS:
                        def temp = new HashMap<String, String>()
                        if (defaultExtraInfo != null) {
                            temp.putAll(defaultExtraInfo)
                        }
                        if (extraInfo != null) {
                            temp.putAll(extraInfo)
                        }
                        extraInfo = temp
                        break;
                    default:
                        break;
                }
            }

            generateChannelApk(apkFile, channelOutputFolder, channelInfo.channel, extraInfo)
        }
    }



    static void copyFileUsingStream(File source, File dest) throws IOException {
        FileInputStream is = null;
        FileOutputStream os = null;
        File parent = dest.getParentFile();
        if (parent != null && (!parent.exists())) {
            parent.mkdirs();
        }
        try {
            is = new FileInputStream(source);
            os = new FileOutputStream(dest, false);

            byte[] buffer = new byte[4 * 1024];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
        } finally {
            if (is != null) {
                is.close();
            }
            if (os != null) {
                os.close();
            }
        }
    }
}