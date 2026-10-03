<p align="center">
  <img src="design/icon/housheng-rounded.png" width="128" alt="后生图标：青色背景上的黑猫与素面折扇">
</p>

<h1 align="center">后生</h1>

<p align="center">
  <b>帮爸妈看一眼手机里的通知。</b><br>
  中文 Android 信息流助手。本机处理，不联网，规则由本人决定。
</p>

<p align="center">
  <a href="https://github.com/PathGao/housheng/releases"><img src="https://img.shields.io/badge/下载-APK-1f6f5c?logo=android&logoColor=white" alt="下载 APK"></a>
  <a href="https://github.com/PathGao/housheng/actions/workflows/ci.yml"><img src="https://github.com/PathGao/housheng/actions/workflows/ci.yml/badge.svg" alt="CI"></a>
  <img src="https://img.shields.io/badge/Android-11%2B-3ddc84?logo=android&logoColor=white" alt="Android 11+">
  <img src="https://img.shields.io/badge/网络权限-无-555" alt="发行版无网络权限">
</p>

<p align="center">
  <a href="#安装">安装</a> ·
  <a href="#能做什么">功能</a> ·
  <a href="#隐私">隐私</a> ·
  <a href="docs/roadmap.md">路线图</a> ·
  <a href="#参与开发">参与开发</a>
</p>

<p align="center">
  <img src="docs/images/home.png" width="300" alt="后生首页：通知清理状态、最近 7 天次数和功能入口">
</p>

爸妈的手机每天响几十次，哪些是推广、哪些是正事，他们分不清，你又不在身边。后生先帮你们**看清**通知从哪来、有多频繁，再由爸妈自己决定清理什么。孩子只看报告、给建议，不远程控制。

## 能做什么

| 问题 | 后生的做法 |
|---|---|
| 不知道哪个应用总在发通知 | 勾选要管理的应用，查看最近 7 天或 30 天的次数与处理汇总 |
| 想少看推广，又怕漏掉正事 | 自设清理关键词，可让某个来源始终放行。电话、消息、闹钟和常驻通知始终保留 |
| 孩子想帮忙，但不在身边 | 爸妈预览后复制或分享家庭报告，孩子据此建议怎么调 |
| 不知道手机最近装了什么 | 查看有桌面入口的应用，标出近 30 天新装的，可跳到系统详情由本人决定是否卸载 |
| 想马上停下 | 首页点“暂停自动清理”，记录保留 |

> [!NOTE]
> 抖音、快手、小红书的信息流筛选**尚未开放**。AI 判断与自动下滑只在独立验证场跑通，见 [模型管线验证](docs/laya-validation.md)。

## 安装

需要 Android 11 或以上。

1. 打开 [发行版页面](https://github.com/PathGao/housheng/releases)，下载 `housheng-<版本>.apk`，可用同页的 `SHA256SUMS` 校验。
2. 打开后生，在首页点“去系统设置允许”，授予通知使用权。
3. 在“通知清理”里选要管理的应用。先保持“按规则清理通知”关闭，观察几天次数。
4. 和家人一起看“家庭报告”，定好关键词和放行来源，再开启清理。

日常使用**不需要无障碍权限**。清理发生在通知到达之后，已经响过的声音或弹过的横幅仍会出现。想彻底关掉某个应用的通知，用后生里的“系统通知设置”入口。

> [!WARNING]
> 装过 CI 或本机调试包的手机，签名与发行版不同，无法直接覆盖安装。需要先卸载旧版，本机记录会清空。详见 [发版流程](docs/releasing.md)。

## 隐私

- **不联网。** 发行版不申请网络权限，CI 每次构建都会检查。
- **只看你勾选的应用。** 浏览器、聊天和名单外应用一概不采集。
- **不存正文。** 通知标题和正文不落盘，本机只留最近 30 天的次数汇总。
- **报告不自动发送。** 报告不含正文、验证码或私聊，由爸妈预览后自己分享。
- **没有账号。** 无远程控制，无同步，无云模型。
- **不越权。** 不屏蔽广告，不点“跳过”，不绕过应用限制，不自动卸载。

完整的数据范围、权限和统计口径见 [应用范围与隐私](docs/app-scope.md)。

## 接下来

先让真实信息流筛选又快又准，并能一键纠正误筛。稳定后再支持年轻人自己用：

- **质量筛选**：少看标题党和没依据的绝对承诺。
- **拓展视野**：给不常接触的话题和来源留点时间。
- **临时回避**：今天不想看某类内容，到期自动恢复。
- **内容解说**：保留观看，同时说明哪里值得核实。

以上均未实现。规则由本人设定和解除，付费不能买放行。进度与验收条件见 [路线图](docs/roadmap.md)。

## 参与开发

需要 JDK 17 和 Android SDK 35，Gradle 用仓库自带的 Wrapper。

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

调试包在 `app/build/outputs/apk/debug/app-debug.apk`。CI 会跑单元测试、Lint、调试与发行构建，并检查发行版没有网络权限。

| 目录 / 文档 | 内容 |
|---|---|
| `app/` | 主程序：通知管理、家庭报告、应用清单 |
| `fixture/` | 合成验证场，测试翻页与保护边界 |
| `validation/laya/` | 本机模型桥接与固定决策协议，见 [说明](validation/laya/README.md) |
| [设备验证](docs/device-validation.md) | 真机测试步骤与结果，入口 `scripts/test-device.sh` |
| [界面设计](docs/product-design.md) | 家庭版页面与适老取舍 |
| [发版流程](docs/releasing.md) | 签名、打标签、发布 APK |
| [图标](design/icon/README.md) | 黑猫折扇图标来源与启动器配置 |

反馈问题请在 [Issues](https://github.com/PathGao/housheng/issues) 附上手机型号、Android 版本、操作步骤和首页显示的服务状态。截图前请遮挡个人信息，不需要提供真实通知正文。
