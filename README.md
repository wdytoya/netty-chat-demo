# NettyProtobufDemo

基于 **Netty** + **Protobuf** 的简易聊天 Demo：客户端连接服务端后，可在控制台输入文本；服务端按消息类型路由处理并回包。

适合用来理解：长度字段拆包、Protobuf 编解码、Idle 心跳/超时、以及按 `MsgType` 分发的 Resolver 模式。

## 环境要求

| 依赖 | 建议版本 |
|------|----------|
| JDK | 17+（开发环境为 JDK 21） |
| Maven | 3.8+（开发环境为 3.9） |
| 网络 | 首次构建需能访问 Maven 中央仓库（下载 Netty / Protobuf / protoc） |

无需单独安装 `protoc`：`protobuf-maven-plugin` + `os-maven-plugin` 会在构建时自动下载对应平台的 protoc，并生成 Java 代码到 `target/generated-sources/protobuf/java/`。

## 快速开始

### 1. 克隆并编译

```bash
git clone <你的仓库地址>.git
cd NettyProtobufDemo
mvn compile
```

编译成功即表示 Protobuf 已生成且源码已编译。

### 2. 启动服务端

**先开一个终端**，在项目根目录执行：

```bash
mvn -q org.codehaus.mojo:exec-maven-plugin:3.5.1:java -Dexec.mainClass=org.example.ChatServer -Dexec.classpathScope=compile
```

看到如下日志表示监听成功：

```text
ChatServer started on 6666, waiting for clients...
```

默认绑定：`0.0.0.0:6666`。

### 3. 启动客户端

**再开一个终端**（服务端保持运行）：

```bash
mvn -q org.codehaus.mojo:exec-maven-plugin:3.5.1:java -Dexec.mainClass=org.example.ChatClient -Dexec.classpathScope=compile
```

客户端会连接 `localhost:6666`，并在连接成功后自动发送一条：

```text
this is connect message...
```

之后可在客户端控制台直接输入文本并回车，消息会以 `MSG_TYPE_REQUEST` 发给服务端。

### 4. 预期现象

- **服务端**：打印收到的 `sessionId`、消息类型、正文，并回 `MSG_TYPE_RESPONSE`（内容形如 `Confirmed your message <sessionId> ...`）。
- **客户端**：打印服务端回包的 Protobuf 对象字符串。

结束方式：在对应终端按 `Ctrl+C`。建议先停客户端，再停服务端。

> **IDE 运行**：在 IntelliJ IDEA / Eclipse 中分别运行 `org.example.ChatServer` 与 `org.example.ChatClient` 的 `main` 方法即可，效果相同。记得先执行一次 Maven `compile` / `generate-sources`，确保 Protobuf 生成类存在。

## 行为说明

| 项目 | 说明 |
|------|------|
| 端口 | `6666`（`ChatServer` 绑定；`ChatClient` 连接 `localhost`） |
| 粘包/拆包 | 帧格式为 `4 字节大端长度 + Protobuf 二进制`（`LengthFieldBasedFrameDecoder` + 自定义 Encoder/Decoder） |
| 消息定义 | `src/main/proto/ChatMsg.proto`（proto2） |
| 请求处理 | `MSG_TYPE_REQUEST` → `RequestMessageResolver` → 回 `MSG_TYPE_RESPONSE` |
| Ping 处理 | `MSG_TYPE_PING` → `PingMessageResolver` → 回 `MSG_TYPE_PONG` |
| 服务端空闲 | `IdleStateHandler(10, 0, 0)`：约 **10 秒** 无读事件会断开该客户端 |

> 当前客户端 Pipeline 中的 `IdleStateHandler(0, 0, 0)` 未开启写空闲，因此客户端侧自动 Ping 逻辑默认不会触发；服务端的 Ping/Pong 解析器仍保留，便于后续扩展心跳。

## 工程结构

```text
NettyProtobufDemo/
├── pom.xml
├── README.md
├── .gitignore
└── src/
    ├── main/
    │   ├── java/org/example/
    │   │   ├── ChatServer.java              # 服务端入口
    │   │   ├── ChatClient.java              # 客户端入口（控制台发消息）
    │   │   ├── ChatServerHandler.java       # 服务端业务 + 读空闲踢人
    │   │   ├── ChatClientHandler.java       # 客户端收包 / 连接首包
    │   │   ├── ChatMsgEncoder.java          # 长度前缀 + Protobuf 编码
    │   │   ├── ChatMsgDecoder.java          # Protobuf 解码
    │   │   ├── ChatMsgFactory.java          # 构造 ChatMsg（含 UUID sessionId）
    │   │   ├── MessageResolverFactory.java  # 按类型查找 Resolver
    │   │   ├── Resolver.java
    │   │   ├── RequestMessageResolver.java
    │   │   └── PingMessageResolver.java
    │   └── proto/
    │       └── ChatMsg.proto                # 协议定义
    └── test/java/org/example/
        └── AppTest.java
```

## 协议字段（ChatMsg）

| 字段 | 含义 |
|------|------|
| `server_*_ver` | 服务端版本号（Demo 中固定写入） |
| `session_id` | 会话 ID（工厂内 `UUID` 生成） |
| `msg_type` | `REQUEST` / `RESPONSE` / `PING` / `PONG` / `EMPTY` |
| `msg_len` | 正文 UTF-8 字节长度 |
| `msg_body` | 文本正文 |

## 常见问题

**1. `mvn compile` 失败 / 下载依赖超时**  
检查网络与 Maven 镜像。国内可配置阿里云等中央仓库镜像后重试。

**2. 找不到 `org.example.demo.protos.ChatMsg`**  
说明 Protobuf 尚未生成。在项目根目录执行 `mvn compile`，确认存在  
`target/generated-sources/protobuf/java/org/example/demo/protos/`。

**3. 客户端连不上**  
确认服务端已启动且控制台出现 `ChatServer started on 6666`；本机防火墙未拦截；端口 `6666` 未被占用。

**4. 连接一会后被断开**  
服务端读空闲约 10 秒会主动关闭连接。保持在客户端持续发消息，或按需调大 `ChatServer` 中 `IdleStateHandler` 的读空闲时间。

**5. Windows PowerShell 传参注意**  
若 `-Dexec.mainClass=...` 被拆开，可改为：

```powershell
mvn -q org.codehaus.mojo:exec-maven-plugin:3.5.1:java "-Dexec.mainClass=org.example.ChatServer" "-Dexec.classpathScope=compile"
```

## 技术栈

- Netty `4.2.17.Final`
- Protobuf Java `4.36.1`（`protobuf-maven-plugin` 0.6.1）
- JUnit 3（仅测试脚手架，非 Demo 主流程）

## License

未单独指定许可证时，默认按仓库所有者声明为准。公开分享前请自行补充 `LICENSE`。
