# ChatRoomDemo

> **Note for non-Chinese readers:** The rest of this document is written in Chinese. For easier understanding, please use an AI tool to translate the content below into your native language before following the instructions.

基于 **Netty** + **Protobuf** 的房间群聊 Demo：统一 Envelope 帧编解码，Chat / Room 领域拆分，支持登录占名、房间创建/加入、群聊广播、心跳踢人、离线通知与断线后手动重连 / 回房。

## 环境要求

| 依赖 | 建议版本 |
|------|----------|
| JDK | 8+（开发环境可为 JDK 21） |
| Maven | 3.8+ |
| 网络 | 首次构建需能访问 Maven 中央仓库 |

无需单独安装 `protoc`：`protobuf-maven-plugin` + `os-maven-plugin` 会在构建时自动下载。

## 快速开始

### 1. 编译

```bash
mvn compile
```

### 2. 启动服务端

```powershell
mvn -q org.codehaus.mojo:exec-maven-plugin:3.5.1:java "-Dexec.mainClass=org.example.ChatServer" "-Dexec.classpathScope=compile"
```

看到：

```text
ChatServer started on 6666, reader idle=60s
```

### 3. 启动客户端（多开几个终端）

```powershell
mvn -q org.codehaus.mojo:exec-maven-plugin:3.5.1:java "-Dexec.mainClass=org.example.ChatClient" "-Dexec.classpathScope=compile"
```

连接成功后自动发送 `CLIENT_HELLO`；服务端签发 `clientId` + `reconnectToken`，客户端进程内缓存。**不会自动重连**，断线后需手动输入 `reconnect`。

也可在 IDE 中分别运行 `ChatServer` / `ChatClient`（多开几个 Client 配置即可）。

## 客户端命令

```text
login <username>
create <room> <password>
list
join <room> <password>
msg <text>
leave
reconnect
rejoin
noping / pingon
help
quit
```

| 命令 | 说明 |
|------|------|
| `login` | 连接后先登录；用户名全局唯一（断线宽限内仍占坑） |
| `create` / `join` | 须已登录且当前不在房；建房即进房 |
| `msg` | 群聊；发送端乐观本地 echo，服务端向其他人广播 |
| `reconnect` | 仅断线后可用；HELLO 带回缓存凭证 |
| `rejoin` | 心跳踢人且服务端下发可回房提示后可用 |
| `noping` / `pingon` | 关闭/开启客户端自动 PING（测 60s 踢人） |
| `quit` | 先发 logout 再关连接，并清空本地凭证 |

未知命令会提示；裸文本不会当作聊天发送。房间广播使用 **username**，不对 peer 暴露 `clientId`。

## 行为说明

| 项目 | 说明 |
|------|------|
| 端口 | `6666` |
| 帧格式 | `4 字节大端长度 + Envelope Protobuf` |
| 协议 | `common.proto` / `chat.proto` / `room.proto` |
| 分发 | `(Domain, msgType) → Resolver` |
| 服务端空闲 | 读空闲 **60s** 无完整入站帧 → 心跳踢人 |
| 客户端心跳 | 写空闲 **20s** 发 PING（滑动续期在线票据） |
| 身份 | HELLO 签发 `clientId` + `reconnectToken`；在线票据 TTL 5min（PING 续期） |
| 断线宽限 | 踢人/断线后票据与 username 占坑约 **60s** |
| 待回房 | **仅心跳踢人**且当时在房、房间仍在时记录，TTL **60s**；须手动 `rejoin` |
| 密码 | SHA-256 + 随机盐，Base64 存于内存；不落明文、不写日志 |
| 审计 | 控制台 `[CHAT]` / `[CREATE]` / `[JOIN]` / `[LEAVE]` / `[OFFLINE]` 等 |

## 工程结构

```text
src/main/java/org/example/
├── ChatServer.java / ChatClient.java
├── ChatServerHandler.java / ChatClientHandler.java
├── codec/          EnvelopeEncoder / Decoder / Factory
├── config/         ServerConfig
├── session/        Session / SessionManager / ClientCredential
├── room/           Room / RoomManager / PasswordHasher / RoomAuditLogger
├── reconnect/      PendingRejoin / ReconnectStateStore
└── resolver/
    ├── Resolver / MessageResolverFactory
    ├── chat/       Hello / Login / Logout / Ping
    └── room/       Create / List / Join / Leave / Chat / RejoinConfirm

src/main/proto/
├── common.proto    Envelope / Domain / ErrorCode
├── chat.proto      HELLO / LOGIN / LOGOUT / PING
└── room.proto      房间业务消息
```

## 建议自测路径

1. **A** `login alice` → `create room1 123`；**B** `login bob` → `join room1 123`；**C** `login carol` → `create room2 456`
2. A `msg hello` → B 收到 `[room1] alice: hello`，C 收不到
3. `list` → 看到 room1/room2 与人数，无密码
4. `join room1 wrong` 失败；再次 `create room1 123` 失败（已存在）
5. A `noping`，静止约 60s 被踢 → B 收到 `alice is offline`；服务端有 `[KICK]` / `[OFFLINE]`
6. A 不退出进程，输入 `reconnect` → HELLO 恢复身份；若有提示则 `rejoin` 回房 → B 收到 `alice reconnected`
7. A `quit` 后同名可立即被他人 `login`（无宽限）；踢人后 60s 内同名仍可能 `Username already taken`
8. 未入房 `msg xx` → `Send failed: Not in any room`（或本地已不在房）

## License

MIT © 2026 wdytoya
