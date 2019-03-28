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
    def android

    PackBackTask() {
        group = 'pack'
        android = project.extensions.android
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

            File mappingFile = new File("${project.buildDir}/outputs/mapping/" + flavorName + "/" + buildType + "/mapping.txt")
            File mappingFileBack = new File(backDir, "mapping.txt")
            if (mappingFile.exists()) {
                copyFileUsingStream(mappingFile, mappingFileBack)
            }


            File resultFile;

            //redex
            if (packExtension.isRedex) {
                File redexFile = new File(backDir, apkBasename + "-redex.apk");
                File coolStartFileBuild = new File(project.buildDir, "redex/class_list.txt");
                File jsonCinfigFileBuild = new File(project.buildDir, "redex/default.config");
                // release 将混淆mapping对应起来

                Map<String, String> mappingMap = readMappingFile(mappingFileBack);

                File coolStartClassFile = new File(packExtension.redexCoolStartath);

                println(coolStartClassFile.getAbsolutePath());

                if (!coolStartFileBuild.getParentFile().exists()) {
                    coolStartFileBuild.getParentFile().mkdirs();
                }
                FileWriter fw = new FileWriter(coolStartFileBuild);

                coolStartClassFile.eachLine {
                    String line = it;

                    String className = line.replace(".class", "");
                    className = className.replace("/", ".");
                    String reguardClassName = mappingMap.get(className);
                    String result;
                    if (reguardClassName == null) {
                        result = className;
                    } else {
                        result = reguardClassName;
                    }

                    result = result.replace(".", "/");

                    fw.write(result+ ".class" + "\n");
                }

                fw.close();

                String redexConfigJson = getFileString(packExtension.redexConfigPath);
                String resultJson = String.format(redexConfigJson, coolStartFileBuild.getAbsolutePath());

                println(resultJson)

                saveAsFileWriter(jsonCinfigFileBuild, resultJson)


                execCommand("redex", sourceApkFileBack.getAbsolutePath(), "-c", jsonCinfigFileBuild.getAbsolutePath(), "-m", mappingFile.getAbsolutePath(), "-o", redexFile.getAbsolutePath())

                //sign redex
                File signApk = new File(backDir, apkBasename + "-redex-signed.apk")
                def signConfig = config.signConfig
                File signFile = signConfig.storeFile;
                execCommand("java", "-jar", getSignPath(), "sign", "--ks", signFile.getAbsolutePath(), "--ks-key-alias", signConfig.keyAlias, "--ks-pass", "pass:" + signConfig.storePassword, "--key-pass", "pass:" + signConfig.keyPassword, "--out", signApk.getAbsolutePath(), redexFile.getAbsolutePath())

                resultFile = signApk;

                redexFile.delete()
            } else {
                resultFile = sourceApkFileBack;
            }

            String apkName = resultFile.getName();
            apkName = apkName.substring(0, apkName.indexOf(".apk"));

            //resource guard
            String resMappingFileName = "resource_mapping_" + apkName + ".txt"
            File resMappingFileBack = new File(backDir, resMappingFileName)
            if (packExtension.isResGuard) {
                File resDir = new File(outPutDir, "/resguard");
//                java -jar andresguard.jar input.apk -config yourconfig.xml -out output_directory
                execCommand("java", "-jar", packExtension.resGuardJarPath, resultFile.getAbsolutePath(), "-config", packExtension.resGuardConfigPath, "-7zip", packExtension.zipPath, "-zipalign", getZipAlignPath(), "-signatureType", "v2", "-out", resDir.getAbsolutePath())
                File resguardApkFile = new File(resDir, apkName + "_7zip_aligned_signed.apk")

                File resMappingFile = new File(resDir, resMappingFileName)

                copyFileUsingStream(resMappingFile, resMappingFileBack)

                resultFile = resguardApkFile;
            }


            //walle
            if (packExtension.isWalle) {
                execCommand("java", "-jar", packExtension.walleJarPath, "batch", "-f", packExtension.walleChannelPath, resultFile.getAbsolutePath())
            }

            if (packExtension.isApkCanary) {
                File analyzeFile = new File(backDir, "apk-checker-result");
                String apkCanaryJson = getFileString(packExtension.apkCanaryJsonPath)
                File resRFile = new File(project.buildDir, "/intermediates/symbols/" + flavorName + "/" + buildType + "/R.txt")
                //只对source apk分析 因为后续的apk dex优化了 可能分析不准
                String resultJson = String.format(apkCanaryJson, resultFile.getAbsolutePath(), mappingFileBack.getAbsolutePath(), resMappingFileBack.getAbsolutePath(), analyzeFile.getAbsolutePath(), resRFile.getAbsolutePath())

                println(resultJson)

                File resultJsonFile = new File("${project.buildDir}/apk-canary", "apk_config.json")
                saveAsFileWriter(resultJsonFile, resultJson)

                execCommand("java", "-jar", packExtension.apkCanaryJarPath, "--config", "CONFIG-FILE_PATH", resultJsonFile.getAbsolutePath())
            }
        }
    }

    def getZipAlignPath() {
        return "${android.getSdkDirectory().getAbsolutePath()}/build-tools/${android.buildToolsVersion}/zipalign"
    }

    def getSignPath() {
        return "${android.getSdkDirectory().getAbsolutePath()}/build-tools/${android.buildToolsVersion}/lib/apksigner.jar"
    }

    static Map<String, String> readMappingFile(File file) {
        Map<String, String> map = new HashMap<>();
        FileReader fr;
        BufferedReader br;
        try {
            fr = new FileReader(file);
            br = new BufferedReader(fr);
            String line = "";

            while ((line = br.readLine())!=null) {
                if (line != null && line.endsWith(":")) {
                    line = line.replace(":", "")
                    String[] values = line.split(" -> ");
                    map.put(values[0], values[1]);
                }
            }
        } finally {
            if (br != null) {
                br.close();
            }

            if (fr != null) {
                fr.close();
            }
        }

        return map;
    }

    static void execCommand(String... command) {
        Process proc;
        try {
            StringBuilder stringBuilder = new StringBuilder();
            for(String s1 :command){
                stringBuilder.append(s1).append(" ");
            }
            println(stringBuilder.toString())
            proc = new ProcessBuilder(command).start();
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
                System.err.println(s);
            }

            proc.waitFor();
            if (proc.exitValue() != 0) {
                throw new RuntimeException("jar 执行失败");
            }
        } finally {
            if (proc != null) {
                proc.destroy();
            }
        }

        println("jar 执行完成");
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