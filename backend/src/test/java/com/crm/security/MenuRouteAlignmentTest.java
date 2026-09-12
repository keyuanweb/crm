package com.crm.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.crm.support.PermissionDictionaryTestSupport;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 前端路由 → 菜单 key 的对齐护栏（一期 1.5）。
 *
 * <p><b>为什么这条断言值得存在</b>：菜单可见性靠一次跨语言翻译——前端 `menuKeyOf(path)` 产出 key， 与 `role_menu` 里授予的 key
 * 比对。两边拼写不一致时不会有任何编译错误，只会表现为 「管理员勾了、用户看不到菜单」。`/visits` 就是这么坏的：它被翻译成 `sales`，而 `MENU_TREE` 里 从来没有
 * `sales` 这个 key。
 *
 * <p><b>为什么由后端来读前端源文件</b>：{@code MENU_TREE} 是权威（{@link com.crm.common.RoleConstants}），
 * 所以「前端是否与权威一致」这一侧在后端校验最自然，也复用现成的 {@link PermissionDictionaryTestSupport} 展平逻辑。 反过来做要付两处代价：前端测试跑在
 * jsdom 下没有 file: 协议的 {@code import.meta.url}， 且没装 {@code @types/node}——为了读一个文件引入 Node 类型依赖，不值得。
 *
 * <p>两处解析与 {@code frontend/scripts/check-i18n.mjs} 的口径一致（同一套正则）， 因为那个脚本已经证明能稳定从 {@code App.tsx}
 * 取出全部菜单路由。
 */
class MenuRouteAlignmentTest {

  /** 前端源文件相对仓库根的路径。 */
  private static final Path APP_TSX = Paths.get("frontend", "src", "App.tsx");

  private static final Path MENU_KEYS_TS = Paths.get("frontend", "src", "constants", "menuKeys.ts");

  /** 与 check-i18n.mjs 同一口径：带 name 的菜单路由。 */
  private static final Pattern ROUTE = Pattern.compile("path:\\s*'([^']+)',\\s*name:\\s*");

  /** 别名表里的条目，形如 {@code '/customers/at-risk': 'at-risk',}。 */
  private static final Pattern ALIAS = Pattern.compile("'(/[^']+)':\\s*'([^']+)'");

  private static final String ALIAS_ANCHOR = "const COARSE_ALIASES";

  @Test
  @DisplayName("前端每条菜单路由翻译出的 key 都必须存在于 MENU_TREE")
  void everyRouteMapsToAnExistingMenuKey() {
    Set<String> menuKeys = PermissionDictionaryTestSupport.menuKeys();
    Map<String, String> aliases = aliases();
    Set<String> routes = routes();

    // 先证明三份输入都真读到了内容，否则下面那条断言会「零路由通过」而给出虚假的安心
    assertThat(menuKeys).hasSizeGreaterThan(50).contains("opportunity-stages");
    assertThat(routes).hasSizeGreaterThan(50).contains("/visits", "/settings/custom-fields");
    assertThat(aliases).containsEntry("/customers/at-risk", "at-risk");

    Set<String> orphans = new LinkedHashSet<>();
    for (String route : routes) {
      String key = aliases.getOrDefault(route, route.replaceFirst("^/", ""));
      if (!menuKeys.contains(key)) {
        orphans.add(route + " → " + key);
      }
    }

    assertThat(orphans)
        .as(
            "以下路由翻译出的菜单 key 不在 MENU_TREE 里：管理员勾不到它，非 ADMIN 角色永远看不到"
                + "这个菜单（/visits → 'sales' 就是这个问题）。修法是在 menuKeys.ts 里改用 MENU_TREE 的拼写，"
                + "或在该处登记别名")
        .isEmpty();
  }

  @Test
  @DisplayName("别名表只保留三条例外，且不得指向不存在的菜单 key")
  void aliasesStayExceptional() {
    Map<String, String> aliases = aliases();
    Set<String> menuKeys = PermissionDictionaryTestSupport.menuKeys();

    // 别名是「例外」而不是常规手段：路径去斜杠即 key 是默认约定，一旦这里长出一堆条目，
    // 说明有人在用别名把多段路径归并到粗粒度 key——正是 /invoices → 'orders' 那类问题的温床。
    assertThat(aliases)
        .containsOnly(
            Map.entry("/customers/at-risk", "at-risk"),
            Map.entry("/marketing/roi", "marketing"),
            Map.entry("/workflows/logs", "workflows"));

    assertThat(menuKeys).containsAll(aliases.values());
  }

  /** 解析 {@code COARSE_ALIASES} 里的 route → key。按花括号配平截取，避免把注释里的示例算进来。 */
  private static Map<String, String> aliases() {
    String text = read(MENU_KEYS_TS);
    int anchor = text.indexOf(ALIAS_ANCHOR);
    assertThat(anchor).as("%s 里找不到 %s", MENU_KEYS_TS, ALIAS_ANCHOR).isNotNegative();
    int open = text.indexOf('{', anchor);
    int end = matchingBrace(text, open);

    Map<String, String> aliases = new LinkedHashMap<>();
    Matcher matcher = ALIAS.matcher(text.substring(open, end + 1));
    while (matcher.find()) {
      aliases.put(matcher.group(1), matcher.group(2));
    }
    return aliases;
  }

  /** 解析 {@code App.tsx} 里全部菜单路由 path。 */
  private static Set<String> routes() {
    Set<String> routes = new LinkedHashSet<>();
    Matcher matcher = ROUTE.matcher(read(APP_TSX));
    while (matcher.find()) {
      routes.add(matcher.group(1));
    }
    return routes;
  }

  private static int matchingBrace(String text, int open) {
    int depth = 0;
    for (int i = open; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '{') {
        depth++;
      } else if (c == '}' && --depth == 0) {
        return i;
      }
    }
    throw new IllegalStateException("花括号不配平：" + MENU_KEYS_TS);
  }

  private static String read(Path repoRelative) {
    Path file = resolveFromRepoRoot(repoRelative);
    try {
      return Files.readString(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("读取前端源文件失败：" + file, e);
    }
  }

  /** 从工作目录向上找仓库根（同时含 frontend/ 与 backend/ 的那一层），以免依赖 surefire 的 basedir。 */
  private static Path resolveFromRepoRoot(Path repoRelative) {
    Path dir = Paths.get("").toAbsolutePath();
    for (int depth = 0; depth < 6 && dir != null; depth++) {
      if (Files.exists(dir.resolve(APP_TSX)) && Files.exists(dir.resolve("backend"))) {
        return dir.resolve(repoRelative);
      }
      dir = dir.getParent();
    }
    throw new IllegalStateException(
        "找不到仓库根：从 "
            + Paths.get("").toAbsolutePath()
            + " 向上 6 层都没看到 frontend/src/App.tsx。"
            + "本护栏需要 frontend/ 与 backend/ 同处一个检出（CI 与本地开发都是如此）。");
  }
}
