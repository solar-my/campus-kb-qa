# IntelliJ IDEA 构建失败修复（Lombok / jakarta.persistence 找不到）

## 现象

IntelliJ 点击 Build 后出现大量错误：

```text
java: 程序包 lombok 不存在
java: 程序包 jakarta.persistence 不存在
java: 找不到符号
...
102 个错误
```

## 原因

`pom.xml` 在项目根目录，但 IntelliJ 当前是以**普通目录**打开的，没有把它识别为 Maven 工程，模块的依赖 classpath 为空。  
`lombok`、`jakarta.persistence`、Spring 等依赖都在 `pom.xml` 里，命令行 Maven 能正常编译，所以不是代码错误。

另外项目使用了 Lombok，IntelliJ 还需要开启注解处理。

## 修复步骤（按顺序）

### 1. 把 pom.xml 加入 Maven 工程

- 在项目树中右键 `pom.xml`；
- 如果菜单里有 **Add as Maven Project**，点击它；
- 或者打开右侧 **Maven** 工具窗口 → 点击 `+` → 选择项目根目录的 `pom.xml`。

### 2. Reload Maven

- 打开右侧 **Maven** 工具窗口；
- 点击左上角刷新按钮 **Reload All Maven Projects**（两个循环箭头）；
- 等待底部进度条完成下载。

完成后展开 **External Libraries**，确认能看到：

- `Maven: org.springframework.boot:spring-boot-starter-web`
- `Maven: org.springframework.boot:spring-boot-starter-data-jpa`
- `Maven: org.projectlombok:lombok`
- `Maven: jakarta.persistence:jakarta.persistence-api`

### 3. 启用注解处理

- `File` → `Settings` → `Build, Execution, Deployment` → `Compiler` → `Annotation Processors`；
- 勾选 **Enable annotation processing**；
- `File` → `Settings` → `Plugins`，确认 **Lombok** 插件已启用（IntelliJ 新版通常自带）。

### 4. 确认 JDK 是 17

- `File` → `Project Structure` → `Project`：
  - SDK 选择 **17**；
  - Language level 选择 **17**。
- `Settings` → `Build, Execution, Deployment` → `Build Tools` → `Maven` → `Runner`：
  - JRE 选择 **17**（或 Project SDK）。

### 5. 重新构建

- `Build` → `Rebuild Project`；
- 如果仍然报旧错误：`File` → `Invalidate Caches...` → 勾选 `Clear file system cache and Local History` → `Invalidate and Restart`。

## 只剩 2 个错误：ExceptionInInitializerError / TypeTag :: UNKNOWN

如果构建输出变成：

```text
java: java.lang.ExceptionInInitializerError
com.sun.tools.javac.code.TypeTag :: UNKNOWN
```

说明依赖已经导入成功，但 **IntelliJ 的项目 SDK 选了 JDK 27**，而 Lombok 1.18.36 不支持 JDK 27。  
本项目要求 JDK 17，机器上已安装：

```text
C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot
```

`.idea/misc.xml` 当前是：

```xml
<component name="ProjectRootManager" version="2"
           project-jdk-name="openjdk-27 (2)" project-jdk-type="JavaSDK" />
```

### 正确设置 JDK 17

1. `File` → `Project Structure`（快捷键 `Ctrl + Alt + Shift + S`）。
2. 左侧 `Platform Settings` → `SDKs` → 点击 `+` → `Add JDK...`。
3. 选择目录：

   ```text
   C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot
   ```

4. 把 SDK 命名为 `temurin-17`（名称随意，建议清晰）。
5. 左侧 `Project Settings` → `Project`：
   - SDK 选择 `temurin-17`
   - Language level 选择 `17`
6. 左侧 `Modules` → 选择 `campus-kb-qa` → `Dependencies`：
   - Module SDK 选择 `Project SDK (temurin-17)`
7. `Settings` → `Build, Execution, Deployment` → `Build Tools` → `Maven` → `Runner`：
   - JRE 选择 `temurin-17`
8. 回到 Maven 工具窗口，点击 **Reload All Maven Projects**。
9. `Build` → `Rebuild Project`。

### 不要用 JDK 27

即使把 Lombok 升到最新版，Spring Boot 3.4.1 / Hibernate 6.6 对 JDK 27 也没有完整支持，仍可能出现运行期问题。请统一使用 JDK 17。

### 如果 Project Structure 里看不到 JDK 17

点击 `SDKs` 页签的 `+` → `Add JDK...`，手动选择上面的安装目录；不要只选 `Project SDK` 下拉框，因为下拉框里目前只有 `openjdk-27`。
## 启动报 Communications link failure / Connection refused

日志末尾出现：

```text
Communications link failure
java.net.ConnectException: Connection refused: getsockopt
Unable to determine Dialect without JDBC metadata
```

说明 Spring Boot 已经启动到 JPA 阶段，但 **默认配置要求连接 `localhost:3306` 的 MySQL**，而本机没有运行 MySQL。并不是代码错误。

### 方案 A：先用 local profile（无需 MySQL / Chroma / Docker）

1. `Run` → `Edit Configurations...`；
2. 选中 `CampusKbApplication`；
3. 找到 **Active profiles**，填入：

   ```text
   local
   ```

4. 点击 `OK` → `Run`。

`local` profile 使用：

- H2 内存数据库
- 内存向量库
- 管理员 `admin / admin123456`

页面、登录、权限、拒答话术可以立即验证；需要真实问答时，在项目根目录 `.env` 填入 `LLM_API_KEY` 与 `EMBEDDING_API_KEY`，重启即可（`application.yml` 会自动导入 `.env`）。

### 方案 B：使用完整 MySQL + Chroma

安装 Docker Desktop 后执行：

```powershell
cd "F:\cumt\campus-kb-qa"
Copy-Item .env.example .env
docker compose up -d --build
```

或者只启动依赖，再用 IntelliJ 运行默认 profile：

```powershell
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d mysql chroma
```

`docker-compose.dev.yml` 会把 MySQL 额外映射到 `127.0.0.1:3306`，否则 IntelliJ 里的后端无法通过 `localhost:3306` 连接。

需要保证：

- MySQL 监听 `localhost:3306`
- Chroma 监听 `localhost:8000`
- `.env` 或环境变量里配置了数据库密码、模型 Key

完整说明见 [DEPLOY.md](DEPLOY.md)。
## 命令行验证

如果本机安装了 Maven：

```powershell
cd "F:\cumt\campus-kb-qa"
mvn -U clean package -DskipTests
```

没有把 Maven 加到 PATH 时，可以使用 IntelliJ 自带 Maven：

```powershell
& "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3\plugins\maven\lib\maven3\bin\mvn.cmd" -U clean package -DskipTests
```

看到：

```text
BUILD SUCCESS
```

说明项目本身没有问题。

## Maven 下载很慢 / 失败

如果 Reload 一直卡在下载依赖，可以在 `C:\Users\31627\.m2\settings.xml` 配置阿里云镜像（没有该文件就新建）：

```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0">
  <mirrors>
    <mirror>
      <id>aliyun</id>
      <name>Aliyun Maven</name>
      <url>https://maven.aliyun.com/repository/public</url>
      <mirrorOf>*</mirrorOf>
    </mirror>
  </mirrors>
</settings>
```

然后在 IntelliJ：`Settings` → `Build Tools` → `Maven` → `User settings file` 选择该文件，再 Reload。

## 仍然失败时请检查

- 项目根目录是否有 `pom.xml`；
- Maven 工具窗口里是否显示 `campus-kb-qa`；
- `External Libraries` 是否为空；
- Build 窗口的错误是否仍然是“程序包不存在”（说明依赖仍未加载），而不是具体的语法错误。

本项目的实际构建验证环境：JDK 17.0.20 + Maven 3.9.11，`mvn test` 12 个测试通过。