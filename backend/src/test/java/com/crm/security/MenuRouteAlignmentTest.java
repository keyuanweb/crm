package com.crm.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.crm.support.PermissionDictionaryTestSupport;
import com.crm.support.PermissionDictionaryTestSupport.MenuGroup;
import com.crm.support.PermissionDictionaryTestSupport.MenuItem;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 前端路由 → 菜单 key 的对齐护栏（一期 1.5），084 扩至「分组」与「名称」两个维度。
 *
 * <p><b>为什么这条断言值得存在</b>：菜单可见性靠一次跨语言翻译——前端 `menuKeyOf(path)` 产出 key， 与 `role_menu` 里授予的 key
 * 比对。两边拼写不一致时不会有任何编译错误，只会表现为 「管理员勾了、用户看不到菜单」。`/visits` 就是这么坏的：它被翻译成 `sales`，而 `MENU_TREE` 里 从来没有
 * `sales` 这个 key。
 *
 * <p><b>084 补的两个维度</b>：路由翻译只保证「点得开」，不保证「管理员看到的名字 = 用户看到的名字」，也不保证
 * 「角色页勾的这一项，在侧边栏里真的躺在同一个分组下」。后两类故障同样是静默的，且 084 的实测里各有一批： 分组归属 1 处（数据保留策略挂在数据分析组）、 项名 9 处、组名 4
 * 处。三处判据都是从前端**生成物** `src/constants/menuManifest.ts` 读出来的， 而不是复刻一遍生成规则——同一规则两份实现必然漂移（生成器的 javadoc
 * 已就此立过规矩）。
 *
 * <p><b>与既有护栏的分工</b>：{@code pnpm menu:check} 证明「生成物 == 现跑一遍生成器的结果」， 能抓「改了 MENU_TREE 忘了重生成」；本类直接从
 * {@code MENU_TREE} 读数据比对，能抓「生成器自己错了而两边一致」 ——后者正是 `menu:check`
 * 的盲区（它比对的两个东西都由同一段代码产出）。两者触发面也不同（`pnpm` vs `mvn test`）。
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

  /** 菜单生成物：分组、顺序、文案键与权威中文名的**唯一**使用侧来源（084 plan 决策 P1/P2）。 */
  private static final Path MENU_MANIFEST_TS =
      Paths.get("frontend", "src", "constants", "menuManifest.ts");

  private static final Path ZH_CN_TS = Paths.get("frontend", "src", "i18n", "zh-CN.ts");

  private static final Path EN_TS = Paths.get("frontend", "src", "i18n", "en.ts");

  /**
   * 生成物里的一行菜单项。**用 {@code find} 而不是 {@code matches}**：单元素分组会被折成一行 （{@code items: [{ menuKey: ...
   * }],}），此时菜单项不在行首。
   */
  private static final Pattern MANIFEST_ITEM =
      Pattern.compile("\\{ menuKey: '([^']+)', i18nKey: '([^']+)', title: '([^']+)' \\}");

  /**
   * 分组头（4 空格缩进的两行）。菜单项在 6 空格且以 {@code { menuKey:} 开头，接口字段行没有前导空格，
   * 两处都不会误命中——这一点由「11 组 / 56 项」的防呆断言兜住。
   */
  private static final Pattern GROUP_TITLE = Pattern.compile("^ {4}title: '([^']+)',$");

  private static final Pattern GROUP_I18N_KEY = Pattern.compile("^ {4}i18nKey: '([^']+)',$");

  /**
   * 语言文件里 {@code menu} 块的一行文案（该块是扁平的，见 {@link #menuTitles} 的防呆断言）。
   *
   * <p>{@code MULTILINE} 是**必须**的：取值用的是 {@code find()} 而不是逐行 {@code matches()}， 缺了它 {@code ^}
   * 只在整段文本的开头匹配，结果是一条都取不到——而「取到 0 条」正是本类各条断言的 假绿形态，故调用方对条数设了下限断言。
   */
  private static final Pattern LOCALE_ENTRY =
      Pattern.compile("^ {4}([A-Za-z0-9_]+): '([^']*)',$", Pattern.MULTILINE);

  /** 与 check-i18n.mjs 同一口径：带 name 的菜单路由。 */
  private static final Pattern ROUTE = Pattern.compile("path:\\s*'([^']+)',\\s*name:\\s*");

  /** 别名表里的条目，形如 {@code '/customers/at-risk': 'at-risk',}。 */
  private static final Pattern ALIAS = Pattern.compile("'(/[^']+)':\\s*'([^']+)'");

  private static final String ALIAS_ANCHOR = "const COARSE_ALIASES";

  /** 借分组显示的子页面表（{@code App.tsx}）：子页面路径 → 它挂在哪个菜单项之后。 */
  private static final String SUB_PAGE_ANCHOR = "const SUB_PAGE_AFTER_MENU_KEY";

  /** 规范路径例外表（{@code menuKeys.ts}）：菜单 key → 路由 path，只登记默认规则不成立的键。 */
  private static final String PATH_OVERRIDE_ANCHOR = "const CANONICAL_PATH_OVERRIDES";

  /** 规范路径例外表里的条目，形如 {@code 'at-risk': '/customers/at-risk',}（键不含前导斜杠，值含）。 */
  private static final Pattern PATH_OVERRIDE = Pattern.compile("'([^'/][^']*)':\\s*'(/[^']+)'");

  /** 置顶分组（{@code App.tsx}）：这些组不渲染分组头，成员平铺到菜单最前面。 */
  private static final String TOP_LEVEL_ANCHOR = "const TOP_LEVEL_GROUP_I18N_KEYS";

  /** 源码里的一处引号字面量（解析上面两张「元素是字符串」的表）。 */
  private static final Pattern QUOTED = Pattern.compile("'([^']*)'");

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

  @Test
  @DisplayName("借分组显示的子页面恰好两条，且两张表指向同一个菜单项（084 边界情况 ①）")
  void subPageBorrowersStayExactlyTwo() {
    Map<String, String> borrowers = subPageBorrowers();
    Map<String, String> aliases = aliases();
    Set<String> menuKeys = PermissionDictionaryTestSupport.menuKeys();
    Set<String> routes = routes();

    // 防假绿：解析成空表时下面逐条检查会「零违规通过」
    assertThat(borrowers)
        .as("%s 里的 %s 没解析出这两条", APP_TSX, SUB_PAGE_ANCHOR)
        .containsOnlyKeys("/marketing/roi", "/workflows/logs");

    List<String> problems = new ArrayList<>();
    for (Map.Entry<String, String> entry : borrowers.entrySet()) {
      String path = entry.getKey();
      String anchor = entry.getValue();
      if (!menuKeys.contains(anchor)) {
        problems.add(path + " 借的锚点 " + anchor + " 不在 MENU_TREE 里：它没有可见性来源，永远不渲染");
      }
      if (!routes.contains(path)) {
        problems.add(path + " 在 App.tsx 里没有对应路由：借了分组也打不开");
      }
      // 可见性取自别名表（menuKeys.ts 的 COARSE_ALIASES），渲染位置取自本表（App.tsx）。
      // 两处指向不同的菜单项时，页面会被「甲的可见性」控制、却显示在「乙」的分组下——正是
      // 084 要根除的「勾了不生效 / 显示在别处」那一类。
      String aliased = aliases.get(path);
      if (!anchor.equals(aliased)) {
        problems.add(
            path
                + " 的锚点不一致：SUB_PAGE_AFTER_MENU_KEY='"
                + anchor
                + "' COARSE_ALIASES='"
                + aliased
                + "'");
      }
    }

    assertThat(problems)
        .as(
            "这两个子页面没有自己的菜单项，可见性与渲染位置都必须恰好跟着所借的那一项（改造前的行为）。"
                + "条数也是断言的一部分：新增第三条意味着有页面又开始借分组，它应当有自己的菜单项")
        .isEmpty();
  }

  @Test
  @DisplayName("每个菜单项的规范路径都必须是一条真实菜单路由（084 面包屑收口）")
  void derivedPathsAreRealRoutes() {
    Set<String> routes = routes();
    Map<String, String> overrides = pathOverrides();

    // 防假绿：三份输入都得真读到内容，否则下面的循环会「零违规通过」
    assertThat(routes).hasSizeGreaterThan(50).contains("/stats", "/opportunity-stages");
    assertThat(overrides)
        .as("规范路径的例外只能有这一条——放宽它等于允许「清单项 → 一个不存在的地址」")
        .containsOnly(Map.entry("at-risk", "/customers/at-risk"));

    List<String> dangling = new ArrayList<>();
    int examined = 0;
    for (ManifestGroup group : manifestGroups()) {
      for (ManifestItem item : group.items) {
        examined++;
        String path = overrides.getOrDefault(item.menuKey, "/" + item.menuKey);
        if (!routes.contains(path)) {
          dangling.add(item.menuKey + " → " + path);
        }
      }
    }
    // 分母也要断言：清单退化成空时上面的循环空转
    assertThat(examined).as("实际检查的菜单项数").isEqualTo(56);

    assertThat(dangling)
        .as(
            "面包屑用 `menuKeys.ts` 的 `pathOfMenuKey` 推出每一项的链接地址，推出的地址必须是一条真实"
                + "声明的菜单路由，否则面包屑会链到一个打不开的地址——而这一点前端测不到"
                + "（本仓库的前端测试跑在 jsdom 下，读不了路由表）。修法：若该项的路由不是 `/${menuKey}`，"
                + "在 menuKeys.ts 的 CANONICAL_PATH_OVERRIDES 里登记")
        .isEmpty();
  }

  @Test
  @DisplayName("组内不得出现与组名同名的项（084 边界情况 ③）")
  void noItemEchoesItsGroupName() {
    List<ManifestGroup> manifest = manifestGroups();
    Set<String> topLevel = topLevelGroupI18nKeys();

    // 防假绿：两侧解析成空时下面「零违规通过」
    assertThat(manifest).hasSize(11);
    assertThat(topLevel)
        .as("%s 里的 %s 没解析出来——解析成空集会让置顶组不再被排除，而下面的检查照样通过", APP_TSX, TOP_LEVEL_ANCHOR)
        .containsExactly("home");

    List<String> echoes = new ArrayList<>();
    int examined = 0;
    int skipped = 0;
    for (ManifestGroup group : manifest) {
      // 置顶组的成员以独立项渲染（不在分组头之下），不存在「组名与项名并排」的观感
      if (topLevel.contains(group.i18nKey)) {
        skipped++;
        continue;
      }
      for (ManifestItem item : group.items) {
        examined++;
        if (item.i18nKey.equals(group.i18nKey)) {
          echoes.add("项 " + item.menuKey + " 与它所在的分组共用文案键 menu." + group.i18nKey);
        } else if (item.title.equals(group.title)) {
          echoes.add("项 " + item.menuKey + "（" + item.title + "）与它所在的分组同名");
        }
      }
    }

    // 分母也要断言：少了它，「只剩一个置顶组」或「生成物退化成空」都会让上面空转
    assertThat(examined).as("实际检查的菜单项数").isEqualTo(55);
    assertThat(skipped).as("按置顶组跳过的分组数").isEqualTo(1);
    assertThat(echoes)
        .as(
            "分组头与它下面的项显示同一个名字，侧边栏出现「客户服务 > 客户服务」这类重复标签。"
                + "改名前 tickets 就是这种形态（项名与组名同为「客户服务」，见 FR-N10），本条防它回来："
                + "归位后若某组只剩一项且与组同名，应把该项并入别的组或改名")
        .isEmpty();
  }

  @Test
  @DisplayName("生成物的分组、成员与项名与 MENU_TREE 逐项一致（084 FR-N13–N18 的归属维度）")
  void manifestGroupsMatchTheAuthority() {
    List<MenuGroup> authority = PermissionDictionaryTestSupport.menuGroups();
    List<ManifestGroup> manifest = manifestGroups();

    // 防假绿：两侧都解析成空时，下面的逐组比对会「零违规通过」
    assertThat(authority).hasSize(11);
    assertThat(manifest).hasSize(11).hasSameSizeAs(authority);
    assertThat(manifest.stream().map(group -> group.i18nKey).toList())
        .as("分组文案键不得重复（重复会让两个组共用一个标题）")
        .doesNotHaveDuplicates();

    List<String> drift = new ArrayList<>();
    for (int i = 0; i < authority.size(); i++) {
      MenuGroup expected = authority.get(i);
      ManifestGroup actual = manifest.get(i);
      String where = "第 " + (i + 1) + " 组";

      if (!expected.title().equals(actual.title)) {
        drift.add(where + "的组名：生成物='" + actual.title + "' 权威='" + expected.title() + "'");
      }

      List<String> expectedKeys = expected.items().stream().map(MenuItem::key).toList();
      List<String> actualKeys = actual.items.stream().map(item -> item.menuKey).toList();
      if (!expectedKeys.equals(actualKeys)) {
        drift.add(
            where + "（" + expected.title() + "）的成员或顺序：生成物=" + actualKeys + " 权威=" + expectedKeys);
        continue; // 成员都对不上时逐项比名称只会刷屏，一个组报一条就够定位了
      }

      for (int j = 0; j < expectedKeys.size(); j++) {
        if (!expected.items().get(j).title().equals(actual.items.get(j).title)) {
          drift.add(
              where
                  + "的项 "
                  + expectedKeys.get(j)
                  + "名称：生成物='"
                  + actual.items.get(j).title
                  + "' 权威='"
                  + expected.items().get(j).title()
                  + "'");
        }
      }
    }

    assertThat(drift)
        .as(
            "生成物的分组归属 / 顺序 / 权威名与 MENU_TREE 不一致。生成物是**派生物**，修法是改 MENU_TREE 后跑"
                + " `pnpm menu:gen`；若改的是生成器本身，请确认改对了——它错的时候 `pnpm menu:check` 会一起错")
        .isEmpty();
  }

  @Test
  @DisplayName("侧边栏的项名与组名和权威处逐字一致（084 FR-N08–N10）")
  void menuTitlesMatchTheAuthorityVerbally() {
    List<ManifestGroup> manifest = manifestGroups();
    Map<String, String> zh = menuTitles(ZH_CN_TS);

    // 防假绿：语言文件解析零命中时下面的循环空转，断言「零违规通过」
    assertThat(zh).as("%s 的 menu 块没解析出 66 条以上文案", ZH_CN_TS).hasSizeGreaterThanOrEqualTo(66);

    List<String> mismatches = new ArrayList<>();
    for (ManifestGroup group : manifest) {
      String shown = zh.get(group.i18nKey);
      if (!group.title.equals(shown)) {
        mismatches.add("组 " + group.i18nKey + "：侧边栏='" + shown + "' 权威='" + group.title + "'");
      }
      for (ManifestItem item : group.items) {
        String label = zh.get(item.i18nKey);
        if (!item.title.equals(label)) {
          mismatches.add(
              "项 "
                  + item.menuKey
                  + "（menu."
                  + item.i18nKey
                  + "）：侧边栏='"
                  + label
                  + "' 权威='"
                  + item.title
                  + "'");
        }
      }
    }

    assertThat(mismatches)
        .as(
            "角色页（MENU_TREE）勾得到、侧边栏显示成另一个名字。方向是**侧边栏向配置侧对齐**（FR-N09）："
                + "改 %s 的值，不要改 MENU_TREE 去迁就界面文案。注意 en 侧不在此断言范围内——"
                + "权威名是中文，英文界面本来就无法与之逐字相等，那侧的约束只有「键存在」（见下一条）",
            ZH_CN_TS)
        .isEmpty();
  }

  @Test
  @DisplayName("生成物引用的每个文案键在 zh-CN 与 en 里都存在（084 FR-N11）")
  void everyManifestI18nKeyExistsInBothLocales() {
    Set<String> required = new LinkedHashSet<>();
    for (ManifestGroup group : manifestGroups()) {
      required.add(group.i18nKey);
      for (ManifestItem item : group.items) {
        required.add(item.i18nKey);
      }
    }
    // 56 项 + 11 组，其中「首页」组与「首页」项共用 menu.home = 66 个键
    assertThat(required).hasSize(66).contains("home", "dataRetention", "customObjects");

    assertThat(menuTitles(ZH_CN_TS).keySet()).containsAll(required);
    assertThat(menuTitles(EN_TS).keySet()).containsAll(required);
  }

  /** 解析 {@code COARSE_ALIASES} 里的 route → key。 */
  private static Map<String, String> aliases() {
    return pathToKeyTable(MENU_KEYS_TS, ALIAS_ANCHOR, ALIAS);
  }

  /** 解析 {@code CANONICAL_PATH_OVERRIDES} 里的菜单 key → 规范路由 path。 */
  private static Map<String, String> pathOverrides() {
    return pathToKeyTable(MENU_KEYS_TS, PATH_OVERRIDE_ANCHOR, PATH_OVERRIDE);
  }

  /** 解析 {@code App.tsx} 的 {@code SUB_PAGE_AFTER_MENU_KEY}：子页面路径 → 所借的菜单项。 */
  private static Map<String, String> subPageBorrowers() {
    return pathToKeyTable(APP_TSX, SUB_PAGE_ANCHOR, ALIAS);
  }

  /**
   * 取源码里一张「路径 → 键」的花括号表。
   *
   * <p>按花括号配平截取（{@link #matchingBrace}），免得把表外的注释示例也算进来。两张表共用这一个入口：
   * 各写一份解析时，一份改坏而另一份照旧，两侧会一起给出「都对得上」的假结论。
   */
  private static Map<String, String> pathToKeyTable(Path file, String anchor, Pattern entry) {
    String text = read(file);
    int at = text.indexOf(anchor);
    assertThat(at).as("%s 里找不到 %s", file, anchor).isNotNegative();
    int open = text.indexOf('{', at);
    int end = matchingBrace(text, open);

    Map<String, String> table = new LinkedHashMap<>();
    Matcher matcher = entry.matcher(text.substring(open, end + 1));
    while (matcher.find()) {
      table.put(matcher.group(1), matcher.group(2));
    }
    return table;
  }

  /**
   * 解析 {@code App.tsx} 的 {@code TOP_LEVEL_GROUP_I18N_KEYS}（置顶分组的文案键）。
   *
   * <p>取方括号后的第一段引号字面量。同样是断言而不是静默返回空集：调用方要用它决定「哪些组不算分组头」， 空集会让排除规则整体失效而没有任何症状。
   */
  private static Set<String> topLevelGroupI18nKeys() {
    String text = read(APP_TSX);
    int at = text.indexOf(TOP_LEVEL_ANCHOR);
    assertThat(at).as("%s 里找不到 %s", APP_TSX, TOP_LEVEL_ANCHOR).isNotNegative();
    int open = text.indexOf('[', at);
    int close = text.indexOf(']', open);
    assertThat(close).as("%s 的 %s 没有闭合的方括号", APP_TSX, TOP_LEVEL_ANCHOR).isGreaterThan(open);

    Set<String> keys = new LinkedHashSet<>();
    Matcher matcher = QUOTED.matcher(text.substring(open, close + 1));
    while (matcher.find()) {
      keys.add(matcher.group(1));
    }
    return keys;
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

  /** 生成物里的一个菜单项。 */
  private record ManifestItem(String menuKey, String i18nKey, String title) {}

  /** 生成物里的一个分组。**不是 record**：菜单项与分组头在不同行上，解析时要往同一个分组里逐个追加， 用 record 就得先攒成员再构造，反而绕。 */
  private static final class ManifestGroup {

    private final String title;
    private final String i18nKey;
    private final List<ManifestItem> items = new ArrayList<>();

    ManifestGroup(String title, String i18nKey) {
      this.title = title;
      this.i18nKey = i18nKey;
    }
  }

  /**
   * 解析生成物 {@code menuManifest.ts} 的分组结构（标题、文案键、成员，全部有序）。
   *
   * <p>按「分组头两行 + 其后的菜单项行」的顺序扫一遍即可——生成物是机器写的，格式恒定； 三种行互不误命中由各自的锚点保证（见那三个 pattern
   * 的注释）。解析不到任何分组时直接抛异常， 不返回空表：空表会让调用方的断言全部静默通过。
   */
  private static List<ManifestGroup> manifestGroups() {
    List<ManifestGroup> groups = new ArrayList<>();
    String pendingTitle = null;

    // 按 `\r?\n` 切：分组头那两个 pattern 用 `matches()` 整行比对，`$` 在没有 MULTILINE 时
    // 不会在行尾匹配——CRLF 检出下每一行都带着 `\r`，届时**只有**菜单项行还能命中，
    // 于是「菜单项出现在任何分组之前」会在第一个分组处抛异常（实测到的现象），
    // 报错信息与真实原因（换行符）相去甚远。切分时去掉 `\r` 就不必依赖检出的换行符风格。
    for (String line : read(MENU_MANIFEST_TS).split("\r?\n")) {
      Matcher groupTitle = GROUP_TITLE.matcher(line);
      if (groupTitle.matches()) {
        pendingTitle = groupTitle.group(1);
        continue;
      }

      Matcher groupKey = GROUP_I18N_KEY.matcher(line);
      if (groupKey.matches()) {
        if (pendingTitle == null) {
          throw new IllegalStateException("生成物里出现没有 title 行的分组文案键：" + line);
        }
        groups.add(new ManifestGroup(pendingTitle, groupKey.group(1)));
        pendingTitle = null;
        continue;
      }

      Matcher item = MANIFEST_ITEM.matcher(line);
      while (item.find()) {
        if (groups.isEmpty()) {
          throw new IllegalStateException("生成物里菜单项出现在任何分组之前：" + line);
        }
        groups
            .get(groups.size() - 1)
            .items
            .add(new ManifestItem(item.group(1), item.group(2), item.group(3)));
      }
    }

    if (groups.isEmpty()) {
      throw new IllegalStateException(
          MENU_MANIFEST_TS + " 里一个分组都没解析出来。生成物的写法多半变了而这里的 pattern 没跟上——" + "请修 pattern，不要放宽断言");
    }
    return groups;
  }

  /**
   * 取出语言文件 {@code menu} 块的「文案键 → 文案」（键不含 {@code menu.} 前缀）。
   *
   * <p>按「{@code menu: {} 起 → 下一个 2 空格缩进的 {@code },} 止」截取，与 {@code frontend/scripts/check-i18n.mjs}
   * 展平后取 {@code menu.*} 是同一批键。 {@code menu} 块目前是扁平的（无嵌套对象），否则这个按行取值的 pattern 会漏掉内层——
   * 调用方对返回值条数的下限断言就是为此设的绊线。
   *
   * <p>换行符风格无关（LF 与 CRLF 都取得到）：这里用带 {@code MULTILINE} 的 {@code find()}， {@code $} 在 {@code \r\n}
   * 之前也成立；上面 {@link #manifestGroups()} 用的是整行 {@code matches()}， 那条路对 {@code \r} 敏感，故它另行切掉了 {@code
   * \r}。
   */
  private static Map<String, String> menuTitles(Path localeFile) {
    String text = read(localeFile);
    int anchor = text.indexOf("\n  menu: {");
    assertThat(anchor).as("%s 里找不到 menu 块", localeFile).isNotNegative();
    int end = text.indexOf("\n  },", anchor);
    assertThat(end).as("%s 里 menu 块没有收尾", localeFile).isGreaterThan(anchor);

    Map<String, String> titles = new LinkedHashMap<>();
    Matcher matcher = LOCALE_ENTRY.matcher(text.substring(anchor, end));
    while (matcher.find()) {
      titles.put(matcher.group(1), matcher.group(2));
    }
    return titles;
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
