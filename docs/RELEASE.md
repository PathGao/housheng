# 发版

每次发版同时出两个正式签名的 APK，共用一个版本号：

| | 不联网版 | 联网版 |
|---|---|---|
| Gradle flavor | `offline` | `online` |
| 包名 | `io.github.pathgao.housheng` | `io.github.pathgao.housheng.online` |
| 桌面名称 | 后生 | 后生 AI |
| 发行文件 | `housheng-<版本>.apk` | `housheng-online-<版本>.apk` |
| 权限 | 无 | 只有 `INTERNET` |

推送 `v*` 标签后，`.github/workflows/release.yml` 会跑两版单元测试与 Lint，用同一把发行密钥签名 `assembleRelease` 产出的两个包，用 `scripts/check-permissions.sh` 精确比对两版权限，再把两个 APK 和一份两行的 `SHA256SUMS` 发到同一个 GitHub Release。版本号带 `-` 的标为预发布。

## 一次性准备

生成发行密钥（放在仓库外，`*.jks` 已被忽略）：

```sh
keytool -genkeypair -v -keystore ~/housheng-release.jks -alias housheng \
  -keyalg RSA -keysize 4096 -validity 36500 -dname "CN=housheng"
```

把四个值存进仓库 secrets：

```sh
base64 -i ~/housheng-release.jks | gh secret set HOUSHENG_KEYSTORE_BASE64 -R PathGao/housheng
gh secret set HOUSHENG_KEYSTORE_PASSWORD -R PathGao/housheng
gh secret set HOUSHENG_KEY_ALIAS -R PathGao/housheng --body housheng
gh secret set HOUSHENG_KEY_PASSWORD -R PathGao/housheng
```

**密钥丢了就再也发不出能覆盖升级的版本。** 把 `housheng-release.jks` 和两个密码另存到密码管理器或离线介质，至少两份。

## 每次发版

1. 改 `app/build.gradle.kts`：`versionCode` 加一，`versionName` 改成新版本。两版共用这两个值。
2. 可选：写 `docs/releases/<versionName>.md` 作为发布说明。也认去掉 `-` 后缀的文件名，如 `0.2.4.md`。没有就由 GitHub 自动生成。
3. 提交、推送，然后打标签。标签必须是 `v` 加 `versionName`，不一致时工作流会失败：

```sh
git tag v0.2.4
git push origin v0.2.4
```

## 验证

```sh
gh release download v0.2.4 -R PathGao/housheng -D /tmp/hs
cd /tmp/hs && shasum -a 256 -c SHA256SUMS
apksigner verify --print-certs housheng-*.apk
```

两个包的证书 SHA-256 相同，且每次发版都不变。

本机签名构建：设置 `HOUSHENG_KEYSTORE_PATH`、`HOUSHENG_KEYSTORE_PASSWORD`、`HOUSHENG_KEY_ALIAS`、`HOUSHENG_KEY_PASSWORD` 后运行 `scripts/build-local.sh :app:assembleRelease`，产物是 `app/build/outputs/apk/{offline,online}/release/app-*-release.apk`。不设置时产物是 `app-*-release-unsigned.apk`，CI 就是这样跑的。

## 从调试包迁移（只需一次）

之前装的都是调试签名包，包名与不联网版相同。发行包签名不同，Android 不允许覆盖安装，**必须先卸载旧版，本机设置、关键词和报告会一起清空**。卸载前先记下需要保留的设置。原来的 Clef 调试预览包已由联网版取代，联网版包名不同，直接安装即可，Clef 凭据需要重新配置。之后的发行包之间可以正常覆盖升级。
