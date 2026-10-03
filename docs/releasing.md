# 发版

推送 `v*` 标签后，`.github/workflows/release.yml` 会跑测试与 Lint，用发行密钥签名 `assembleRelease`，确认没有网络权限，再把 `housheng-<版本>.apk` 和 `SHA256SUMS` 发到 GitHub Release。版本号带 `-` 的标为预发布。

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

1. 改 `app/build.gradle.kts`：`versionCode` 加一，`versionName` 改成新版本。
2. 可选：写 `docs/releases/<versionName>.md` 作为发布说明。也认去掉 `-` 后缀的文件名，如 `0.2.1.md`。没有就由 GitHub 自动生成。
3. 提交、推送，然后打标签。标签必须是 `v` 加 `versionName`，不一致时工作流会失败：

```sh
git tag v0.2.2-family-preview
git push origin v0.2.2-family-preview
```

## 验证

```sh
gh release download v0.2.2-family-preview -R PathGao/housheng -D /tmp/hs
cd /tmp/hs && shasum -a 256 -c SHA256SUMS
apksigner verify --print-certs housheng-*.apk
```

证书 SHA-256 每次都应相同。

本机签名构建：设置 `HOUSHENG_KEYSTORE_PATH`、`HOUSHENG_KEYSTORE_PASSWORD`、`HOUSHENG_KEY_ALIAS`、`HOUSHENG_KEY_PASSWORD` 后运行 `scripts/build-local.sh :app:assembleRelease`，产物是 `app-release.apk`。不设置时产物是 `app-release-unsigned.apk`，CI 就是这样跑的。

## 从调试包迁移（只需一次）

之前装的都是调试签名包。发行包签名不同，Android 不允许覆盖安装，**必须先卸载旧版，本机设置、关键词和报告会一起清空**。卸载前先记下需要保留的设置。

发行包也没有调试包里的 USB 本机模型入口。之后的发行包之间可以正常覆盖升级。
