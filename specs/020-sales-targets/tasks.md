# Tasks: 鍥㈤槦閿€鍞洰鏍囦笌鎺掕鐪嬫澘

**Input**: Design documents from `/specs/020-sales-targets/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/sales-targets.md

**Tests**: 绔犵▼鍘熷垯鍥涜姹傛祴璇曞厛浜庡疄鐜帮紙绾⑩啋缁匡級锛屾湰鍔熻兘鍚悗绔崟鍏?闆嗘垚 + 鍓嶇娓叉煋娴嬭瘯銆?
## Phase 1: 鍩虹璁炬柦

- [x] T001 [P] 鍚庣锛欶lyway `backend/src/main/resources/db/migration/V44__sales_target_user_id.sql`鈥斺€攕ales_target 鍔?`user_id` 鍒楋紙鍙┖锛? `idx_sales_target_user_month` 绱㈠紩锛涜皟鏁村敮涓€閿负鎸?(user_id, target_month)锛坲ser_id 绌烘寜 target_month锛夈€?- [x] T002 [P] 鍚庣锛歚entity/SalesTarget.java` 鍔?`userId` 瀛楁锛沗dto/stats/SalesTargetResponse.java` 鍔?`userId` 瀛楁銆?
## Phase 2: 鍚庣娴嬭瘯鍏堣锛圱DD 绾級

- [x] T003 [P] [US1] 鍚庣锛氱紪鍐?`backend/src/test/java/com/crm/service/TeamLeaderboardServiceTest.java` 鍗曞厓娴嬭瘯鈥斺€旇鐩栵細鎸?created_by 缁熻璧㈠崟銆佽揪鎴愮巼璁＄畻銆佹棤鐩爣閿€鍞帓鏈€鍚庛€佹寜杈炬垚鐜囬檷搴忋€傛鏃?TeamLeaderboardService 鏈疄鐜帮紝娴嬭瘯缂栬瘧澶辫触锛堢孩锛夈€?- [x] T004 [P] [US1] 鍚庣锛氱紪鍐?`backend/src/test/java/com/crm/integration/SalesTargetsIT.java` 闆嗘垚娴嬭瘯鈥斺€斾负涓や綅閿€鍞缃釜浜虹洰鏍囦簰涓嶅共鎵帮紱GET /stats/leaderboard 杩斿洖鎺掕锛堢洰鏍?璧㈠崟/杈炬垚鐜囷級銆傛鏃舵帴鍙ｆ湭瀹炵幇锛屾祴璇曞け璐ワ紙绾級銆?
## Phase 3: 鍚庣瀹炵幇锛圲S1 涓汉鐩爣锛?
- [x] T005 [US1] 鍚庣锛歚SalesTargetService` 鐨?`get/set` 鏀寔 `userId`鈥斺€旀寜 (userId, month) 鏌ヨ/upsert锛沗get` 涓嶄紶 userId 鏃舵煡鍏ㄥ眬鐩爣锛坲ser_id IS NULL锛夈€傦紙渚濊禆 T001/T002锛?- [x] T006 [US1] 鍚庣锛歚StatsController` 鐨?`GET /stats/sales-targets` 涓?`PUT /stats/sales-targets` 鏀寔 `userId` 鍙傛暟锛沗SalesTargetRequest` 鍔?`userId` 瀛楁銆傦紙渚濊禆 T005锛?
## Phase 4: 鍚庣瀹炵幇锛圲S2 鎺掕鐪嬫澘锛?
- [x] T007 [US2] 鍚庣锛氭柊澧?`service/TeamLeaderboardService.java`鈥斺€旀寜鏈堣仛鍚堬細鏌ュ綋鏈?sales_target锛堜釜浜猴級+ 褰撴湀 CLOSED_WON 鎸?created_by 姹囨€婚噾棰濓紱鐢熸垚 LeaderboardItem锛堢洰鏍?璧㈠崟/杈炬垚鐜囷級锛涙寜杈炬垚鐜囬檷搴忥紙鏃犵洰鏍囨帓鏈€鍚庯級锛汚DMIN 鍏ㄩ噺銆丼ALES 浠呮湰浜恒€傦紙渚濊禆 T001/T002锛?- [x] T008 [US2] 鍚庣锛氭柊澧?`dto/stats/LeaderboardItem.java`锛沗StatsController` 鏂板 `GET /stats/leaderboard`锛坢onth/sortBy 鍙傛暟锛夈€傦紙渚濊禆 T007锛?- [x] T009 [US2] 鍚庣锛歚DashboardStatsService.computePerformance` 浼樺厛鏌ュ綋鍓嶇敤鎴蜂釜浜虹洰鏍囷紙user_id=褰撳墠鐢ㄦ埛锛夛紝鏈缃洖閫€鍏ㄥ眬鐩爣锛沗Performance` 鍔?`personal` 瀛楁銆傦紙渚濊禆 T005锛?
## Phase 5: 鍓嶇

- [x] T010 [P] [US1] 鍓嶇锛歚types/stats.ts` 鍔?LeaderboardItem銆丼alesTarget.userId锛沗services/statsService.ts` 鏂板 `fetchLeaderboard(month)`銆乣saveSalesTarget` 鏀寔 userId銆?- [x] T011 [US2] 鍓嶇锛氭柊澧?`pages/stats/TeamLeaderboardPage.tsx`鈥斺€旀帓琛岃〃鏍硷紙閿€鍞?鐩爣/璧㈠崟/杈炬垚鐜?Progress + 绾㈤粍缁?Tag锛夛紝鎸夋湀浠芥煡璇紱`App.tsx` 娉ㄥ唽璺敱锛堟暟鎹垎鏋愬垎缁勶級銆?- [x] T012 [US3] 鍓嶇锛歚DashboardPage` 涓氱哗杈炬垚鍗＄墖灞曠ず涓汉鐩爣锛坧erformance.personal 鏍囪瘑锛夛紝鏈缃椂鎻愮ず銆?
## Phase 6: 楠岃瘉涓庢敹灏?
- [x] T013 鍚庣锛歚mvn test` 鍏ㄩ噺閫氳繃锛堟柊澧?TeamLeaderboardServiceTest + SalesTargetsIT锛屼笉褰卞搷鏃㈡湁 217锛夈€?- [x] T014 鍓嶇锛歚pnpm run typecheck` + `lint` + `test` 鍏ㄩ噺閫氳繃銆?- [x] T015 [P] 鎵嬪姩鍐掔儫锛氳缃攢鍞釜浜虹洰鏍?鈫?鎺掕鐪嬫澘鎸夎揪鎴愮巼鎺掑簭 鈫?棣栭〉涓氱哗杈炬垚鏄剧ず涓汉鐩爣銆?
## Dependencies & Execution Order

- T001/T002 鍙苟琛岋紙鍩虹璁炬柦锛夈€?- T003/T004 鍙苟琛岋紝鍧囦负绾㈤樁娈碉紱渚濊禆 T001/T002銆?- T005 渚濊禆 T001/T002锛汿006 渚濊禆 T005銆?- T007 渚濊禆 T001/T002锛汿008 渚濊禆 T007锛汿009 渚濊禆 T005銆?- T010/T011/T012 鍙苟琛岋紙鍓嶇锛夈€?- Phase 6 鍦ㄦ墍鏈夊疄鐜板畬鎴愬悗鎵ц銆?
## Notes

- sales_target.user_id NULL=鍏ㄥ眬鐩爣锛堝吋瀹?006锛夛紝涓汉鐩爣浼樺厛灞曠ず銆?- 璧㈠崟閲戦鎸?sales_opportunity.created_by 褰掑睘褰撴湀 CLOSED_WON銆?- 鎺掕瀹炴椂鑱氬悎锛屾棤鏂拌〃銆佹棤缂撳瓨銆?- 杈炬垚鐜囬鑹?<50% 绾?/ 50-79% 榛?/ 鈮?0% 缁裤€?