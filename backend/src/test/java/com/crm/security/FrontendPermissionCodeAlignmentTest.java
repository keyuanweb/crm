package com.crm.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.crm.support.PermissionDictionaryTestSupport;
import com.crm.support.RequirePermissionScanTestSupport;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 前端权限码登记表 → 后端权限字典的对齐护栏（一期 1.5）。
 *
 * <p><b>防的是什么</b>：`frontend/src/constants/permissions.ts` 里登记的每个码都必须能在 {@link
 * com.crm.common.RoleConstants#PERMISSION_DEFS} 里找到。写错一个码（或写了一个只存在于旧版本的码）是 **静默失效**：`hasPerm` 走
 * `user.permissions.includes(code)`，字典里不存在的码永远授不给任何角色，于是它 对非 ADMIN 恒为 false——那个按钮无声无息地消失，没有
 * 403、没有报错、控制台也没有提示。反向同理：登记一个 **后端根本不校验**的码，按钮会出现但点了必然失败（或永远 disabled），界面在替一个不存在的授权做承诺。
 *
 * <p><b>为什么这两条由后端来断言</b>：权威在 {@code RoleConstants}，而且后端读前端源文件已有先例与现成范式 （{@link
 * MenuRouteAlignmentTest} 读 {@code App.tsx} / {@code menuManifest.ts}）。前端侧要读这些文件得先 引入
 * {@code @types/node}——前端测试跑在 jsdom 下没有 file: 协议的 {@code import.meta.url}，为一个护栏加一条 依赖不划算。{@link
 * PermissionDictionaryTestSupport} 与 {@link RequirePermissionScanTestSupport} 都是现成的。
 *
 * <p><b>与 {@code RequirePermissionCatalogTest} 的分工</b>：那个管「注解 → 字典」（后端自洽），本类管 「前端登记表 → 字典 /
 * 注解」（跨语言）。两者都不能少：前者按不住前端写错码，后者按不住注解写错码。
 *
 * <p><b>反向那条断言已经赚到过一次</b>（2026-09-12，1.5 批 3）：它指出 {@code customer:transfer} 被登记、 被授给 SALES /
 * SALES_MANAGER（V46 / V75），却**没有任何端点校验**——客户列表的「转移」按钮当时就按它 gating， 而该按钮真正的端点挂的是 {@code
 * customer:pool_manage}，于是销售看到的是一个永远点不动的按钮。修法是界面改挂 {@code customer:pool_manage}（权威答案），这个码则不再登记。
 */
class FrontendPermissionCodeAlignmentTest {

  /** 前端权限码登记表，相对仓库根的路径。 */
  private static final Path PERMISSIONS_TS =
      Paths.get("frontend", "src", "constants", "permissions.ts");

  /** 登记表的起点。找不到它必须报错——那说明文件被改名或改了导出形态，而不是「没有码要查」。 */
  private static final String ENTRIES_ANCHOR = "export const PERMS = {";

  /**
   * 登记项，形如 {@code customerCreate: 'customer:create',}。
   *
   * <p>只认「行首（缩进后）就是 {@code 标识符: '码',}」这一种形态：登记表内部有大量注释行（含反引号包起来的 码名，如「客户导出 `customer:export`」），它们以
   * {@code *} 或中文起头，落不进本正则——这正是**不能**退化成 「全文搜所有引号字符串」的原因：那样注释里提到一个已废弃的码就会误报。行尾允许有空格（不锚定 {@code $}），
   * 以便将来在同一行补注释。
   */
  private static final Pattern ENTRY =
      Pattern.compile("^\\s+[A-Za-z0-9_]+:\\s*'([^']+)',", Pattern.MULTILINE);

  @Test
  @DisplayName("前端登记的每个权限码都必须存在于后端字典")
  void everyRegisteredCodeExistsInTheDictionary() {
    Set<String> registered = registeredCodes();

    // 防呆：正则与锚点失配时，下面那条断言会因为「一个都没登记」而通过——假绿
    assertThat(registered)
        .as("登记表一个码都没解析出来，先查 %s 的锚点与正则", PERMISSIONS_TS)
        .hasSizeGreaterThanOrEqualTo(16)
        .contains("customer:claim", "product:update", "campaign:create", "customer:pool_manage");

    Set<String> dictionary = PermissionDictionaryTestSupport.codes();

    Set<String> orphans = new TreeSet<>(registered);
    orphans.removeAll(dictionary);

    assertThat(orphans).as("前端登记了字典里不存在的权限码：这些码授不出、hasPerm 对非 ADMIN 恒为 false，按钮会静默消失").isEmpty();
  }

  @Test
  @DisplayName("前端登记的每个权限码都必须真的被某个端点校验")
  void everyRegisteredCodeIsEnforcedSomewhere() {
    Set<String> registered = registeredCodes();
    assertThat(registered).hasSizeGreaterThanOrEqualTo(16);

    Map<String, Set<String>> enforced = RequirePermissionScanTestSupport.usages();
    // 同一处防呆：扫描范围（classpath com/crm）写坏时 usages 会空，下面会「零违规通过」
    assertThat(enforced).as("一个 @RequirePermission 都没扫到，先查扫描范围").containsKey("customer:merge");

    Set<String> decorative = new TreeSet<>(registered);
    decorative.removeAll(enforced.keySet());

    assertThat(decorative)
        .as(
            "前端按这些码做 gating，但后端没有任何端点校验它们：按钮要么必然 403、要么永远 disabled。"
                + "先确认该端点的真实码（@RequirePermission），界面改挂那个码；若该码描述的能力确实还没有端点，"
                + "就不要登记它")
        .isEmpty();
  }

  /** 解析 {@code PERMS} 对象体里登记的码。 */
  private static Set<String> registeredCodes() {
    String source = read(PERMISSIONS_TS);
    int anchor = source.indexOf(ENTRIES_ANCHOR);
    if (anchor < 0) {
      throw new IllegalStateException("在 " + PERMISSIONS_TS + " 里找不到 " + ENTRIES_ANCHOR);
    }
    Set<String> codes = new TreeSet<>();
    Matcher matcher = ENTRY.matcher(source.substring(anchor));
    while (matcher.find()) {
      codes.add(matcher.group(1));
    }
    return codes;
  }

  private static String read(Path repoRelative) {
    Path file = resolveFromRepoRoot(repoRelative);
    try {
      return Files.readString(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("读取前端源文件失败：" + file, e);
    }
  }

  /**
   * 从工作目录向上找仓库根（同时含 frontend/ 与 backend/ 的那一层），以免依赖 surefire 的 basedir。 与 {@link
   * MenuRouteAlignmentTest} 的同名方法同源——这里刻意各留一份：找不到时是**抛异常**（响的）， 不会退化成假绿，所以复制一份的代价只是将来改规则要改两处。
   */
  private static Path resolveFromRepoRoot(Path repoRelative) {
    Path dir = Paths.get("").toAbsolutePath();
    for (int depth = 0; depth < 6 && dir != null; depth++) {
      if (Files.exists(dir.resolve(PERMISSIONS_TS)) && Files.exists(dir.resolve("backend"))) {
        return dir.resolve(repoRelative);
      }
      dir = dir.getParent();
    }
    throw new IllegalStateException(
        "找不到仓库根：从 "
            + Paths.get("").toAbsolutePath()
            + " 向上 6 层都没看到 "
            + PERMISSIONS_TS
            + "。本护栏需要 frontend/ 与 backend/ 同处一个检出（CI 与本地开发都是如此）。");
  }
}
