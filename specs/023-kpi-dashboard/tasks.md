# Tasks: 澶у睆鏁版嵁鐪嬫澘锛圞PI 澶у睆锛?
**Input**: Design documents from `/specs/023-kpi-dashboard/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/kpi-board.md

**Tests**: 绔犵▼鍘熷垯鍥涜姹傛祴璇曞厛浜庡疄鐜帮紙绾⑩啋缁匡級锛屾湰鍔熻兘鍚悗绔崟鍏?闆嗘垚 + 鍓嶇娓叉煋娴嬭瘯銆?
## Phase 1: 鍩虹璁炬柦

- [x] T001 [P] 鍚庣锛氭柊澧?`dto/stats/KpiBoardResponse.java`锛坘pi/funnel/leaderboard/healthDistribution/suggestions/trend锛? `dto/stats/HealthDistribution.java`锛坮ed/yellow/green锛? `dto/stats/TrendPoint.java`锛坉ate/count/amount锛夈€?
## Phase 2: 鍚庣娴嬭瘯鍏堣锛圱DD 绾級

- [x] T002 [P] [US1] 鍚庣锛氱紪鍐?`backend/src/test/java/com/crm/service/KpiBoardServiceTest.java` 鍗曞厓娴嬭瘯鈥斺€旇鐩栵細缁勫悎鍚勬湇鍔¤緭鍑恒€佸仴搴峰害鍒嗗竷缁熻銆?0 澶╄秼鍔胯仛鍚堛€佺紦瀛樺懡涓€傛鏃?KpiBoardService 鏈疄鐜帮紝娴嬭瘯缂栬瘧澶辫触锛堢孩锛夈€?- [x] T003 [P] [US1] 鍚庣锛氱紪鍐?`backend/src/test/java/com/crm/integration/KpiBoardIT.java` 闆嗘垚娴嬭瘯鈥斺€擥ET /stats/kpi-board 杩斿洖鍏ㄩ儴鍖哄潡锛坘pi/funnel/leaderboard/healthDistribution/suggestions/trend锛夛紱SALES 瑙掕壊璁块棶杩斿洖 403銆傛鏃舵帴鍙ｆ湭瀹炵幇锛屾祴璇曞け璐ワ紙绾級銆?
## Phase 3: 鍚庣瀹炵幇锛圲S1/2 鑱氬悎鎺ュ彛锛?
- [x] T004 [US1] 鍚庣锛氭柊澧?`service/KpiBoardService.java`鈥斺€旀敞鍏?DashboardStatsService銆乀eamLeaderboardService銆丼uggestionService銆丆ustomerService锛堝仴搴峰害鍒嗗竷鎶芥煡 200 瀹㈡埛锛夈€丼alesOpportunityMapper锛?0 澶╄秼鍔挎寜鏃ヨ仛鍚堬級锛涚粍鍚堣緭鍑?KpiBoardResponse锛汻edis 缂撳瓨 5 鍒嗛挓锛坘ey `stats:kpi-board`锛夈€傦紙渚濊禆 T001锛?- [x] T005 [US2] 鍚庣锛歚StatsController` 鏂板 `GET /stats/kpi-board`锛園PreAuthorize ADMIN锛夈€傦紙渚濊禆 T004锛?
## Phase 4: 鍓嶇

- [x] T006 [P] [US1] 鍓嶇锛歚types/kpiBoard.ts` + `services/kpiBoardService.ts`锛坒etchKpiBoard锛夈€?- [x] T007 [US1] 鍓嶇锛氭柊澧?`pages/board/KpiBoardPage.tsx`鈥斺€斿叏灞忔繁鑹插ぇ灞忥紙KPI 鍗¤ + 婕忔枟妯悜鏉?+ 鎺掕 TopN + 鍋ュ悍搴︿笁鑹插垎甯?+ 寤鸿鎽樿 + SVG 鎶樼嚎瓒嬪娍锛夛紝鑷姩鍒锋柊 60s锛堝け璐ヤ繚鐣欐棫鏁版嵁锛? 鍏ㄥ睆鍒囨崲鎸夐挳锛沗App.tsx` 娉ㄥ唽璺敱锛堟暟鎹垎鏋愬垎缁勶級銆?
## Phase 5: 楠岃瘉涓庢敹灏?
- [x] T008 鍚庣锛歚mvn test` 鍏ㄩ噺閫氳繃锛堟柊澧?KpiBoardServiceTest + KpiBoardIT锛屼笉褰卞搷鏃㈡湁 228锛夈€?- [x] T009 鍓嶇锛歚pnpm run typecheck` + `lint` + `test` 鍏ㄩ噺閫氳繃銆?- [x] T010 [P] 鎵嬪姩鍐掔儫锛氱鐞嗗憳鎵撳紑澶у睆鍏ㄥ睆灞曠ず鍚勫尯鍧楋紱60s 鑷姩鍒锋柊锛汼ALES 瑙掕壊璁块棶琚嫆銆?
## Dependencies & Execution Order

- T001锛堝熀纭€璁炬柦锛夈€?- T002/T003 鍙苟琛岋紝鍧囦负绾㈤樁娈碉紱渚濊禆 T001銆?- T004 渚濊禆 T001锛汿005 渚濊禆 T004銆?- T006/T007 鍙苟琛岋紙鍓嶇锛夈€?- Phase 5 鍦ㄦ墍鏈夊疄鐜板畬鎴愬悗鎵ц銆?
## Notes

- 鑱氬悎澶嶇敤鏃㈡湁鏈嶅姟缁勫悎杈撳嚭锛屼笉閲嶅瀹炵幇銆?- 鍋ュ悍搴﹀垎甯冩娊鏌ユ渶澶?200 瀹㈡埛锛?0 澶╄秼鍔挎寜鏃ヨ仛鍚堛€?- 澶у睆浠?ADMIN锛汻edis 缂撳瓨 5 鍒嗛挓銆?- 鍓嶇鑷粯 SVG 鍥捐〃锛屼笉寮曞叆 echarts锛圷AGNI锛夈€?