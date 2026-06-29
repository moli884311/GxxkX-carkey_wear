五菱无感蓝牙控车 - Android 原生应用

基于真实五菱 Sgmw API (openapi.baojun.net) 和开源项目 hasscc/wuling 与 GxxkX/carkey_wear 实现。

## 功能

- **无感控车**: 靠近自动开锁，离开自动落锁 (BLE RSSI)
- **远程控制**: 通过 Sgmw API 远程控车 (空调、寻车、状态查询)
- **双模式配置**: Sgmw API 凭证 / 纯 BLE 手动输入
- **后台保活**: 前台 Service 持续扫描蓝牙信号

## 技术栈

- Kotlin + Jetpack Compose + Material 3
- Nordic BLE Library (no.nordicsemi.android:ble)
- OkHttp + Coroutines
- DataStore Preferences
- Navigation Compose

## 构建

```bash
./gradlew assembleDebug
```

## 配置方式

### Sgmw API 模式
从五菱官方 App 抓包获取 accessToken、clientId、clientSecret，支持远程控车和车辆状态查询。

### BLE 手动模式
直接输入车辆蓝牙 MAC 地址和 BLE 通信密钥，全程蓝牙无需网络，适用于安卓手表等离线设备。
