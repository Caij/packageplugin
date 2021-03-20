# 打包插件 结合andresguard、walle两者。

## 接入步骤
### 1 根目录gradle插件中添加
```
classpath 'com.github.Caij:packageplugin:50f72dfc73'
```

### 2 在app工程中添加
```
apply plugin: 'pack-plugin'
pack {
    outFileDir = "${project.projectDir}/other" //输出目录

    isWalle = true //是否多渠道打包

    //多渠道打包配置 和walle配置相同
    channelConfigFile = "${project.rootDir}/mupackage/config.json"
    //or
    channelFile = ""

    //是否开启 andresguard
    isResGuard = true
}
```

### 3 接入andresguard
按照官方配置接入andresguard即可。 https://github.com/shwenzhang/AndResGuard

### 使用
详细可查看app->tasks->pack内的所有任务
执行 pack${variantName}${BulidType}即可，比如packNeiceRelease


