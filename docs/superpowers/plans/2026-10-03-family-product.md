# 后生家庭试用版实施计划

**目标：** 将已验证原型交付为父母可用、孩子可协助的本地家庭版，完成用户批准的P1–P4。信息流自动筛选保持验证场试用，模型后续接入。

**架构：** 原生Android Activity和共享适老控件；SQLite只保存30天来源汇总及有限连接日志；通知意愿持久化，权限/连接/执行分别呈现。没有账号或服务器，分享前预览。

**技术栈：** 现有Kotlin、平台View、SQLiteOpenHelper、JUnit，无新增运行时依赖。

**依据：** 用户已确认上一轮P1–P4产品范围和多subagent执行，UI参考用户给定UI UX Pro Max技能。用户已授权自主完成，不增加重复审批或提交推送。

## 当前进度（2026-10-03，家庭版功能验收完成）

家庭版五个页面及导航已实现。首页使用真实服务状态，通知页接通来源、始终放行、关键词和系统权限入口，报告页接通7/30天统计、主动包含安装清单、预览复制/系统分享和二次确认清空。原型工具移到独立诊断页。应用清单复用后台查询，保留系统详情和独立分享入口。

服务状态已区分授权、连接及执行意愿，通知执行选择和主动暂停持久化。通知和连接记录异步写入，历史清空与服务记录使用同一队列；默认家庭报告隔离验证场通知。报告的始终放行来源已独立输出，避免长关键词截断它；包含应用清单时输出完整可见列表并标记近期安装。

26项JVM、27项小米10S功能测试及1项恢复测试通过。复制使用实际剪贴板验证，分享截获系统请求验证，未向联系人发送。模型和真实应用信息流均未开放。完整TalkBack、最大系统字号和长期存活保留为发布前验收项。

## 边界

真实应用页面仍禁用，浏览器/私信不采集，广告和未知页不操作。不增加保活绕过、无障碍自动授权或远程控制。统计不存通知正文、标题或原始通知标识。验证场与真实数据分开，清理请求不冒充已拦截数量。

## 写入归属

- product_ui：MainActivity、InventoryActivity、DiagnosticsActivity、ProductUi、res、product-design文档。
- report_data：ReportStore、ReportData、FamilyReport及其新测试。
- service_state：Session、FeedService、NotificationService、ServiceHealth及其新测试。
- root：NotificationsActivity、ReportActivity、InstalledApps、manifest/build版本、既有测试迁移、集成测试、脚本和交付文档。构建和设备只有root操作。

## 接口

ProductUi.page/activity,title,subtitle返回内容LinearLayout。提供text/button/card/dp。
ServiceHealth.notificationStatus/pageStatus返回UNAUTHORIZED/DISCONNECTED/OBSERVING/EXECUTING及文案属性。Session.initialize接通持久化意愿，stop持久化暂停。Preferences提供selected、rules、alwaysAllow。
ReportStore.recordNotification按source,eventId,outcome异步保存，内部摘要去重；snapshot(days,testData)返回窗口/开始记录时间/来源汇总/服务事件；clear清空。FamilyReport纯函数拼接预览文本。

## 任务与预期

- [x] T1 首页/应用清单：主要操作≥56dp、主要正文18sp、隐私范围明确，诊断离开首页；清单主动读取、无自动恶意判定。当前手机字号截图检查完成，辅助说明部分16sp。
- [x] T2 统计/报告：来源隔离、重放去重、30天清理、清空、报告无正文、空数据覆盖说明，纯测试和数据库真机测试通过。
- [x] T3 生命周期：未授权与断连分开；通知选择持久化、主动暂停优先；放行来源不读正文；写库不阻塞热路径。实际重启及自然回收仍待长期验证。
- [x] T4 通知/报告页面：选择来源、始终放行、关键词、系统权限入口；7/30天报告，应用清单显式选择，预览后复制/分享；清空二次确认。
- [x] T5 集成：本地测试/Lint/构建、旧边界设备测试、新导航/存储/状态；截图检查实际UI；最终安装及服务恢复。
- [ ] 发布前补充：最大系统字号、完整TalkBack、横屏、其他品牌和长期后台验证。

## 未证明的条件

24–72小时后台稳定性、重启/自然回收/省电仍需真实时间验证。本轮不以短测代替这些结果，不设置没有用户要求的定时监控。模型和真实页面适配未完成，不对外发布。
