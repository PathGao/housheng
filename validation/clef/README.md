# Clef Flash 技术验证

2026-10-03。使用固定合成样本和判断规则验证 Clef Flash。模型规则、样本和评估结果均以本仓库为准。

## 手机独立调用

安装联网版“后生 AI”，打开 → 设置与诊断 → 配置 Clef API / 测试连接。打开“手机独立调用 API”，填入 Cloudflare Account ID 和 API Token，保存后点击测试连接。凭据使用 Android Keystore AES-GCM 加密保存，排除备份与设备迁移。只使用手机网络，断开 USB 后仍可请求 API。手机网络必须能访问 api.cloudflare.com。

判断规则唯一来源是 app/src/online/assets/clef-questions.json，电脑客户端也读取该文件。验证场模型开关和各真实应用开关默认关闭，进程重启后关闭。验证场只有已勾选的普通内容能触发模型判断，Cloudflare 会收到该合成文本。真实应用发送的范围见 [应用范围](../../docs/app-scope.md)。API 错误、未知或不确定结果均保留，没有关键词降级。

调试模型动作期限3秒，普通关键词路径500ms。期限在读取页面时固定，不因排队或后续事件刷新。切页、来源撤销、停止执行、过期、广告和未知页面不能触发旧动作。不联网版“后生”不包含网络能力。

## 电脑评估与 USB 桥接

凭据在项目忽略的 .tools/clef.env，内容如下，不提交真实值：

```dotenv
CLOUDFLARE_ACCOUNT_ID=
CLOUDFLARE_API_TOKEN=
CLOUDFLARE_MODEL=@cf/cloudflare/clef-flash
```

使用系统 Python 3 标准库，无模型权重或新增依赖。在仓库根目录执行，输出路径必须未存在：

```sh
python3 validation/clef/evaluate.py --split eval --output .tools/clef-results/new-eval.json
```

```sh
python3 validation/clef/bridge.py
```

桥接仅监听127.0.0.1:18765。手机通过ADB USB反向转发到电脑，电脑调用 Cloudflare。请求只接受来源 io.github.pathgao.housheng.fixture、最多4000字及合法请求ID。响应协议仍是 POST /v1/classify → request_id、decision、model、elapsed_ms，decision 仅 keep/filter/uncertain。密钥不写日志，不放入APK。

选择明确的设备，避免误操作已运行的模拟器。手机需保持解锁：

```sh
ANDROID_SERIAL=<目标设备序列号> sh scripts/test-device.sh --model direct
```

```sh
ANDROID_SERIAL=<目标设备序列号> sh scripts/test-device.sh --model usb
```

测试脚本经标准输入把电脑凭据暂存到应用私有目录，测试通过配置保存后删除暂存明文。直连测试移除18765反向转发，USB测试要求桥接健康检查通过。测试结束后开关关闭，独立验证场恢复服务。

## 不调用真实 API 的检查

```sh
python3 -m unittest discover -s validation/clef -p 'test_*.py' -v
```

本轮数据和设备结果见 [验证记录](../../docs/clef-validation.md)。准确率和耗时只代表固定合成集与本次网络，不代表真实短视频效果。
