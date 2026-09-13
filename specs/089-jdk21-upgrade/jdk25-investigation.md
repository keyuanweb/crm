# 调查记录：为什么 17 → 25 不是配置调整

**创建**：2026-09-13
**归属**：`specs/089-jdk21-upgrade`（本记录是「背景」的展开，也是将来"上 25"立项时的起点）
**状态**：25 路径**已评估、已否决（本期）**——不是被遗忘，是被量过之后主动推迟

---

## 0. 这份记录的用途

有一批改动曾试图把项目从 17 提到 25（两个 CI 作业 + 两个镜像 `FROM` + pom 三行），**已被退回原状**，原文留档在同目录 `parked-jdk25.diff`。

退回的理由不是"那批改动写得不对"，而是**"升到 25"在构造上不是一次配置调整**。本记录的目的有两个：

1. 让后来者**不必重走**这段路；
2. 让将来真的要上 25 的人**直接从版本矩阵与阻塞点开始**，而不是从零调研。

---

## 1. 四个阻塞点（均为本机实测，非推测）

四条都是"**构建在这里第一个中止**"，所以是按实际撞墙顺序排列的，不是按严重性。

### 1.1 编译：200 个错误 / 5 个文件

把 pom 的 `java.version` 改成 25，全仓编译失败：

```
SlaPolicyService 112 · SlaEscalationService 64 · NotificationService 16
ApiResponse 6 · RoleConstants 3 · PageResult 2   → 合计 200 错误 / 5 个文件
```

**根因**：本仓 `backend/pom.xml` **没有任何 `maven-compiler-plugin` 配置**。Lombok 一直依赖 **classpath 上的隐式注解处理**；而 **JDK 23 起，隐式注解处理被默认关闭**，Lombok 因此完全不运行，`@Getter` / `@Data` / `@Builder` 的生成物全部消失。

**最小实验**（javac 25 单编 `SlaPolicy`）：不加参数 → 生成 **0** 个 `getPriority()`；加 `-proc:full` → 生成 **1** 个。

**注意**：Lombok 1.18.42 本身**兼容** JDK 25。卡点是"注解处理器没被调用"，不是"Lombok 不支持 25"。

**已验证的修法**：pom 加 `maven-compiler-plugin` + `annotationProcessorPaths` 挂 Lombok → **565 个源文件 BUILD SUCCESS**。

> ⚠️ 但这个"修好了"是**假绿**。见 1.3：编译过 ≠ 应用能起。这正是上一批改动没被发现的缺口。

### 1.2 格式门禁：0.97 秒即失败

`spotless:check` 在 JDK 25 上**不到 1 秒**就中止：

```
java.lang.NoSuchMethodError:
  'java.util.Queue com.sun.tools.javac.util.Log$DeferredDiagnosticHandler.getDiagnostics()'
  at com.google.googlejavaformat.java.JavaInput.buildToks (JavaInput.java:367)
```

**是同源对照实验**：同一份代码、同一份 pom，在 JDK 17 上 `spotless:check` → `732 files clean`、BUILD SUCCESS。故该失败 **100% 由 JDK 25 引起**。

**关键判别**：抛的是 `NoSuchMethodError`，**不是** `IllegalAccessError`。所以 `--add-exports` / `--add-opens` **修不了它**——那两个开关解决的是模块可见性，而这里是方法根本不存在。不要在这条路上浪费时间去调 JVM 参数。

**版本归属**：google-java-format 的版本是**在 pom 里直接钉死的**（`<googleJavaFormat><version>1.19.2</version>`），**不受 spring-boot-parent 管理**。所以这一条不随框架线升级自动解决，必须显式提版本（见 §2）。

### 1.3 启动：应用起不来（"只改几处版本"在此**构造上**不可能）

JDK 25 产出 class **major 69**；而 `spring-core 6.1.1`（= Boot 3.2.0）内置重打包的 ASM 只读到 **major 66（Java 22）**。

**逐字节实测**（改 class 文件版本字节后调 `org.springframework.asm.ClassReader`）：

| major | 对应 | 结果 |
|---|---|---|
| 61–66 | Java 17–22 | ✅ 通过 |
| 67 | Java 23 | ❌ `IllegalArgumentException: Unsupported class file major version 67` |
| 68 | Java 24 | ❌ 同上（68） |
| 69 | Java 25 | ❌ 同上（69） |

**真实扫描探针**（不是人造 class，而是真的让框架扫自己编译出来的产物）：

```
BeanDefinitionStoreException: Failed to read candidate component class ...
  caused by: ASM ClassReader failed to parse class file ...
  Unsupported class file major version 69
```

**推论（这是最要紧的一条）**：Boot 3.2.0 **连引导一个 JDK 25 编译的应用都做不到**。所以"只覆盖几个插件版本、框架线不动"的方案**在构造上就是死的**——不是"难"，是"不成立"。任何只改编译目标/插件版本的做法都会在这一步撞死。

### 1.4 测试：540 例中 482 个错误

编译修好后跑单测：

```
Tests run: 540, Failures: 0, Errors: 482      （仅 3 个类全绿）
```

两类根因，**都是版本太老，不是行为变化**：

- `mockito-core 5.7.0` + `byte-buddy 1.14.10` 无法 mock JDK 25 的类（924 处，异常体自带 `Java: 25`）
- `JaCoCo 0.8.11` 无法插桩（574 处）

两者**都**由 `spring-boot-parent 3.2.0` 管理（但见 §2 的订正：jacoco 的归属说法需要修正）。

---

## 2. 版本矩阵（调研所得，**非本机实测**）

> **来源区分**：§1 全部是本机实测。§2 是文档/发布说明调研的结论，**未在本机逐项验证**。将来立项时应逐条实测确认，尤其是标注"未验证跳跃"的那几项。

### 2.1 必须动的

| 组件 | 当前 | 需要 | 说明 |
|---|---|---|---|
| Spring Boot | 3.2.0 | **3.5.7**（最低 3.5.6） | 3.5.6 是官方声明的 JDK 25 最低支持线；建议取 3.5.7 |
| ASM 读取上限 | 6.1.1（major ≤66） | Spring Framework **6.2.5** = Boot **3.4.4** | ASM 9.8 才支持 major 69。此条给出**绝对下限** |
| google-java-format | 1.19.2 | **≥ 1.27.0** | spotless 2.43.0 **不用动**。pom 里钉死的版本，不随框架线走 |
| Lombok | 1.18.30（继承） | **≥ 1.18.40** | 与 1.18.42 实测兼容 25 一致 |
| maven-compiler-plugin | **无配置** | 需**新加** | `annotationProcessorPaths` 显式挂 Lombok（见 §1.1） |
| JaCoCo | 0.8.11 | **≥ 0.8.14** | **订正**：jacoco **不受 Boot 管理**——pom 里 `<jacoco.version>` 是自管的。所以"随框架线升级自动解决"是错的 |
| byte-buddy | 1.14.10（继承） | **≥ 1.16.1** | **这是真正的门槛** |
| Mockito | 5.7.0 | **无需升级** | 钉住 Mockito、只提 byte-buddy 即可。**订正**：原先判断"mockito 也要升"是错的 |

### 2.2 大概率要动但未验证的

| 组件 | 当前 | 需要 | 风险 |
|---|---|---|---|
| springdoc | 2.3.0 | **≥ 2.8.9** | 中 |
| MyBatis-Plus | 3.5.5 | 建议 **3.5.16+** | 中 |
| Flyway | 9（继承） | 11 | **高**——跨两个大版本 |
| Connector/J | 8（继承） | 9 | **高**——跨大版本 |

### 2.3 已评估、已否决的替代路径

**`--release 22` 中间路**：编译到 Java 22 字节码（major 66，正好在 Boot 3.2.0 的 ASM 上限内）、运行时仍用 JDK 25。

**技术上可行**，且能绕开 §1.3（ASM）与 §1.4 的**大部分**——因为 byte-buddy 判定的是**被 mock 的类的字节码版本**，不是运行 JVM 的版本，所以 `--release 22` 会让 byte-buddy 的 924 处失败消失；ASM 的扫描也会因 major ≤66 而通过。

**但它被否决**，理由：

1. 它**不减少**任何必需工作——§1.1（编译插件）与 §1.2（google-java-format）**照样**要改；
2. 它把项目置于一个**官方不支持**的组合上（JDK 25 运行时 + Java 22 字节码 + Boot 3.2.0），后续任何问题都会先被问"你这组合谁支持"；
3. 它**回避**了真正的目标（跑到受支持的新框架线上），只是把问题往后推；
4. 它会让"我们在 JDK 25 上"这句话变成**不准确的自我描述**。

**裁决**：**伪解**。记录在此，避免后人重新"发现"它并以为找到了捷径。

### 2.4 两条订正（本轮调研推翻了我方先前的判断）

1. **JaCoCo 不受 Spring Boot 管理**：`spring-boot-dependencies` 管的是依赖，jacoco 是**构建插件**，本仓在 `<properties>` 里自管 `<jacoco.version>0.8.11</jacoco.version>`。所以"升级 Boot 就带上了新 jacoco"是错的，**必须单独改**。
2. **Mockito 不需要升级**：门槛在 byte-buddy（≥1.16.1），Mockito 5.7.0 可留。

### 2.5 一处事实订正

调研中曾以为本仓依赖 Hutool —— **本仓没有 Hutool**。已核对 `backend/pom.xml` 全文。

### 2.6 另一条参考事实

**Boot 4.0 的基线仍然是 Java 17**。所以"必须升到 Boot 4 才能上 25"不成立——升到 3.5.x 就够。这条对将来选框架线时有意义。

---

## 3. 给将来立项者的检查清单

若要把项目提到 25，顺序建议如下（前一条不通，后面都是空谈）：

1. **先确认框架线目标**（3.5.7 起，且不低于 Spring Framework 6.2.5）——这一步决定了 §1.3 能否通过。
2. **加 `maven-compiler-plugin` + `annotationProcessorPaths`**（§1.1）。这一步与框架线无关，可先行。
3. **提 google-java-format 到 ≥1.27.0**（§1.2）。spotless 本体不动。
4. **提 jacoco 到 ≥0.8.14**（自管属性，别等 Boot）。
5. **提 byte-buddy 到 ≥1.16.1**，Mockito 不动。
6. **提 Lombok 到 ≥1.18.40**。
7. **再处理 §2.2 的四项**，其中 Flyway 9→11 与 Connector/J 8→9 风险最高，**应各自单独实测**，不要与其它改动混在一起——否则无法归因。
8. **四个同步点**：pom、镜像两个 `FROM`、CI 两个作业（`parked-jdk25.diff` 已给出这四处的原文）。
9. **全程用"能不能起来"作为判据**，不要停在"编译 0 错误"（§1.3 的教训）。

---

## 4. 与 089 的关系

`specs/089-jdk21-upgrade` 把基线提到 **21**。21 的 class major 是 **65**，落在 §1.3 实测的 ASM 上限（66）之内，且 §1.1 的隐式注解处理在 21 上仍然有效——**这正是"21 零成本、25 需跳框架线"的分界点**。

089 **不浪费**这次调查：它就是这条路线上的第一级台阶，而本记录保证第二级台阶的图纸不会丢。
