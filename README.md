> [!IMPORTANT]
> **本仓库是 [Kaia-Alenia/Axon](https://github.com/Kaia-Alenia/Axon) 的 Fork，并非官方仓库。**
>
> - 上游项目：[Kaia-Alenia/Axon](https://github.com/Kaia-Alenia/Axon)
> - 本 Fork 所有者：[yk2068](https://github.com/yk2068)
> - 许可：沿用上游的 **GPL-3.0**（见 [LICENSE](LICENSE)）
>
> **本 Fork 为 AI 辅助定制版本**：改动由 AI 辅助生成与维护，非上游作者官方发布。
> 定制内容不代表上游立场，问题与 bug 请优先在本仓库反馈；如需跟随上游进展，可关注上游仓库。

Axon 把手机变成电脑的无线触控板。本 Fork 只做一件事：让它像一块真正的触控板那样安静、顺手——界面只剩触控区，设置靠手势唤出，输入链路上的延迟与丢包逐项清掉。

## 定制改动点

### 客户端（Android）

- **界面精简**：启动直接进扫码页，删除引导页、连接页、键盘页与底部 Mouse/Keyboard 切换；触控区全屏铺满，并避开状态栏、导航条与挖孔。
- **设置入口改为手势**：不再显示设置按钮，触控区**双指双击**开/关设置面板；双指手势不再误触发左键点击。
- **灵敏度可调**：0.8×–5.0×，同时作用于指针移动与右侧滚动条——每格所需手指位移 = 40dp ÷ 灵敏度，快划一次最多连发 4 格。
- **滚动条宽度可调**：36–110dp。
- **滚动反向开关**：只反转滚动方向，不影响指针；滚动条内的视觉方向同步反转。
- **记住上次连接**：连接串（IP + token）落盘，冷启动与从后台回到前台各自动重连一次，无需重新扫码。
- **熄屏保活**：会话期间持有 `WifiLock(HIGH_PERF)` 与 `PARTIAL_WAKE_LOCK`，尽量减少熄屏后连接被系统回收。
- **输入流畅度**：UDP 发送由 conflated 通道改为缓冲队列（原先待发包被覆盖，位移直接丢失）、移动发送改为 8ms 时间预算、滚动节流由 20ms 收到 12ms。

### 服务端

- **修复 Windows 鼠标注入完全失效**：`InputUnion` 结构体多了一个 `Pad [8]byte`，在 x64 上大小变成 48 字节，而 `SendInput` 要求严格 40 字节，导致移动、点击、滚轮全部被拒绝。删掉该字段后恢复正常。
- **亚像素累积**：`MoveMouse` 原先直接 `int32(dx)` 截断，不足 1px 的位移整包丢弃，慢速滑动会「粘住再跳」；改为累积小数、余数留到下一包。
- **新增 `-host` 参数**：强制广播指定的局域网 IP。上游会挑到 Wi-Fi Direct 虚拟网卡的 `169.254.x` 地址，导致二维码扫了连不上。
- **新增 `-token` 参数**：固定鉴权 token。上游每次启动重新生成，重启后就得重新复制。
- **修正 `getLocalIP()`**：网卡名前缀改为大小写不敏感（Windows 报 `WLAN`），并跳过链路本地地址。
- **终端静音**：去掉每 500ms 一条的 RTT 延迟、连接/断开、键盘三类日志，启动横幅与错误信息保留。

## 安装

### 服务端（电脑）

取得 `axon.exe` 后放到固定目录，以固定参数启动：

```powershell
axon -host <本机局域网IP> -token <自定义token>
```

可选 `-no-logo` 去掉启动大图，`-port` / `-udp-port` 指定端口。

### 客户端（手机）

安装 `app-release.apk`，首次扫码连接，之后启动即自动重连。

## 构建

**Android**

```bash
cd android
./gradlew :app:assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

**服务端**

```bash
cd server
GOPROXY=https://goproxy.cn,direct go build -o axon.exe .
```
