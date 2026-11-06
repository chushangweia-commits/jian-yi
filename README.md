# 简易 · Jian Yi

> 中文｜[English](#english)

## 中文

**简易** 是一款离线优先的易学与传统文化学习工具，以原生 Java Android 应用形式提供经典阅读、结构化学习与本地记录功能。

### 主要能力

- 离线阅读《周易》与相关经典资料；
- 梅花易数、六爻、八字、大衍等学习模块与规则化练习；
- 本地笔记、学习记录及备份恢复；
- 可选的个人 DeepSeek 联网解答：密钥仅保存在设备系统密钥库中，不写入备份、笔记或日志；
- 无内置 API 密钥；发布构建会在构建前后扫描常见凭据与私钥，且不显示匹配值。

本软件仅供学习、阅读与研究使用，不提供任何保证性结论或替代专业判断的建议。

### 构建

本仓库不包含 Android 工具链、发布签名证书或上传私钥。准备好受信任的本地工具链后，设置以下环境变量并运行：

```bash
export JIANYI_ANDROID_TOOLS=/absolute/path/to/android-analysis
export JIANYI_ANDROID_SECURITY=/absolute/path/to/security-clean
python3 build.py
```

构建产生未经签名的 APK：`build/jianyi-fresh-unsigned.apk`。请仅使用既有、受保护的发布签名流程进行正式签名；不要将证书、私钥、病例、个人配置或 APK 产物提交到仓库。

### 许可与商业使用

本仓库为**源码可用、非商业学习使用**项目，并非开源许可。完整条款见 [`LICENSE-NONCOMMERCIAL.md`](LICENSE-NONCOMMERCIAL.md)。

版权所有 © 2026 浙江光米文化科技有限公司。商业部署、收费服务、再分发、培训/课程集成或衍生产品，须事先取得书面授权。

离线古籍和第三方组件适用各自随附的来源与许可说明，尤其请保留 [`assets/CLASSICS-LICENSE.txt`](assets/CLASSICS-LICENSE.txt) 中的署名与再分发条件。

---

## English

**Jian Yi** is an offline-first Android learning tool for I Ching and traditional Chinese culture. It is implemented as a native Java Android application for classical-text reading, structured study, and local records.

### Highlights

- Offline reading of the *I Ching* and related classical materials;
- Guided learning modules and rule-based practice for Meihua Yishu, Liuyao, Bazi, and Dayan;
- Local notes, learning records, backup, and restore;
- Optional personal DeepSeek access: a user-provided key stays in the device system keystore and is excluded from backups, notes, and logs;
- No embedded API key. Release builds scan common credential and private-key patterns before and after building without printing matched values.

This software is for learning, reading, and research only. It does not offer guaranteed conclusions or replace professional judgment.

### Building

This repository intentionally excludes Android toolchains, release-signing certificates, and upload private keys. With trusted local tools available, set:

```bash
export JIANYI_ANDROID_TOOLS=/absolute/path/to/android-analysis
export JIANYI_ANDROID_SECURITY=/absolute/path/to/security-clean
python3 build.py
```

The build produces an unsigned APK at `build/jianyi-fresh-unsigned.apk`. Use only an existing, protected release-signing process for an official build. Do not commit certificates, private keys, personal configuration, or APK outputs.

### License and commercial use

This is a **source-available project for non-commercial learning use**, not an open-source license. See [`LICENSE-NONCOMMERCIAL.md`](LICENSE-NONCOMMERCIAL.md) for the complete terms.

Copyright © 2026 Zhejiang Guangmi Culture Technology Co., Ltd. Written permission is required before commercial deployment, paid services, redistribution, training/course integration, or derivative commercial products.

Bundled classical texts and third-party components retain their own source and license terms. In particular, retain the attribution and redistribution conditions in [`assets/CLASSICS-LICENSE.txt`](assets/CLASSICS-LICENSE.txt).
