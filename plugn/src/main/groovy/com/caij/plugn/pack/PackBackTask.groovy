package com.caij.plugn.pack

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

/**
 * The configuration properties.
 *
 * @author Sim Sun (sunsj1231@gmail.com)
 */
class PackBackTask extends DefaultTask {
    def buildConfigs = []
    PackExtension packExtension;

    PackBackTask() {
        group = 'pack'
        def android = project.extensions.android
        packExtension = project.pack
        android.applicationVariants.all { variant ->
            variant.outputs.each { output ->
                String variantName = this.name["pack".length()..-1]
                if (variantName.equalsIgnoreCase(variant.buildType.name as String) || isTargetFlavor(variantName,
                        variant.productFlavors, variant.buildType.name)) {
                    buildConfigs << new BuildInfo(output.outputFile,
                            variant.variantData.variantConfiguration.signingConfig,
                            variant.variantData.variantConfiguration.applicationId,
                            variant.buildType.name,
                            variant.productFlavors,
                            variantName,
                            variant.mergedFlavor.minSdkVersion.apiLevel,
                            variant.versionName,
                            variant.versionCode)
                }
            }
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

            String dir = useFolder(config.file)
            String apkBasename = config.file.getName()
            apkBasename = apkBasename.substring(0, apkBasename.indexOf(".apk"));
            File resFile = new File(dir, apkBasename + "_7zip_aligned_signed.apk")
            println("res file " + resFile.getAbsolutePath())

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
            File resDir = new File(outPutDir, "/resguard")
            File backDir = new File(outPutDir, "/backup-" + suf)

            File resApkFile = new File(backDir, "resguard-" + apkBasename + ".apk")
            println("out file " + resApkFile.getAbsolutePath())

            copyFileUsingStream(resFile, resApkFile)

            String resMappingFileName = "resource_mapping_" + apkBasename + ".txt"
            File resMappingFile = new File(dir, resMappingFileName)
            File resMappingFileBack = new File(backDir, resMappingFileName)
            copyFileUsingStream(resMappingFile, resMappingFileBack)

            if (buildType == "release") {
                String walleCommand = "java" + " -jar" + " " + packExtension.walleJarPath + " batch" + " -f " + packExtension.channelFilePath + " " + resApkFile.getAbsolutePath() + " " + resDir.getAbsolutePath()
                execCommand(walleCommand)

                File mappingFile = new File("${project.buildDir}/outputs/mapping/" + flavorName + "/" + buildType + "/mapping.txt")
                File mappingFileBack = new File(backDir, "mapping.txt")
                if (mappingFile.exists()) {
                    copyFileUsingStream(mappingFile, mappingFileBack)
                }

                String updateLogFile = "${project.projectDir}/update.txt"
                File updateFileBack = new File(backDir, "update.txt")
                if (new File(updateLogFile).exists()) {
                    copyFileUsingStream(new File(updateLogFile), updateFileBack)
                }

                if (packExtension.isApkCanary) {
                    File analyzeFile = new File(backDir, "apk-checker-result");
                    String apkCanaryJson = getFileString(packExtension.apkCanaryJsonPath)
                    File resRFile = new File(project.buildDir, "/intermediates/symbols/" + flavorName + "/" + buildType + "/R.txt");
                    String resultJson = String.format(apkCanaryJson, resApkFile.getAbsolutePath(), mappingFileBack.getAbsolutePath(), resMappingFileBack.getAbsolutePath(), analyzeFile.getAbsolutePath(), resRFile.getAbsolutePath())
                    println(resultJson)

                    File resultJsonFile = new File("${project.buildDir}/apk-canary", "apk_config.json")
                    saveAsFileWriter(resultJsonFile, "hahahahah")
                    String apkCanaryCommand = "java " + "-jar " + packExtension.apkCanaryJarPath + " --config " + "CONFIG-FILE_PATH " + resultJsonFile.getAbsolutePath()

//                    execCommand(apkCanaryCommand)
                }
            }
        }
    }

    static void execCommand(String command) {
        println(command)
        Process proc = command.execute();
        BufferedReader stdInput = new BufferedReader(new
                InputStreamReader(proc.getInputStream()));

        BufferedReader stdError = new BufferedReader(new
                InputStreamReader(proc.getErrorStream()));

        //这里必须加输出日志 否则apk canary不成功  咱不知道什么问题 考虑是gradle完成 进程被杀 所以导致不成功
        println("Here is the standard output of the command:\n");
        String s = null;
        while ((s = stdInput.readLine()) != null) {
            println(s);
        }

        println("Here is the standard error of the command (if any):\n");
        while ((s = stdError.readLine()) != null) {
            println(s);
        }
        proc.waitForProcessOutput()
    }

    static void saveAsFileWriter(File file, String content) {
        FileWriter fwriter = null;
        try {
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs()
            }
            fwriter = new FileWriter(file);
            fwriter.write(content);
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            try {
                fwriter.flush();
                fwriter.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    static String getFileString(String path) {
        try {
            FileInputStream inStream= new FileInputStream(path);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buffer=new byte[1024];
            int length = -1;
            while( (length = inStream.read(buffer)) != -1) {
                bos.write(buffer,0,length);
            }
            bos.close();
            inStream.close();
            return bos.toString();
        } catch (Exception e){

        }
        return null;
    }


    static useFolder(file) {
        //remove .apk from filename
        def fileName = file.name[0..-5]
        return "${file.parent}/AndResGuard_${fileName}/"
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