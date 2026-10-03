# 开发与验证

源码、界面规范、模型规则、验证结果与待办均以 [GitHub](https://github.com/PathGao/housheng) 为准。不维护仓库外的另一份产品进度。

## 本机构建

需要 JDK 17、Android SDK 35 和 Python 3 标准库，无模型权重和 Python 虚拟环境。现有本机工具位于 `.tools/jdk`、`.tools/android-sdk`，`scripts/build-local.sh` 自动使用这些路径，否则使用已配置工具链。Gradle 下载与缓存位于 `.gradle-home`。

```sh
scripts/build-local.sh --no-daemon -Pkotlin.compiler.execution.strategy=in-process :app:testOfflineDebugUnitTest :app:testOnlineDebugUnitTest :app:lintOnlineDebug :app:lintOfflineRelease :fixture:lintDebug :app:assembleDebug :app:assembleRelease
python3 -m unittest discover -s validation/clef -v
```

主程序分两个 flavor：`offline` 包名 `io.github.pathgao.housheng`，无网络权限；`online` 包名加 `.online`，多出 `app/src/online` 下的 Clef、真实应用筛选和遮挡，服务名带“后生 AI”前缀。USB 电脑桥接和 127.0.0.1 明文放行只在 `app/src/onlineDebug`，正式版固定手机直连。类名两版相同，设备命令按包名区分。

`.tools/clef.env` 只用于电脑端验证，不进 APK 或 Git。手机在后生内配置自己的凭据。`.tools/android-user/debug.keystore` 决定本机调试包的更新签名，不能作为缓存删除。

## 真机

优先使用真实应用验证，当前设备是小米 10S、Android 13。ADB 多设备时显式设置 `ANDROID_SERIAL`。安装和系统安全锁屏可能中断自动测试，锁屏必须由本人解除，不修改全局锁屏设置。

真实小红书测试必须显式选择 `RealAppValidationTest`，不会混进日常回归。它需要已保存 Clef 凭据、小红书 9.49.0、已登录且处于首页发现页。它调用真实 API，验证实际卡片的读取、遮挡与恢复，不使用合成内容替代。

```sh
adb shell am instrument -w -r -e realApps true -e class io.github.pathgao.housheng.RealAppValidationTest io.github.pathgao.housheng.online.test/androidx.test.runner.AndroidJUnitRunner
```

合成场景继续作为回归工具检查过期结果、广告页、通知保护等边界。入口为 `scripts/test-device.sh --model direct`，安装 online 调试版、独立验证场和两个测试组件。设 `HOUSHENG_FLAVOR=offline` 可在不联网调试版上跑不含模型的部分。测试进程退出会强停主程序，脚本最后通过独立进程恢复服务。这种测试重连不能当作日常运行需要反复授权的证据。

交付手机只保留后生主程序。测试组件、验证场、临时凭据文件、截图缓存和 ADB 反向转发在收尾清理。真实内容截图和界面树不上传 GitHub。

## 当前限制

- 小红书只识别发现页标题，不能理解图片和视频。广告标记无法通过无障碍读取的情况仍需实测，不宣称能可靠区分所有商业内容。
- 抖音系 40.6.0 与快手系 14.8.40 的视频文案筛选只按小米 10S 采样数据离线回放过，未在真机运行。快手只采到未登录状态，广告和直播样本缺失。尚未接入 OCR 或视频理解，没有文案的视频不处理。
- 24–72 小时后台稳定性、不同运营商网络、其他品牌和跨应用版本兼容性尚未验收。

最新结果见 [Clef 验证](clef-validation.md)，待办与不做的范围见 [路线图](roadmap.md)。
